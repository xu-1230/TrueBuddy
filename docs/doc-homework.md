# doc-homework · 作业批改业务逻辑

> 项目：TrueBuddy ｜ 规范入口：[doc.md](./doc.md) ｜ 最近核对代码现状：2026-11-16
> 适用范围：`tb-module-homework` 的上传 / 批改链路，以及批改执行对 workflow 模块的复用
> 相关：[doc-workflow.md](./doc-workflow.md)（执行引擎）、[doc-api.md](./doc-api.md)、[doc-database.md](./doc-database.md)、[doc-knowledgebase.md](./doc-knowledgebase.md)（spaceIds 检索增强）、[adr/0006-知识库与工作流归属与共享模型.md](./adr/0006-知识库与工作流归属与共享模型.md)（批改历史落库与回写）

---

## 1. 模块定位

作业批改 = 「**文件上传与配置存档**（本模块）」+「**工作流执行**（复用 tb-module-workflow）」两段。后端只收文件与配置；批改的真正执行由前端驱动工作流 `POST /api/chat`（SSE）完成，LLM / 多模态识别能力都在工作流节点内，本模块自身不调模型。

## 2. 接口清单（`HomeworkController`，前缀 `/homework`）

| 方法 | 路径 | 说明 | 返回 |
| --- | --- | --- | --- |
| POST | `/homework/upload` | 批量上传作业文件夹（multipart：`files` + `paths` 按序一一对应 + 可选 `folderName`），逐份存 MinIO 并落库相对路径 | `CommonResult<HomeworkUploadRespVO>`（taskId、fileCount、paths、files 明细含 7 天预签名 URL） |
| POST | `/homework/grade` | 提交批改配置（taskId + spaceIds + files + description + **scoreMin/scoreMax/passScore + customFields** + workflowId），**仅落库存档，不执行批改**；后端校验区间与列定义（400） | `CommonResult<Boolean>` |
| GET | `/homework/task/list` | 批改任务列表（历史抽屉数据源）：最近 N 条（id/名称/状态/时间/区间），`limit` 非正取默认值 | `CommonResult<List<HomeworkTaskItemVO>>` |
| GET | `/homework/task/detail` | 任务详情（历史抽屉点条目回看）：任务信息 + 批改配置（区间 / 自定义列 / 文件树）+ 逐学生结果（含 `custom`） | `CommonResult<HomeworkTaskDetailRespVO>` |
| POST | `/homework/task/start` | 开始执行批改：任务状态 0→1；前端在 `/grade` 之后、跑工作流之前调用 | `CommonResult<Boolean>` |
| POST | `/homework/{taskId}/results` | **终态回写**（前端执行终态后调用）：逐学生评分覆盖式落库 + 任务状态收敛到终态 2/3/4 | `CommonResult<HomeworkResultWriteRespVO>`（written 条数） |

## 3. 上传主流程（`HomeworkServiceImpl.uploadAndBuildTree`）

1. 校验：非空、≤50 份、files/paths 数量一致；相对路径规范化（拒绝 `.` / `..` / 重复，防路径穿越）。
2. 逐份 `storeToMinio`：扩展名白名单（pdf / doc / docx / md / txt / ppt / pptx / xls / xlsx + png / jpg / jpeg / gif / webp）、单文件 ≤50MB、允许 0 字节文件。
3. 短事务落库（全成或全败）：`infra_file` → `homework_task`(status=0) → `homework_file` 批量。
4. 组装返回：taskId、folderName、fileCount、paths、files 明细（fileId + 7 天预签名 URL）。

## 4. 批改执行链路（关键约定）

- **执行入口**：前端 `POST /api/chat`（`text/event-stream`，见 doc-workflow.md §2.2）。**工作流 ID 不写死**：由前端从 `listWorkflows` 选择，默认选后端标记 `builtin` 的内置模板（「标准批改作业」，启动初始化器幂等写入）。
- **状态推进**：点「开始批改」先调 `POST /homework/task/start` 把任务置 `1=批改中`，再建会话跑工作流；终态回写后收敛到 `2=成功 / 3=部分成功（有漏批）/ 4=失败`（取消/中断的执行不回写，任务停在 `1`）。
- **结果落库**：`completed` 后前端组装逐学生记录（student/score/grade/readFiles/custom）调 `POST /homework/{taskId}/results`；后端覆盖式回写——先按 taskId 物理删除旧记录再整批插入，同一任务以最后一次为准（US12）；`custom` 是自定义导出列的值对象，服务端按任务定义过滤未定义 key、缺失补空串；`conversation_id` 仅作溯源。旧 `comment` / `deductions` 列保留为 legacy，不再写入。
- **inputs 契约**：`{file_tree, description, know, score_min, score_max, pass_score, custom_fields}`——开始节点按这些键读取；知识库 spaceIds 透传给工作流节点作检索增强；区间与列定义由左侧「评分与导出设置」固定，显式空 `custom_fields` 表示本次没有自定义列。
- **输出契约**：迭代体固定产出 `student / score / grade / readFiles / custom`；原「单份评语」节点已合并进评分节点，汇总节点 `grade_sheet` 不再带 comment。
- **人工确认**：遇 `requires_action` 中断帧弹出「确认批改列表」卡片，确定 / 取消后携原值续跑，最多 5 轮（防环状定义死循环）。
- **流式上牌**：迭代体每完成一份学生，输出节点帧触发 `onRecord`，ScoreCard 卡堆逐张进入（字段名来自输出节点声明的 inputs）。
- **无轮询兜底**：终态只认 `conversation.chat.{completed,failed,requires_action}` 帧；帧丢失时用最终结果兜底补齐卡片。
- **多模态**：若把文件交给多模态模型，要么确认 MinIO 预签名 URL 对模型侧可达，要么后端读字节后 base64 内联——由工作流节点实现，homework 模块只提供文件与路径。

## 5. 表与状态枚举

| 表 | 关键点 |
| --- | --- |
| `homework_task` | status：0=已上传待批改 / 1=批改中 / 2=成功 / 3=部分成功 / 4=失败；`description`（批改要求）、`space_ids`（逗号分隔）、`score_min / score_max / pass_score`（评分区间）、`custom_fields JSONB`（自定义导出列定义） |
| `homework_file` | `relative_path`（形如 `课程/学号-姓名/文件.docx`，本模块产出核心，`(task_id, relative_path)` 唯一）；从属表物理删除、无软删列 |
| `homework_grading_result` | 逐学生评分记录（覆盖式回写）：student/score/grade/**custom(JSONB)**/read_files(JSONB) + conversation_id 溯源；`comment` / `deductions` 为 legacy 列（已迁入 custom，停止读写）；idx(task_id) |
| `homework_grading_task` / `homework_grading_record` | **旧版表**，仅存于 `restore_dropped_tables_20261022.sql` 恢复脚本，现行代码不使用（新表命名刻意避开二者，防与恢复脚本混淆） |

> 完整列定义见 [doc-database.md](./doc-database.md)。

## 6. 前端流程（`views/homework/HomeworkGradingView.vue` + `utils/grading-run.js`）

1. 挂载：`listWorkflows` 加载工作流（默认 builtin）、`getSpaceList` 加载知识库。
2. FolderUploader 选择 / 拖入文件夹（webkitRelativePath 取相对路径；zip 由前端本地 jszip 解压）。
3. ConfigPanel：填批改要求（description）、选知识库（spaceIds）、**固定评分区间（最低分/最高分/及格线）与自定义导出列**、选工作流；原 Excel/CSV 切换已移除，导出格式留待导出接口落地时按次选择。
4. 点「开始批改」：① `POST /homework/upload`（带进度条）→ taskId；② `POST /homework/grade`（仅存档）；③ `POST /homework/task/start`（状态 0→1）；④ `getWorkflowDetail` 校验输出节点契约 → `POST /chat/conversation` 建会话 → `POST /api/chat` SSE 流式执行。
5. 中断帧弹人工确认卡片续跑；输出节点帧流式上牌。
6. `completed` 后取逐学生 records + 汇总 statistics，提示含漏批份数统计；随后用 `utils/result-payload.js` 的 `buildResultWritePayload`（零依赖纯函数）组装请求体调 `POST /homework/{taskId}/results` 覆盖式回写——落库失败不打断已上牌的结果，只提示「历史记录将缺少本次结果」；执行失败（非取消）也回写空 records 把任务收敛到 `4=失败`。用户取消的执行不回写（任务停在当前状态）。
7. **历史抽屉**（`components/HistoryDrawer.vue`，交互仿文档模板页）：打开时 `GET /homework/task/list` 列最近任务（名称/状态/时间/记录数）；点条目 `GET /homework/task/detail`——有落库评分时按成绩卡形态恢复画布（与服务端流式上牌同一映射，一个学生一张卡），无记录时退回只读的配置 + 文件树回看；批改进行中暂不允许回看。
8. **成绩卡**（`components/ScoreCard.vue`，2026-11-17 重构）：每张卡是一张「成绩单」——卡头文件/翻阅、抬头学生与超大得分 + 档位印章 + 横贯全卡的 `min–max` 刻度、材料清单、**`02 评分输出`（按自定义导出列渲染）**、页脚批改要求。分数分母 / 刻度 / 失分 / 档位色都取当次任务的评分区间；版式、容器自适应与动效口径见 [doc-ui.md](./doc-ui.md) §12，区间与自定义列口径见 [specs/2026-11-17-作业批改评分区间与自定义导出列.md](./specs/2026-11-17-作业批改评分区间与自定义导出列.md)。

## 7. 现状与规划

| 项 | 现状 | 规划 |
| --- | --- | --- |
| 批改结果导出 | 前端 `handleExport` 标注「待接入后端」；自定义导出列已定义、随工作流产出并落库，导出接口未实现 | 后端导出接口（按定义渲染自定义列） |
| 批改历史 | **已落地**（2026-11-16）：`/task/list` + `/task/detail` + 历史抽屉回看；管理端 `/admin/homework/{resources,records}` 页面仍为前端 mock | 管理端打通 |
| 评分区间 / 自定义导出列 | **已落地**（2026-11-17）：面板固定区间与导出列，startnode / 规划 / 确认 / 评分 / 汇总按新变量运行；区间与列定义落 `homework_task`，逐学生值落 `homework_grading_result.custom`；旧数据一次性迁移 | 导出接口、列排序 / 类型扩展 |
| 成绩卡视觉 | **已落地**（2026-11-17）：成绩单版式（区间刻度 / 档位印章 / 材料清单 / `02 评分输出`）、容器自适应与入场动效；右栏按自定义列渲染（doc-ui §12） | 无（导出、签名、班级汇总另开方案） |
| 状态回写 | **已落地**（2026-11-16）：`/task/start` 置 1、`/{taskId}/results` 覆盖式落库并收敛 2/3/4 | 用户取消的执行任务停在 `1`（接受：终态只认前端回写，无服务端兜底超时） |
| 结果表 0 行（2026-11-16 诊断） | **历史诊断**：当时业务库 36 条 `homework_task`、`homework_grading_result` 0 行；最近两条任务 `status=4`，对应 `workflow_run.status=3`（失败）。最小测试证明：内置标准工作流非空 records 时 payload 非空、Service + 真实 PostgreSQL 均写入 2 行；空 records 时只删不插、状态收敛 4、结果表 0 行。2026-11-17 迁移时表内已有 6 行存量结果，落库链路未改 | **规范要求**：历史结果缺失应能追溯到失败阶段，不能只表现为「没有写入」 |
| 结果表 0 行·待整改 | **根因**：上游工作流执行失败（`workflow_run.status=3`）→ 前端 `handleGrade` 失败兜底回写空 records → `writeResults` 只删不插并置 4，结果表保持 0 行；**写入链路本身无故障** | **待整改**：① 在工作流失败时保留可见的失败原因（当前 `workflow_run` 无错误字段，SSE `last_error` 未落库）；② 区分「失败收敛」与「没有产出」两类空记录，避免历史抽屉只看到空画布；③ 补 E2E 断言 `completed` 后查表有行 |
| 认证归属 | 现状：`/homework/**` 整体在 permit-all 白名单（application.yml，为未登录态联调而设的历史口径），新加的 `/task/*`、`/{taskId}/results` 因此**匿名可达**；`writeResults` 可覆盖任意 taskId 的结果、`/task/list` 可看全部任务 | 规范要求：结果回写、任务列表/详情应校验登录并按 `creator` 过滤（同 ADR 0006 归属模型）。**待整改**：收口 `/homework/**` 白名单或对写路径加归属校验，属独立变更，未纳入本次批次 |
