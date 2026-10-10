# 05 - API 规范

> 项目：TrueBuddy
> 生效日期：2026-09-18 ｜ 最近核对代码现状：2026-10-10 ｜ 规范入口：[README.md](./README.md)
> 适用范围：后端接口定义与前端 `src/api/` 调用层
> 相关：[06-路由规范.md](./06-路由规范.md)、[08-通用约束.md](./08-通用约束.md)、[12-工作流规范.md](./12-工作流规范.md)

---

## 1. 全局约定

| 项 | 值 |
| --- | --- |
| 端口 | `8086` |
| 上下文路径 | `/api`（`server.servlet.context-path`，**不计入 Controller 映射**） |
| 完整 URL 前缀 | `/api/{模块}/{资源}` |
| 数据格式 | JSON（`application/json`）；文件上传 `multipart/form-data` |
| 上传上限 | 单文件 `50MB`、单请求 `1024MB`（`spring.servlet.multipart`；整包上传靠请求上限放行） |
| 统一返回 | `CommonResult<T>`（`code = 0` 成功）；三处例外：`POST /chat` 返回 `SseEmitter`、`GET /workflow/text` 返回 `void`、`POST /ppt/speech/speak` 直接写 `audio/wav` 字节流 |
| 认证 | 请求头 `Authorization: Bearer <token>`，或参数 `token`（`tb.security.token-parameter`） |
| 跨域 | **未配置可用的 CORS 来源**：`SecurityConfig` 只有 `.cors(withDefaults())`，全仓无 `CorsConfigurationSource` / `@CrossOrigin`，前端开发一律走 Vite 代理 `/api` → `8086` |

---

## 2. 后端接口规范

### 2.1 返回结构

业务接口一律返回 `CommonResult<T>`（两个已记录的例外：`POST /chat` 的 `SseEmitter`、`GET /workflow/text` 的 `void`）：

```json
// 成功
{ "code": 0, "message": "", "data": {} }

// 失败
{ "code": 500, "message": "错误描述", "data": null }
```

- 静态工厂：`CommonResult.success(data)` / `CommonResult.error(code, message)`。
- 分页场景使用 `CommonResult<PageResult<T>>`（`PageResult` = `list` + `total`）。
- 错误码与异常处理见 [08-通用约束.md](./08-通用约束.md) §2。

### 2.2 方法语义

| HTTP 方法 | 语义 | 示例 |
| --- | --- | --- |
| `GET` | 查询（不产生副作用） | `GET /knowledge-base/space/list` |
| `POST` | 新建，或语义明确的动作 | `POST /knowledge-base/space/create`、`POST /doc-template/generate` |
| `PUT` | 更新 | `PUT /knowledge-base/space/update` |
| `DELETE` | 删除 | `DELETE /knowledge-base/document/delete` |

### 2.3 入参与出参

- `@RequestBody` 接收 JSON 请求体；`@RequestParam` 接收查询串 / 表单参数；文件用 `MultipartFile`。
- 入参一律使用 VO（`XxxReqVO` / `XxxSaveReqVO`），**禁止** `DO` 暴露到接口层。
- 出参一律使用 `XxxRespVO`；列表直接返回 `List<XxxRespVO>`（当前项目列表接口未分页，除非明确需要）。
- Controller 只负责参数接收与校验，业务逻辑在 Service。

### 2.4 放行规则

| 机制 | 位置 | 说明 |
| --- | --- | --- |
| `@PermitAll` | 方法 / 类 | 标注即免登录（现有 `AuthController.login`） |
| `tb.security.permit-all-urls` | `application.yml` | URL 白名单：`/system/auth/login`、`/test2/read-pptx`、`/test2/file-text`、`/doc-template/**`、`/workflow/**`、`/homework/**`、`/meeting/**`（实时媒体与聊天走自托管 LiveKit 的 7880/7881，不经过本服务；会议内身份是 `hostToken` / `attendeeToken`，离会接口 `POST /meeting/leave` 也靠它校验）、`/ppt/**`（PPT 讲稿生成，无状态接口） |
| 兜底 | `SecurityConfig` | `anyRequest().authenticated()`；静态资源与 `DispatcherType.ASYNC` 放行 |

> 新增免登录接口优先使用 `@PermitAll`；批量前缀放行才写入 `permit-all-urls` 并同步本文档。
>
> **已整改**：`/test2/kb-parse*` 四条残留已从白名单清理，当前 `permit-all-urls` 与 `Test2Controller` 实际端点一致。
>
> **仍待整改**：`application.yml` 里 `/workflow/**` 上方的注释仍写着「`/run`、`/resume` 为 SSE 流」，而这两个端点已合并为 `POST /chat`（且 `/chat/**` **故意不放行**，要求登录）；注释误导，需随代码修正。

### 2.5 接口清单（共 15 个 `@RestController` / 45 个接口）

> 下表路径均省略上下文前缀 `/api`。

| # | Controller | 方法 | 路径 | 说明 | 返回 |
| --- | --- | --- | --- | --- | --- |
| 1 | `TestController` | GET | `/test/ping` | 连通性测试 | `CommonResult<String>` |
| 2 | `IdentityController` | GET | `/identity/normal` | 普通用户身份校验 | `CommonResult<String>` |
| 3 | `IdentityController` | GET | `/identity/member` | 会员身份校验 | `CommonResult<String>` |
| 4 | `IdentityController` | GET | `/identity/admin` | 管理员身份校验 | `CommonResult<String>` |
| 5 | `AuthController` | POST | `/system/auth/login` | 登录（`@PermitAll`） | `CommonResult<AuthLoginRespVO>` |
| 6 | `Test2Controller` | GET | `/test2/ping` | 连通性测试 | `CommonResult<String>` |
| 7 | `Test2Controller` | GET | `/test2/read-pptx` | 读取本地 pptx 做解析验证（白名单放行） | `CommonResult<List<PptxPage>>` |
| 8 | `FileController` | POST | `/infra/file/upload` | 文件上传（multipart） | `CommonResult<FileUploadRespVO>` |
| 9 | `DocTemplateController` | POST | `/doc-template/generate` | 发起模板复刻生成 | `CommonResult<GenerationRespVO>` |
| 10 | `DocTemplateController` | GET | `/doc-template/history` | 生成历史（可选 `limit`） | `CommonResult<List<GenerationHistoryRespVO>>` |
| 11 | `DocTemplateController` | GET | `/doc-template/detail` | 生成详情（`id`） | `CommonResult<GenerationRespVO>` |
| 12 | `KbSpaceController` | GET | `/knowledge-base/space/list` | 空间列表 | `CommonResult<List<SpaceRespVO>>` |
| 13 | `KbSpaceController` | POST | `/knowledge-base/space/create` | 新建空间 | `CommonResult<Long>` |
| 14 | `KbSpaceController` | PUT | `/knowledge-base/space/update` | 更新空间 | `CommonResult<SpaceRespVO>` |
| 15 | `KbSpaceController` | DELETE | `/knowledge-base/space/delete` | 删除空间（`id`） | `CommonResult<Boolean>` |
| 16 | `KbDocumentController` | GET | `/knowledge-base/document/list` | 文档列表（`spaceId`） | `CommonResult<List<DocumentRespVO>>` |
| 17 | `KbDocumentController` | POST | `/knowledge-base/document/create` | 新建文档记录 | `CommonResult<Long>` |
| 18 | `KbDocumentController` | POST | `/knowledge-base/document/upload` | 上传并提交解析任务（multipart：`files` + `spaceId`） | `CommonResult<List<Map<String,Object>>>` |
| 19 | `KbDocumentController` | GET | `/knowledge-base/document/parse-status` | 轮询解析状态；完成后落块 + AI 分片并返回分片预览（`documentId`） | `CommonResult<Map<String,Object>>` |
| 20 | `KbDocumentController` | POST | `/knowledge-base/document/vectorize` | 确认分片后向量化（multipart：`documentId` + `spaceId` + `chunks`） | `CommonResult<Map<String,Object>>` |
| 21 | `KbDocumentController` | DELETE | `/knowledge-base/document/delete` | 删除文档（`id`） | `CommonResult<Boolean>` |
| 22 | `KbDocumentController` | POST | `/knowledge-base/document/abandon` | 放弃未向量化的文档（`documentId`） | `CommonResult<Boolean>` |
| 23 | `KbDocumentChunkController` | GET | `/knowledge-base/chunk/list` | 文档分片列表（`documentId`） | `CommonResult<List<ChunkRespVO>>` |
| 24 | `WorkflowController` | GET | `/workflow/text` | **联调专用**：同步执行 classpath 内置定义一次，把终态状态键打到控制台，不是业务接口 | `void`（无包装） |
| 25 | `WorkflowController` | POST | `/workflow/save` | 保存工作流定义（`id` 空 = 新建，非空 = 更新；画布 JSON 整存，**不做版本管理**；返回 String 类型的雪花 id） | `CommonResult<String>` |
| 26 | `WorkflowController` | GET | `/workflow/detail` | 工作流详情（query 参数 `workflow_id`；含 `schemaJson` 画布字符串与 `builtin` / `stepCount`） | `CommonResult<WorkflowDetailRespVO>` |
| 27 | `WorkflowController` | GET | `/workflow/list` | 工作流列表（不含 `schemaJson`，按更新时间倒序；含 `builtin` / `stepCount`） | `CommonResult<List<WorkflowVO>>` |
| 28 | `ChatController` | POST | `/chat` | **执行入口（SSE）**：`produces = text/event-stream`，请求体 `{conversation_id, workflow_id?, message?}`；首轮与人工确认续跑同一端点，画布**不内联提交**（靠 `workflow_id` 引用已落库定义）。事件协议见 `ChatProtocol`，详见 [12-工作流规范.md](./12-工作流规范.md) §2.2 | `SseEmitter` |
| 29 | `ChatController` | POST | `/chat/conversation` | 新建会话（`workflow_id` 必须已落库） | `CommonResult<ConversationVO>` |
| 30 | `ChatController` | GET | `/chat/conversation/list` | 当前用户的会话列表 | `CommonResult<List<ConversationVO>>` |
| 31 | `HomeworkController` | POST | `/homework/upload` | 作业文件夹批量上传（multipart：`files` + 可选 `paths`（与 `files` 顺序一一对应的相对路径）+ 可选 `folderName`），落 `homework_task` / `homework_file` 并返回文件树 | `CommonResult<HomeworkUploadRespVO>` |
| 32 | `HomeworkController` | POST | `/homework/grade` | 提交批改（`taskId` / `spaceIds` / `files` / `description` / `exportFormat` / `workflowId`），转交 workflow 执行链 | `CommonResult<Boolean>` |
| 33 | `HomeworkFileTextController` | GET | `/test2/file-text` | 读取作业文件文本，逐条返回、单文件失败不影响整批（HTTP 恒 200）；内部用模拟批改请求，**是联调桩，不是业务接口** | `CommonResult<HomeworkFileTextRespVO>` |
| 34 | `MeetingController` | POST | `/meeting/create` | 创建会议（老师控制台）：返回会议号、入会链接与主持人凭据 | `CommonResult<MeetingCreateRespVO>` |
| 35 | `MeetingController` | POST | `/meeting/join` | 学生入会（会议号 + 昵称），返回参会凭据与会议初始数据；**不限人数**（上限闸门已作废，见 ADR-0001） | `CommonResult<MeetingJoinRespVO>` |
| 36 | `MeetingController` | POST | `/meeting/leave` | 学生离会（`meetingNo` + `attendeeToken`）：只给入会台账补 `leave_time`，幂等；**不是**在线状态来源 | `CommonResult<Boolean>` |
| 37 | `MeetingController` | GET | `/meeting/detail` | 会议详情（`meetingNo` + `hostToken`）：控制台初始化 / 刷新恢复；响应不含 `onlineCount`（在线只看房间事件，ADR-0002） | `CommonResult<MeetingDetailRespVO>` |
| 38 | `MeetingController` | GET | `/meeting/state` | 学生侧会议状态（`meetingNo` + `attendeeToken`）：刷新 / 断线重连后补齐数据；同 `detail` 不下发 `onlineCount` | `CommonResult<MeetingDetailRespVO>` |
| 39 | `MeetingController` | POST | `/meeting/close` | 结束会议（`meetingNo` + `hostToken`）：置为已结束、给台账补离会时间并**删除 LiveKit 房间**（房间内学生立即断开） | `CommonResult<Boolean>` |
| 40 | `MeetingController` | POST | `/meeting/message` | 聊天落库（`meetingNo` + `attendeeToken` 或 `hostToken` + `content`）；实时由 LiveKit DataChannel 承担，本接口只写历史 | `CommonResult<MeetingMessageVO>` |
| 41 | `MeetingController` | GET | `/meeting/digital/status` | 数字人可用性探测（`meetingNo`；后端会按 `worker-probe-timeout-ms` 探一次 worker 的 `/healthz`），前端据此显示「数字人不可用」而不是静默无声 | `CommonResult<DigitalStatusVO>` |
| 42 | `MeetingController` | POST | `/meeting/digital/say` | 让数字人在会议里播报一段文本（`meetingNo` + `hostToken` + `text`，长度受 `tb.lecture.digital-human.max-text-length` 约束）；合成走 CosyVoice、发布走 Node worker。**`requestId` 可由客户端自带**（8–64 位 `[A-Za-z0-9_-]`，不合规则后端生成）——因为本接口同步返回即代表整段已交给 worker，前端要能在响应回来之前就打断它，见 [18](./18-放映与会议演讲体系方案.md) §5.4 | `CommonResult<DigitalSayRespVO>` |
| 43 | `MeetingController` | POST | `/meeting/digital/say/cancel` | 掐断一条在途播报（`meetingNo` + `hostToken` + `requestId`）：后端转调 worker 的 `DELETE /say/{id}` 清未播队列。幂等，已播完时返回 `false` 不报错 | `CommonResult<Boolean>` |
| 44 | `PptSpeechController` | POST | `/ppt/speech/speak` | 试听：把一段文本合成成 WAV 直接回给浏览器，**不进 LiveKit 房间、不依赖 worker**（放映形态自己听，见 [18](./18-放映与会议演讲体系方案.md) §5.3）。上限走 `tb.lecture.digital-human.max-speak-chars`（比会中播报宽松，整页 `remark` 要一次进来） | `void`（`audio/wav` 字节流；合成失败发生在写出首字节前，此时仍返回 `CommonResult` JSON） |
| 45 | `PptSpeechController` | POST | `/ppt/speech/optimize` | PPT 讲稿生成（`pages` 长度 1 = 本页，长度 N = 全部）；无状态、不落库，内部按 `tb.lecture.ppt.batch-size` 分批调用模型，部分失败走 `failedIndexes` | `CommonResult<PptSpeechOptimizeRespVO>` |

> 接口 24~30 属**自定义工作流模块**（包根 `org.example.textflow`），分两块：
>
> - **定义持久化（接口 25~27）**：工作流定义落在 `workflow` 表（脚本 `sql/workflow.sql`，表结构见 [12-工作流规范.md](./12-工作流规范.md) §6）。`POST /workflow/save` 新建 / 更新定义（`id` 空 = 新建、非空 = 更新；`id` 全程为**字符串形式**的雪花 id，避免前端 JS 丢精度）；`GET /workflow/detail` 返回含 `schemaJson` 的详情；`GET /workflow/list` 返回不含画布的列表。详情与列表都带 `builtin`（是否内置模板）与 `stepCount`（顶层节点数），供批改页工作流下拉分组与「N 个步骤」展示。内置模板「标准批改作业」由启动初始化器在表为空时写入（固定 id，幂等）。
> - **执行链路（接口 28~30）**：入口是 **`POST /chat`**（不再是 `POST /workflow/run`，也没有 `POST /workflow/resume`）。请求体 `{conversationId, workflowId?, message?}`：无 `conversationId` 时先建会话，**首轮与人工确认续跑共用同一端点**，靠「该会话最新一条执行记录的状态」分流（客户端不传状态）。事件帧为 `conversation.chat.in_progress` → `conversation.message.completed`\*（节点产出 / 表单卡片 / 正文）→ 终态帧 `conversation.chat.{completed,failed,requires_action}`，每轮都以 `done` 收尾（**终态看帧，不看流是否结束**）。会话与执行实例落 `workflow_conversation` / `workflow_run`；**中断点与图 checkpoint 仍是内存 Map**（`executeId` → `InterruptionMetadata`、`MemorySaver`），进程重启后续跑会退化为新执行。LLM 节点注入真实 `ChatClient`（知识库检索工具仍为占位）。
>
> 放行与鉴权的分工：`/workflow/**` 免登录（联调方便），**`/chat/**` 不在白名单内**——会话与执行实例要记 `creator` 并按用户隔离，因此要求登录（这也是它不挂在 `/workflow` 前缀下的原因）。
> 前端接口层 `frontend/src/api/workflow.js`：`CHAT_API`（`chat` = `/api/chat`，**带** `/api` 前缀，因为 `fetch` 不走 axios 实例；`conversation` / `conversationList` **不带**前缀）与 `WORKFLOW_REST_API`（`save` / `detail` / `list`，走 axios，避免与 `baseURL` 重复拼接），导出 `buildChatBody` / `buildCardSubmitBody` / `buildRunDefinition` / `buildWorkflowSchema` / `saveWorkflow` / `getWorkflowDetail` / `listWorkflows` / `createConversation` / `listConversations`。SSE 读取封装在 `frontend/src/utils/workflow-sse.js` 的 `readWorkflowStream`（`fetch` + `ReadableStream`，因 `EventSource` 只支持 GET），事件到状态快照的归约在 `views/workflow2/utils/run-state.js`。消费方：画布页 `views/workflow2/WorkflowView.vue`（建会话 / 试运行 / 节点下方人工确认卡片）、批改页 `views/homework/HomeworkGradingView.vue`（工作流下拉 `listWorkflows`）与首页 `views/system/SystemHomeView.vue`（`listWorkflows` / `listConversations` 拉动态）。

### 2.6 待整改（后端）

| 位置 | 现状 | 规范要求 |
| --- | --- | --- |
| `Test2Controller.readPptx` | 硬编码本机绝对路径读取 pptx | 走配置项或改为上传入参 |
| `PptxPage` | VO 置于 `controller` 包 | 移入 `model/vo/` |
| 列表接口 | 未使用分页 | 数据量增长后改为 `PageResult` + 分页参数 |
| `TestController` / `Test2Controller` | 验证性接口保留在生产代码中 | 明确其为测试用途，上线前下线或收敛 |
| `HomeworkController.grade` | 方法体里用 `System.out.println(reqVO)` 打请求 | 改用日志（`log.info`）或删除，禁止 `System.out` |
| `HomeworkFileTextController` | `/test2/file-text` 内部走 `mockGradeReq()` 硬编码样本，白名单放行 | 联调桩不得当业务接口用；接入真实作业来源后再决定去留 |
| `SecurityConfig.filterChain` | `permitAllUrls` 容器始终为空（未采集 `@PermitAll` 注解），方法级 `@PermitAll` 实际不生效 | 统一走 `tb.security.permit-all-urls`，或补齐注解采集逻辑 |

---

## 3. 前端 API 层规范 — `src/api/`

### 3.1 axios 实例

`src/api/index.js` 创建统一实例：

```js
import axios from "axios";
import { getToken } from "@/utils/auth";

const service = axios.create({
  baseURL: "/api",
  // 不设置默认 Content-Type：JSON 由 axios 自动设为 application/json；
  // FormData 交给浏览器生成 multipart/form-data（含 boundary）
});
```

| 约束 | 说明 |
| --- | --- |
| `baseURL` | 固定 `/api`，与后端上下文路径一致，开发经 Vite 代理 |
| 默认 `Content-Type` | **禁止**手动设置（否则会破坏 FormData 的 boundary） |
| 超时 | 当前未设置默认超时；长耗时上传需在具体接口上单独指定（如 `timeout: 0`） |

### 3.2 拦截器

- **请求拦截器**：从 Cookie 读取 Token（`getToken()`），存在时写入 `config.headers.Authorization = \`Bearer ${token}\``。
- **响应拦截器**：
  - `res.code === 0` → 直接 `return res.data`（透明解包 `CommonResult`）；
  - `res.code !== 0` → `reject` 一个 `Error(res.message)`，并在 `error.code` 上挂业务码，供业务层区分处理；
  - 网络层错误 → 原样 `reject`。

```js
service.interceptors.response.use(
  (response) => {
    const res = response.data;
    if (res.code === 0) return res.data;
    const error = new Error(res.message || "请求失败");
    error.code = res.code;
    return Promise.reject(error);
  },
  (error) => Promise.reject(error),
);
```

> **规划**：401 统一跳转登录、错误统一弹窗提示尚未实现，当前由业务层自行 `try/catch`。

### 3.3 文件组织与命名

- **一个业务模块一个文件**，默认导出实例、具名导出接口函数。
- 函数命名：`动词 + 资源`，camelCase（`getSpaceList`、`createSpace`、`deleteDocument`、`uploadFile`、`generateDoc`）。
- 复杂出入参用 JSDoc 声明契约（参考 `api/doctemplate.js`）。
- 文件顶部注明接口状态（可用 / 待联调）。

| 文件 | 导出函数 | 对应后端 |
| --- | --- | --- |
| `test.js` | `login`、`ping`、`ping2`、`identityNormal`、`identityMember`、`identityAdmin` | 接口 1~6 |
| `doctemplate.js` | `generateDoc`、`getGenerationHistory`、`getGenerationDetail` | 接口 9~11 |
| `file.js` | `uploadFile` | 接口 8 |
| `knowledgebase.js` | `getSpaceList`、`createSpace`、`updateSpace`、`deleteSpace`、`getDocumentList`、`createDocument`、`deleteDocument`、`getChunkList`、`uploadDocument`、`vectorizeDocument` | 接口 12~21 |
| `homework.js` | `uploadHomeworkFiles(files, paths, folderName, onUploadProgress)`、`submitGrading(payload)` | 接口 31~32：`views/homework/HomeworkGradingView.vue` 已接线（上传成功后串行提交批改，上传带进度回调）。历史的 `gradeHomework` / `getGradingHistory` / `getGradingDetail` / `exportGradingResult` 属演示态，已随页面改造移除；批改**历史与导出**目前仍走本地，无后端接口 |
| `workflow.js` | `buildChatBody`、`buildCardSubmitBody`、`buildRunDefinition`、`buildWorkflowSchema`、`saveWorkflow`、`getWorkflowDetail`、`listWorkflows`、`createConversation`、`listConversations`；常量 `CHAT_API`（SSE / 会话地址，其中 `chat` **含** `/api` 前缀）与 `WORKFLOW_REST_API`（REST 路径，无前缀） | 接口 25~30：`views/workflow2/WorkflowView.vue`（画布编辑 + 保存对话框 + 按 `workflow_id` 打开 + 建会话 + 试运行 + 节点下方人工确认卡片）、`views/homework/HomeworkGradingView.vue` 与 `views/system/SystemHomeView.vue`（`listWorkflows` / `listConversations`）；SSE 读取封装见 `src/utils/workflow-sse.js`。**没有轮询兜底**：`GET /workflow/get_process` 从未实现，终态只认 SSE 帧 |
| `meeting.js` | `createMeeting`、`joinMeeting`、`leaveMeeting`、`getMeetingDetail`、`getMeetingState`、`sendMeetingMessage`、`closeMeeting`、`getDigitalStatus`、`sayDigital`；常量 `MEETING_REST_API` | 接口 34~42：`views/meeting/MeetingConsoleView.vue`（老师控制台）、`views/meeting/MeetingRoomView.vue`（学生会议室）、`views/meeting/components/DigitalHumanAvatar.vue`（播报与可用性状态）。`leaveMeeting` 走 `navigator.sendBeacon`（组件卸载路径上普通请求会被浏览器掐掉），因此带 `/api` 前缀而非用 axios 实例。实时能力不在本文件内：LiveKit 房间客户端见 `views/meeting/hooks/useLiveKitRoom.js`，「在线成员 vs 入会台账」的裁决见 `views/meeting/utils/attendees.js`，数字人 PCM 播放见 `views/meeting/utils/agentAudio.js` |
| `ppt.js` | `optimizeSpeech(payload, signal)`；常量 `PPT_REST_API` | 接口 43：`views/ppt/index.vue`（备注区星形按钮 = 本页、工具栏按钮 = 全部）+ 两个对比弹窗（`views/ppt/components/ai/`）。整篇是**一次长请求**，离开页面用 `AbortController` 中止 |

### 3.4 调用约定

```js
import { getSpaceList } from "@/api/knowledgebase";

async function load() {
  try {
    spaces.value = await getSpaceList(); // 已解包，直接是业务数据
  } catch (error) {
    ElMessage.error(error.message);
  }
}
```

- 业务代码**不再解包** `CommonResult`，拦截器已完成。
- 必须 `try/catch`，并给出用户可见的失败反馈。

---

## 4. 前后端对接清单

| 项 | 前端 | 后端 |
| --- | --- | --- |
| 前缀 | `baseURL: "/api"` | `context-path: /api` |
| 认证 | 登录态 `Authorization: Bearer <token>`（Cookie，`utils/auth.js`）；会议凭据另走 `hostToken` / `attendeeToken`（localStorage / sessionStorage，不进 `auth.js`） | `TokenAuthenticationFilter` 解析 Bearer，白名单见 §2.4 |
| 返回 | 拦截器解包 `code === 0` | 统一 `CommonResult<T>`（SSE / `void` 例外） |
| 上传 | `FormData` + 不设 Content-Type | `MultipartFile` + 单文件 50MB / 单请求 1024MB |
| 实时 | **SSE 已实现**：`fetch` + `ReadableStream`（`utils/workflow-sse.js`），未用 `EventSource`（只支持 GET，且无法带请求体）；会议走 `livekit-client` 直连 7880 | `POST /chat` 返回 `SseEmitter`（异步不超时，ASYNC 已放行）；数字人音频轨由 `digital-human-worker/` 发布 |

---

> 免登录白名单、错误码与安全约束见 [08-通用约束.md](./08-通用约束.md)。
