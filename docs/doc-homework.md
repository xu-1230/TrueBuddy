# doc-homework · 作业批改业务逻辑

> 项目：TrueBuddy ｜ 规范入口：[doc.md](./doc.md) ｜ 最近核对代码现状：2026-11-13
> 适用范围：`tb-module-homework` 的上传 / 批改链路，以及批改执行对 workflow 模块的复用
> 相关：[doc-workflow.md](./doc-workflow.md)（执行引擎）、[doc-api.md](./doc-api.md)、[doc-database.md](./doc-database.md)、[doc-knowledgebase.md](./doc-knowledgebase.md)（spaceIds 检索增强）

---

## 1. 模块定位

作业批改 = 「**文件上传与配置存档**（本模块）」+「**工作流执行**（复用 tb-module-workflow）」两段。后端只收文件与配置；批改的真正执行由前端驱动工作流 `POST /api/chat`（SSE）完成，LLM / 多模态识别能力都在工作流节点内，本模块自身不调模型。

## 2. 接口清单（`HomeworkController`，前缀 `/homework`）

| 方法 | 路径 | 说明 | 返回 |
| --- | --- | --- | --- |
| POST | `/homework/upload` | 批量上传作业文件夹（multipart：`files` + `paths` 按序一一对应 + 可选 `folderName`），逐份存 MinIO 并落库相对路径 | `CommonResult<HomeworkUploadRespVO>`（taskId、fileCount、paths、files 明细含 7 天预签名 URL） |
| POST | `/homework/grade` | 提交批改配置（taskId + spaceIds + files + description + exportFormat + workflowId），**仅落库存档，不执行批改** | `CommonResult<Boolean>` |

## 3. 上传主流程（`HomeworkServiceImpl.uploadAndBuildTree`）

1. 校验：非空、≤50 份、files/paths 数量一致；相对路径规范化（拒绝 `.` / `..` / 重复，防路径穿越）。
2. 逐份 `storeToMinio`：扩展名白名单（pdf / doc / docx / md / txt / ppt / pptx / xls / xlsx + png / jpg / jpeg / gif / webp）、单文件 ≤50MB、允许 0 字节文件。
3. 短事务落库（全成或全败）：`infra_file` → `homework_task`(status=0) → `homework_file` 批量。
4. 组装返回：taskId、folderName、fileCount、paths、files 明细（fileId + 7 天预签名 URL）。

## 4. 批改执行链路（关键约定）

- **执行入口**：前端 `POST /api/chat`（`text/event-stream`，见 doc-workflow.md §2.2）。**工作流 ID 不写死**：由前端从 `listWorkflows` 选择，默认选后端标记 `builtin` 的内置模板（「标准批改作业」，启动初始化器幂等写入）。
- **inputs 契约**：`{file_tree: files, description, know: spaceIds}`——开始节点按这三个键读取；知识库 spaceIds 透传给工作流节点作检索增强。
- **人工确认**：遇 `requires_action` 中断帧弹出「确认批改列表」卡片，确定 / 取消后携原值续跑，最多 5 轮（防环状定义死循环）。
- **流式上牌**：迭代体每完成一份学生，输出节点帧触发 `onRecord`，ScoreCard 卡堆逐张进入（字段名来自输出节点声明的 inputs）。
- **无轮询兜底**：终态只认 `conversation.chat.{completed,failed,requires_action}` 帧；帧丢失时用最终结果兜底补齐卡片。
- **多模态**：若把文件交给多模态模型，要么确认 MinIO 预签名 URL 对模型侧可达，要么后端读字节后 base64 内联——由工作流节点实现，homework 模块只提供文件与路径。

## 5. 表与状态枚举

| 表 | 关键点 |
| --- | --- |
| `homework_task` | status：0=已上传待批改 / 1=批改中 / 2=成功 / 3=部分成功 / 4=失败；`description`（批改要求，grade 时写入）、`space_ids`（逗号分隔） |
| `homework_file` | `relative_path`（形如 `课程/学号-姓名/文件.docx`，本模块产出核心，`(task_id, relative_path)` 唯一）；从属表物理删除、无软删列 |
| `homework_grading_task` / `homework_grading_record` | **旧版表**，仅存于 `restore_dropped_tables_20261022.sql` 恢复脚本，现行代码不使用 |

> 完整列定义见 [doc-database.md](./doc-database.md)。

## 6. 前端流程（`views/homework/HomeworkGradingView.vue` + `utils/grading-run.js`）

1. 挂载：`listWorkflows` 加载工作流（默认 builtin）、`getSpaceList` 加载知识库。
2. FolderUploader 选择 / 拖入文件夹（webkitRelativePath 取相对路径；zip 由前端本地 jszip 解压）。
3. ConfigPanel：填批改要求（description）、选知识库（spaceIds）、导出格式（xlsx/csv）、选工作流。
4. 点「开始批改」：① `POST /homework/upload`（带进度条）→ taskId；② `POST /homework/grade`（仅存档）；③ `getWorkflowDetail` 校验输出节点契约 → `POST /chat/conversation` 建会话 → `POST /api/chat` SSE 流式执行。
5. 中断帧弹人工确认卡片续跑；输出节点帧流式上牌。
6. `completed` 后取逐学生 records + 汇总 statistics，提示含漏批份数统计。

## 7. 现状与规划

| 项 | 现状 | 规划 |
| --- | --- | --- |
| 批改结果导出 | 前端 `handleExport` 标注「待接入后端」，导出格式选择已入库 | 后端导出接口 |
| 批改历史 | `homework_task` 已存档配置，管理端 `/admin/homework/{resources,records}` 页面为前端 mock | 历史查询接口 + 管理端打通 |
| 状态回写 | `/homework/grade` 只存档，执行结果不回写 task.status（status 停在 0） | 执行完成后回写任务状态 |
