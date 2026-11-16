# doc-doctemplate · 模板文档复刻业务逻辑

> 项目：TrueBuddy ｜ 规范入口：[doc.md](./doc.md) ｜ 最近核对代码现状：2026-11-13
> 适用范围：`tb-module-doctemplate` 的生成链路、docx-mcp 工具协作与前端流程
> 相关：[doc-api.md](./doc-api.md)（接口清单）、[doc-database.md](./doc-database.md)（表结构）、[doc-integration.md](./doc-integration.md)（MCP / LLM / MinIO）、[doc-knowledgebase.md](./doc-knowledgebase.md)（检索增强）

---

## 1. 模块定位

上传一份 `.docx` 模板 + 一段 AI 描述（可选知识库空间），产出与模板**同版式**的教案文档 1~N 份（复刻 / 优化 / 精简等份数定位由 LLM 规划）。核心原则：**AI 只产出改写规则；结构、版式与校验由确定性工具保证**——`docx_replace` 只改 `w:t` 文本、版式零变化，可用 `docx_diff` 证明。

## 2. 接口清单（`DocTemplateController`，前缀 `/doc-template`）

| 方法 | 路径 | 说明 | 返回 |
| --- | --- | --- | --- |
| POST | `/doc-template/generate` | 生成文档：`templateFileId` + `spaceId`（空 / `"default"` = 不启用检索）+ `description`（≤500 字）+ `quality`（480p/720p/1080p） | **同步阻塞**返回生成文件列表（无轮询、无 SSE） |
| GET | `/doc-template/history?limit=` | 生成历史（默认 20 条，上限 100，按**任务**计数）；每条内联 `docs` 文件摘要（`DocFileRespVO`，`src` 为空、不签发 URL），首页「文档」tab 据此展开文件卡 | `CommonResult<List<GenerationHistoryRespVO>>` |
| GET | `/doc-template/detail?id=` | 生成详情（任务 + 文件列表，重新签发 MinIO 预签名 URL） | `CommonResult<GenerationRespVO>` |

## 3. 生成主流程（`DocTemplateServiceImpl.generate`）

1. **参数校验**：templateFileId 必填、description 非空 ≤500 字；模板查 `infra_file` 且必须 `.docx`；落 `doc_template_generation`（status=0 生成中）。
2. **环境准备**：临时目录 `{tempDir}/{generationId}`；取模板的 MinIO 预签名 URL。
3. **解析模板**（docx-mcp 第 1 次介入）：`docx_open` 取骨架 → `docx_get_tables` 取表格清单 → `docx_get_text` 翻页取全文（每页 200 块、最多 30 页）→ `docx_validate` 生成「体检基线」。
4. **知识库检索增强**（第 2 次介入）：`kbDocumentService.searchBySpace(spaceId, description, topK=5)`。
5. **LLM 规划**（第 3 次介入）：`aiContentService.plan()` 产出份数 + 每份主题定位 + 自检关键词（必备 / 禁检）。
6. **逐份生成** `generateOne`（单份失败不影响其余）：
   - LLM `buildReplacements` 产出 old→new 改写规则（重复文本带 scope 按位置区分）；
   - `docx_replace(dryRun=true)` 预演 → `selectRules` 只留命中规则；无 scope 且多命中的按位置展开（**禁止全局替换**）；
   - `docx_replace(dryRun=false)` 落盘（只改 `w:t` 文本）；`docx_fill_fields` 填封面字段（仅课程名称 / 章节名称 / 面向专业，人事日期不编造）；
   - `selfCheckWithFixup`：`docx_validate` 体检（结构 / 页面设置 / 空单元格 / 旧词残留 / 新词缺失）→ 有缺口则 LLM `buildFixups` 补规则 → 增量重落盘，最多 `max-fix-rounds` 轮；
   - 结果上传 MinIO（`fileService.uploadBytes`）→ `doc_template_file` 落库。
7. **回填任务结果**：status 1=成功 / 2=部分成功 / 3=失败，记录 docCount / errorMsg / durationMs；finally 清理临时目录。

## 4. docx-mcp 工具

后端经 Spring AI MCP Client（stdio 进程内长连接）**按工具名**调用，统一解包 `content` 数组 JSON；句柄 `docId` 依赖同一 MCP 会话。**后端只使用 6 个工具**：`docx_open`、`docx_get_text`、`docx_get_tables`、`docx_replace`、`docx_validate`、`docx_fill_fields`。

`docx-mcp/main.py` 全部工具清单：

| 工具 | 用途 |
| --- | --- |
| `docx_open` | 返回会话句柄 docId + 文档骨架（结构统计 / 页面设置 / 标题 / 封面字段 / 表格概览） |
| `docx_get_text` | 带稳定索引的纯文本导出（正文段落 + 表格单元格路径），支持分页 |
| `docx_get_tables` | 表格结构（行列数、合并单元格、单元格路径→文本） |
| `docx_replace` | 带作用域批量替换（不走 HTML 往返），dry_run 预演 + 结构化变更报告 |
| `docx_validate` | 文档体检（占位符残留 / 空单元格 / 关键词计数 / 页面设置） |
| `docx_fill_fields` | 填充封面 / 表单字段（`{{占位符}}` 或「标签：」后空值） |
| `docx_to_html` / `html_to_docx` | docx 渲染为与 docx-preview 同构的 HTML / 逆向还原（互为逆过程） |
| `docx_search` | 文档内检索，返回命中位置 + 上下文 |
| `docx_get_runs` | 段落 run 边界（切分 / 格式 / 跨 run 匹配说明） |
| `docx_diff` | 两文档比对，证明「只改文字、版式零变化」 |
| `docx_set_cell` | 写单元格文本（保留原格式） |
| `docx_insert_paragraph` / `docx_delete_paragraph` | 插入 / 删除段落 |
| `test` | MCP 连通性测试 |

## 5. 前端流程（`views/doctemplate/DocTemplateView.vue`）

1. 选模板文件 → `POST /infra/file/upload` 拿 templateFileId。
2. 选知识库（内置「默认知识库」传 `"default"` = 不启用检索）、填 AI 描述、清晰度。
3. 点生成 → `POST /doc-template/generate` **同步等待** → 有文档则 `replace` 到 `/index/doc-template/:generationId`，渲染结果卡片（tag / summary / MinIO URL）；失败 / 0 份只提示错误，不改 URL 与当前结果。
4. **URL 是页面状态的唯一真相源**：`watch([:id, ?file], immediate)` 驱动载入。点历史条目 → `push` 到 `/:generationId`（0 份 / 失败记录同样进入），抽屉立即关闭，由结果区显示「正在载入生成记录…」；详情拉取后回填预览 URL。
5. `?file=<DocFileVO.id>`（`doc_template_file` 主键，不是 `infra_file.id`）打开查看模式；刷新 / 前进 / 后退均可重放。文件不存在或不属于该次生成 → `replace` 剔除 query 回结果列表。
6. 结果卡可打开查看模式（docx-preview 渲染 + 元素选取 + AI 聊天面板）；「返回结果列表」= `push` 去掉 `file` 的地址。

> URL 契约、异常分层与验收项见 [specs/2026-11-16-模板生成页路由状态.md](./specs/2026-11-16-模板生成页路由状态.md)。
>
> 首页（`views/system/SystemHomeView.vue`）「文档」tab 复用 history 内联的 `docs` 展开文件卡，一份文件一张卡，点击直达上述 `/:generationId?file=<docId>`；详见 [specs/2026-11-16-首页文档tab列出生成文档.md](./specs/2026-11-16-首页文档tab列出生成文档.md)。

## 6. 现状与规划

| 项 | 现状 | 规划 |
| --- | --- | --- |
| `components/AiChatPanel.vue` | 回复来自前端写死的回复池（演示态） | 接入真实 SSE 流式对话（复用 workflow 的 SSE 读取封装） |
| 导出 | `quality` 档位仅作生成参数；`doc_template_file.type`（WORD/PDF/MARKDOWN）已预留 | 导出能力落地 |
| 管理端 | `/admin/doc-template/{templates,records,inspections}` 三页为前端 mock，业务接口未实现 | 与后端真实数据打通 |
| 历史 / 详情越权 | `getHistory` / `getDetail` 未按 `creator` 过滤，`/doc-template/**` 在放行白名单内；`/index/doc-template/:id` 可枚举访问任意生成记录 | 按登录用户 `creator` 过滤并调整白名单，或改用不可枚举 id |

> 表结构（`doc_template_generation` / `doc_template_file`，status 枚举）见 [doc-database.md](./doc-database.md)。
