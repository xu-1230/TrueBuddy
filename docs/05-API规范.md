# 05 - API 规范

> 项目：TrueBuddy
> 生效日期：2026-09-18 ｜ 规范入口：[README.md](./README.md)
> 适用范围：后端接口定义与前端 `src/api/` 调用层
> 相关：[06-路由规范.md](./06-路由规范.md)、[08-通用约束.md](./08-通用约束.md)

---

## 1. 全局约定

| 项 | 值 |
| --- | --- |
| 端口 | `8086` |
| 上下文路径 | `/api`（`server.servlet.context-path`，**不计入 Controller 映射**） |
| 完整 URL 前缀 | `/api/{模块}/{资源}` |
| 数据格式 | JSON（`application/json`）；文件上传 `multipart/form-data` |
| 上传上限 | 单文件 / 单请求 `50MB` |
| 统一返回 | `CommonResult<T>`（`code = 0` 成功） |
| 认证 | 请求头 `Authorization: Bearer <token>`，或参数 `token` |
| 跨域 | 全局 CORS（`SecurityConfig` 开启），前端开发另走 Vite 代理 |

---

## 2. 后端接口规范

### 2.1 返回结构

所有接口**一律**返回 `CommonResult<T>`：

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
| `tb.security.permit-all-urls` | `application.yml` | URL 白名单：`/system/auth/login`、`/test2/read-pptx`、`/doc-template/**`、`/workflow/**` |
| 兜底 | `SecurityConfig` | `anyRequest().authenticated()`；静态资源与 `DispatcherType.ASYNC` 放行 |

> 新增免登录接口优先使用 `@PermitAll`；批量前缀放行才写入 `permit-all-urls` 并同步本文档。
>
> **待整改**：`permit-all-urls` 中仍保留 `/test2/kb-parse`、`/test2/kb-parse-raw`、`/test2/kb-parse-official`、`/test2/kb-parse-structure` 四条，但 `Test2Controller` 当前只有 `/test2/ping` 与 `/test2/read-pptx`，这四个端点已无对应实现，应从白名单清理。

### 2.5 接口清单（共 10 个 Controller / 28 个接口）

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
| 24 | `WorkflowController` | POST | `/workflow/run` | 提交执行 / 恢复人工确认（**单接口双语义**：不带 `executeId` = 新建执行；带 `executeId` + `confirmedPlan` = 提交确认清单续跑） | `SseEmitter`（`text/event-stream`） |
| 25 | `WorkflowController` | GET | `/workflow/get_process` | 查询执行状态与结果（`workflow_id`；SSE 的**轮询兜底**，建议每 2s，`exeHistoryStatus` 1=执行中 / 2=成功 / 3=失败 / 4=等待用户输入） | `CommonResult<WorkflowProcessRespVO>` |
| 26 | `WorkflowController` | POST | `/workflow/save` | 保存工作流定义（`id` 空 = 新建，非空 = 更新；画布 JSON 整存，**不做版本管理**；返回 String 类型的雪花 id） | `CommonResult<String>` |
| 27 | `WorkflowController` | GET | `/workflow/detail` | 工作流详情（含 `schema_json` 画布字符串） | `CommonResult<WorkflowDetailRespVO>` |
| 28 | `WorkflowController` | GET | `/workflow/list` | 工作流列表（不含画布结构，按更新时间倒序） | `CommonResult<List<WorkflowVO>>` |

> 接口 24~28 为**自定义工作流模块**：工作流定义已持久化到 `workflow` 表（接口 26~28，脚本 `sql/workflow.sql`），大模型与知识库检索仍为**模拟实现**，执行会话存内存（不落库、进程重启即失效）。执行走「**SSE 实时推送 + 轮询兜底**」：`POST /workflow/run` 同步校验画布后落会话并交由后台守护线程执行整个图，同时以 `text/event-stream` 推事件（`created` / `message` / `completed` / `error` / `done`）；`GET /workflow/get_process` 作轮询兜底，返回节点级执行状态（`nodeResults`）与终态业务结果（`result`）。
>
> **人工确认**：执行到「人工确认」节点时图中断，会话置「等待用户输入」（`exeHistoryStatus = 4`，**非终态**）并推 `message`（`type = requires_action`，含 `plan` / `defaultPlan`），**SSE 连接保持打开**；调用方再调一次 `POST /workflow/run`（带 `executeId` + `confirmedPlan`）提交清单，后续事件继续推回原连接。校验失败同样在流内以 `error` + `done` 回传（HTTP 仍 200）。
>
> 白名单放行 `/workflow/**`；前端 `frontend/src/api/workflow.js` 全部接口**已接线**，SSE 读取封装在 `frontend/src/utils/workflow-sse.js`（`fetch` + `ReadableStream`，因 `EventSource` 只支持 GET），事件→状态拼装在 `frontend/src/utils/workflow-run-state.js`，轮询兜底在 `frontend/src/utils/workflow-poll.js`（固定 2s、仅终态 2/3 自停、可 `stop()`）。
>
> `get_process` 的 `data` 口径对齐 Coze 的执行状态接口（`executeId` / `workFlowId` / `exeHistoryStatus` / `executeStatus` / `workflowExeCost` / `nodeResults` / `tokenAndCost` / `projectId`），外层仍是项目 `CommonResult`；`tokenAndCost` 与 `nodeEvents` 为占位，`nodeResults` 不做循环内部子节点的子项拆解。等待用户输入时额外返回 `pendingConfirm`（待确认清单）。

### 2.6 待整改（后端）

| 位置 | 现状 | 规范要求 |
| --- | --- | --- |
| `Test2Controller.readPptx` | 硬编码本机绝对路径读取 pptx | 走配置项或改为上传入参 |
| `PptxPage` | VO 置于 `controller` 包 | 移入 `model/vo/` |
| 列表接口 | 未使用分页 | 数据量增长后改为 `PageResult` + 分页参数 |
| `TestController` / `Test2Controller` | 验证性接口保留在生产代码中 | 明确其为测试用途，上线前下线或收敛 |
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
| `homework.js` | `gradeHomework`、`getGradingHistory`、`getGradingDetail`、`exportGradingResult` | **后端暂无对应接口（待联调）** |
| `workflow.js` | `runWorkflowStream`、`resumeWorkflow`、`getProcess`、`getWorkflowDetail`、`saveWorkflow`、`listWorkflows`（常量 `WORKFLOW_API`，状态码常量 `WORKFLOW_EXE_STATUS` / `WORKFLOW_NODE_STATUS` / `WORKFLOW_STREAM_EVENT` / `WORKFLOW_MESSAGE_TYPE`） | 接口 24~28 **已全部接线**：`views/workflow/WorkflowView.vue`（画布编辑 + 保存 / 打开 + 试运行 + 节点下方人工确认卡片）与 `views/homework/HomeworkWorkflowView.vue`（SSE 实测页，画布取自本地 mock）；SSE 读取封装见 `src/utils/workflow-sse.js`，事件→状态拼装见 `src/utils/workflow-run-state.js`，断线兜底轮询见 `src/utils/workflow-poll.js` |

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
| 认证 | `Authorization: Bearer <token>`（Cookie 存储） | `TokenAuthenticationFilter` 解析 |
| 返回 | 拦截器解包 `code === 0` | 统一 `CommonResult<T>` |
| 上传 | `FormData` + 不设 Content-Type | `MultipartFile` + 50MB 上限 |
| 实时 | `EventSource`（**规划**） | SSE（**规划**，已为 ASYNC 放行） |

---

> 免登录白名单、错误码与安全约束见 [08-通用约束.md](./08-通用约束.md)。
