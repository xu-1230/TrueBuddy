# doc-doctemplate · 模板文档复刻业务逻辑

> 项目：TrueBuddy ｜ 规范入口：[doc.md](./doc.md) ｜ 最近核对代码现状：2026-11-17
> 适用范围：`tb-module-doctemplate` 的生成链路、docx-mcp 工具协作与前端流程
> 相关：[doc-api.md](./doc-api.md)（接口清单）、[doc-database.md](./doc-database.md)（表结构）、[doc-integration.md](./doc-integration.md)（MCP / LLM / MinIO）、[doc-knowledgebase.md](./doc-knowledgebase.md)（检索增强）

---

## 1. 模块定位

上传一份 `.docx` 模板 + 一段 AI 描述（可选知识库空间），产出与模板**同版式**的教案文档 1~N 份（复刻 / 优化 / 精简等份数定位由 LLM 规划）。核心原则：**AI 只产出改写规则；结构、版式与校验由确定性工具保证**——`docx_replace` 只改 `w:t` 文本、版式零变化，可用 `docx_diff` 证明。

## 2. 接口清单（`DocTemplateController` / `DocChatController`，前缀 `/doc-template`）

| 方法 | 路径 | 说明 | 返回 |
| --- | --- | --- | --- |
| POST | `/doc-template/generate` | 生成文档：`templateFileId` + `spaceId`（空 / `"default"` = 不启用检索）+ `description`（≤500 字）+ `quality`（480p/720p/1080p） | **同步阻塞**返回生成文件列表（无轮询、无 SSE） |
| GET | `/doc-template/history?limit=` | 生成历史（默认 20 条，上限 100，按**任务**计数；只返回当前登录用户 creator 的记录）；每条内联 `docs` 文件摘要（`DocFileRespVO`，`src` 为空、不签发 URL），首页「文档」tab 据此展开文件卡 | `CommonResult<List<GenerationHistoryRespVO>>` |
| GET | `/doc-template/detail?id=` | 生成详情（任务 + 文件列表，重新签发 MinIO 预签名 URL）；非本人记录按不存在处理；`docs[].src` 指向 `current_version_id` 对应文件 | `CommonResult<GenerationRespVO>` |
| POST | `/doc-template/chat` | AI 文档编辑 SSE（见 §7）；要求登录且为根文件 creator | `SseEmitter`：`status` / `assistant` / `version` / `error` / `done` |
| POST | `/doc-template/chat/undo` | 撤回某次编辑：目标版本及全部后代 `superseded`，当前版本回到目标版本的父版本；目标助手消息及其后聊天消息一并失效 | `CommonResult<ChatUndoRespVO>` |
| GET | `/doc-template/chat/history?rootFileId=&limit=` | 某文档（根文件 / 工作区）的聊天记录；登录且仅根文件 creator 可见；`limit` 默认 200、上限 500，按时间正序 | `CommonResult<List<ChatMessageRespVO>>` |

> `/doc-template/**` 自 2026-11-17 起移出免登录白名单：生成 / 历史 / 详情 / 聊天记录 / 编辑 / 撤回都要求登录，并按 creator 隔离。

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

后端经 Spring AI MCP Client（stdio 进程内长连接）**按工具名**调用，统一解包 `content` 数组 JSON；句柄 `docId` 依赖同一 MCP 会话。生成链路使用 `docx_open`、`docx_get_text`、`docx_get_tables`、`docx_replace`、`docx_validate`、`docx_fill_fields`；AI 聊天编辑另用 `docx_set_cell`、`docx_insert_paragraph`、`docx_delete_paragraph`、`docx_diff`。

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
| `components/AiChatPanel.vue` | 真实 SSE 编辑：选区 chips、阶段状态、`assistant` / `version` 事件、chat-meta 撤回；聊天仅前端会话态，见 [doc-ui.md](./doc-ui.md) §11 | 聊天历史持久化（后续方案） |
| 导出 | `quality` 档位仅作生成参数；`doc_template_file.type`（WORD/PDF/MARKDOWN）已预留 | 导出能力落地 |
| 管理端 | `/admin/doc-template/{templates,records,inspections}` 三页为前端 mock，业务接口未实现 | 与后端真实数据打通 |
| 历史 / 详情 / AI 编辑越权 | 已整改：`getHistory` / `getDetail` / `chat` / `undo` 按 creator 隔离；`/doc-template/**` 移出白名单；存量记录 creator 统一为 `2902017108@qq.com` | 无（仍不提供跨用户分享） |

> 表结构（`doc_template_generation` / `doc_template_file` / `doc_template_version`，status 枚举）见 [doc-database.md](./doc-database.md)。

## 7. 文档工作区、版本与 AI 编辑（2026-11-17）

### 7.1 数据模型

- **根文件即工作区**：不建 workspace 表；`doc_template_file` 根行就是工作区，其 `creator` 即所有者。
- 根文件本身即 **v1**；`doc_template_version` 只存 v2+ 的不可变版本（`parent_version_id` 为空表示父级是 v1）。
- `doc_template_file.current_version_id` 指向当前版本；为空表示当前是 v1 根文件。
- 结果列表 / 首页「文档」tab 仍只展示根文件，**不展示版本号或版本下拉**；卡片 `src` 指向当前版本。
- 存量记录 creator 统一刷为 `2902017108@qq.com`（`system_user.id=1`）；新生成写入当前登录用户。

### 7.2 AI 编辑链路

1. 前端在 docx-preview DOM 上按 `p:N` / `t:N` / 单元格标注 `data-docx-loc`；点击选中元素加入 composer 的 ghost-chip（最多 5 个，作为 LLM 上下文提示，不是硬性范围）。
2. `POST /doc-template/chat`（SSE）请求 `{rootFileId, instruction, selections[], history[]}`；LLM 上下文优先读取持久化记录（最近 12 条有效消息），没有任何记录时才回退请求里的 `history`。
3. 后端用当前版本 `docx_get_text` / `docx_get_tables` 构建全文摘要，叠加选中元素高亮与最近 12 条会话消息，调用 LLM 产出 `{reply, operations}`。
4. operations 仅允许 `replace` / `setCell` / `insertParagraph` / `deleteParagraph`；replace 先 `docx_replace(dryRun)`，失败自动让 LLM 修正一次；结构操作按段落号倒序执行，避免索引漂移。
5. 成功后 `docx_diff` 校验页面设置，上传新 docx 到 MinIO，插入 `doc_template_version`（revision = max+1），更新 `current_version_id`；SSE 返回 `assistant` 摘要 + `version` 当前地址。
6. 助手消息的 `versionId` 用于 chat-meta 撤回；前端不展示版本号。
7. 无选区时按全文处理；选中元素只作为重点提示，AI 可为一致性修改其他段落。

### 7.3 撤回

- chat-meta 的撤回图标只出现在产生了新版本的助手消息上；点击确认后调用 `POST /doc-template/chat/undo`。
- 目标版本及全部后代置 `superseded=TRUE`，`current_version_id` 回到目标版本的 `parent_version_id`（空 = v1）。
- 目标版本的助手消息及其后聊天消息同时置 `superseded`（旧版本没有消息行时，以被丢弃版本里最早的消息为边界），并落一条 `system` 撤回提示；整个撤回在一个事务内完成。
- 前端先本地置失效/追加提示保证响应即时，再重新拉取聊天记录以与服务端一致；不做 redo。

### 7.4 权限

- `/doc-template/**` 已移出免登录白名单；生成 / 历史 / 详情 / 编辑 / 撤回都要求登录。
- 历史 / 详情按 `doc_template_generation.creator` 过滤；聊天 / 撤回校验根文件 creator，非本人按「文档不存在或无权访问」处理。
- 生成时写入当前用户到 `doc_template_generation` / `doc_template_file`（及后续版本的 `creator`）。

### 7.5 单元格定位说明

`docx-preview` 对合并单元格的 DOM 行列与 docx-mcp 的逻辑网格可能不一致：正文段落 `p:N` 可精确对齐；单元格选择保留 DOM locator 做高亮，送给 LLM 时降级为**表格级定位 + 单元格文本**，由模型从表格清单中定位实际单元格。edit 指令中的 locator 必须来自 docx-mcp 清单，由后端 dryRun 校验。

### 7.6 聊天记录持久化与加载（2026-11-17）

- 表 `doc_template_chat_message` 按 `root_file_id` 归属，保存 `user` / `assistant` / `system` 三类消息；助手消息用 `version_id` 关联本次编辑版本。
- 一轮对话的写入点：请求开始写 `user`（含选区）；无编辑 / 编辑成功写 `assistant`（成功带 `versionId`）；异常写 `error=TRUE` 的 `assistant`；撤回写 `system` 提示。写入为最佳努力，失败只记日志，不阻断 SSE。
- 打开文档（`rootFileId` 变化）时前端调用 `GET /doc-template/chat/history` 回放；切换版本不改 `rootFileId`，因此不会重载或重复消息。
- 记录只对根文件 creator 可见；历史接口与聊天 / 撤回共用 `requireOwnedRoot` 校验。
- 分页 / 清空 / 搜索暂不做；单次最多返回 500 条（默认 200），超出部分留待后续方案。
