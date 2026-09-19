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
| `tb.security.permit-all-urls` | `application.yml` | URL 白名单：`/system/auth/login`、`/test2/read-pptx`、`/doc-template/**` |
| 兜底 | `SecurityConfig` | `anyRequest().authenticated()`；静态资源与 `DispatcherType.ASYNC` 放行 |

> 新增免登录接口优先使用 `@PermitAll`；批量前缀放行才写入 `permit-all-urls` 并同步本文档。

### 2.5 接口清单（共 9 个 Controller / 21 个接口）

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
| 18 | `KbDocumentController` | POST | `/knowledge-base/document/upload` | 上传并解析预览分片（multipart） | `CommonResult<Map<String,Object>>` |
| 19 | `KbDocumentController` | POST | `/knowledge-base/document/vectorize` | 向量化落库（`file` + `chunksJson` + `spaceId`） | `CommonResult<Map<String,Object>>` |
| 20 | `KbDocumentController` | DELETE | `/knowledge-base/document/delete` | 删除文档（`id`） | `CommonResult<Boolean>` |
| 21 | `KbDocumentChunkController` | GET | `/knowledge-base/chunk/list` | 文档分片列表（`documentId`） | `CommonResult<List<ChunkRespVO>>` |

### 2.6 待整改（后端）

| 位置 | 现状 | 规范要求 |
| --- | --- | --- |
| `Test2Controller.readPptx` | 硬编码本机绝对路径读取 pptx | 走配置项或改为上传入参 |
| `PptxPage` | VO 置于 `controller` 包 | 移入 `model/vo/` |
| 列表接口 | 未使用分页 | 数据量增长后改为 `PageResult` + 分页参数 |
| `TestController` / `Test2Controller` | 验证性接口保留在生产代码中 | 明确其为测试用途，上线前下线或收敛 |

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
| `workflow.js` | `getWorkflowDetail`、`saveWorkflow`（常量 `WORKFLOW_API`） | **后端暂无对应接口（待联调）** |

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
