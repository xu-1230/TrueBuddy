# TrueBuddy API 接口文档

> 项目：TrueBuddy（AI 驱动的教学辅助平台）
> 后端：Spring Boot 多模块（`backend/`） ｜ 文档生成日期：2026-10-18
> 适用范围：前后端联调、第三方集成、接口回归

---

## 目录

- [1. 概述](#1-概述)
  - [1.1 服务地址](#11-服务地址)
  - [1.2 统一响应结构](#12-统一响应结构)
  - [1.3 认证与鉴权](#13-认证与鉴权)
  - [1.4 通用约定](#14-通用约定)
  - [1.5 错误码规范](#15-错误码规范)
- [2. 接口索引](#2-接口索引)
- [3. 接口详情](#3-接口详情)
  - [3.1 认证模块](#31-认证模块)
  - [3.2 文件模块](#32-文件模块)
  - [3.3 文档模板生成模块](#33-文档模板生成模块)
  - [3.4 知识库空间模块](#34-知识库空间模块)
  - [3.5 知识库文档模块](#35-知识库文档模块)
  - [3.6 知识库文档分片模块](#36-知识库文档分片模块)
  - [3.7 工作流定义模块](#37-工作流定义模块)
  - [3.8 工作流对话执行模块](#38-工作流对话执行模块)
  - [3.9 作业批改模块](#39-作业批改模块)
  - [3.10 模拟会议模块](#310-模拟会议模块)
  - [3.11 PPT 讲稿模块](#311-ppt-讲稿模块)
  - [3.12 测试与联调模块](#312-测试与联调模块)
  - [3.13 管理端身份模块](#313-管理端身份模块)
- [4. 附录](#4-附录)
  - [4.1 错误码总表](#41-错误码总表)
  - [4.2 枚举速查](#42-枚举速查)
  - [4.3 数据模型](#43-数据模型)

---

## 1. 概述

### 1.1 服务地址

| 项 | 值 |
| --- | --- |
| 协议 | HTTP |
| 主机 | `http://localhost:8086` |
| 上下文路径（context-path） | `/api` |
| 完整 URL 前缀 | `http://localhost:8086/api` |
| 数据格式 | 请求/响应体 `application/json`；文件上传 `multipart/form-data`；`/chat` 与 `/ppt/speech/speak` 为 `text/event-stream` |
| 字符编码 | UTF-8 |
| 上传上限 | 单文件 `50MB`；单请求 `1024MB`（`spring.servlet.multipart`） |

> 下文所有「请求路径」均**省略**上下文前缀 `/api`。实际调用需拼上，例如 `GET /test/ping` → `GET http://localhost:8086/api/test/ping`。

### 1.2 统一响应结构

除两个事件流接口（`POST /chat`、`POST /ppt/speech/speak`）与联调接口（`GET /workflow/text`）外，**所有接口**统一返回 `CommonResult<T>`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | int | 业务状态码，`0` = 成功；非 `0` = 失败 |
| `message` | String | 提示信息，成功时为空串 `""`，失败时可直接展示给用户 |
| `data` | T | 业务数据，泛型；失败时通常为 `null` |

**成功响应示例**

```json
{
  "code": 0,
  "message": "",
  "data": { "id": 1, "name": "示例" }
}
```

**失败响应示例**

```json
{
  "code": 1003000000,
  "message": "知识库空间不存在",
  "data": null
}
```

### 1.3 认证与鉴权

- 安全框架：Spring Security（**无状态**，Token 为随机 UUID，非 JWT），由 `TokenAuthenticationFilter` 校验。
- Token 传递方式（二选一）：
  1. 请求头：`Authorization: Bearer <accessToken>`（也兼容不加 `Bearer ` 前缀）
  2. 请求参数：`?token=<accessToken>`
- Token 有效期：`accessToken` 默认 **2 小时**（`7200` 秒），`refreshToken` 默认 **7 天**（`604800` 秒）。
- 刷新模型：accessToken 过期后，前端用 `POST /system/auth/refresh` 换取新令牌；旧 refreshToken **一次性消费**，刷新成功后返回新的 accessToken + refreshToken。refreshToken 不能再直接作为 Bearer Token 使用。

**免登录（白名单）接口**，无需携带 Token（`tb.security.permit-all-urls`）：

| 路径 | 说明 |
| --- | --- |
| `/system/auth/login` | 登录（另标注了 `@PermitAll`） |
| `/system/auth/refresh` | 刷新令牌（accessToken 往往已过期，必须免登录） |
| `/doc-template/**` | 文档模板生成模块全部接口 |
| `/workflow/**` | 工作流定义模块全部接口（含联调接口 `/workflow/text`） |
| `/homework/**` | 作业批改模块全部接口 |
| `/meeting/**` | 模拟会议模块全部接口（会议内身份用 `hostToken` / `attendeeToken`） |
| `/ppt/**` | PPT 讲稿模块全部接口（无状态、不落库） |
| `/test2/read-pptx` | PPTX 读取验证 |
| `/test2/file-text` | 作业文件文本读取（联调桩） |

**需登录**的模块：`/infra/**`、`/knowledge-base/**`、`/chat/**`、`/admin/**`、`/test/ping`、`/test2/ping`。
其中 `/admin/auth/profile` 与 `/admin/auth/permissions` 还要求 `admin:dashboard` 权限（管理员与审核员拥有，普通用户与会员返回 403）。

> `/chat/**` **故意不放入白名单**：会话与执行实例要记录 `creator`，因此要求登录；这也是它没有挂在 `/workflow` 前缀下的原因。
>
> 未携带或携带无效 Token 访问受保护接口时，由 Spring Security 直接拦截（HTTP 4xx），响应体**不是** `CommonResult`。

### 1.4 通用约定

| 项 | 约定 |
| --- | --- |
| HTTP 方法语义 | `GET` 查询 ｜ `POST` 新建或动作 ｜ `PUT` 更新 ｜ `DELETE` 删除 |
| 时间字段 | `LocalDateTime` 序列化为 `yyyy-MM-dd HH:mm:ss`；SSE 帧内的 `created_at` / `completed_at` 为**秒级时间戳**（`Long`） |
| 长整型 ID | 普通模块为 `Long`（JSON 数字）；工作流 / 会话 / 执行相关 ID 一律为 **String**（雪花 ID 超出 JS 安全整数范围） |
| 入参 | 一律使用 `XxxReqVO` / `XxxSaveReqVO`；文件字段使用 `multipart/form-data` |
| 出参 | 一律使用 `XxxRespVO` 或 `List<XxxRespVO>`；列表接口**当前未分页** |
| 字段命名 | REST 请求/响应体用 **camelCase**；`/chat` 的 SSE 帧用 **snake_case**（与 Coze 事件协议对齐） |
| 文件上传 | 不要手动设置 `Content-Type`，由客户端自动生成带 boundary 的 `multipart/form-data` |
| 跨域 | 后端只声明了 `.cors(withDefaults())`，**未配置可用的 CORS 来源**；前端开发环境一律经 Vite 代理 `/api` → `8086` |

### 1.5 错误码规范

- 全局错误码：`0` 成功、`400` 请求参数错误、`401` 未登录、`403` 无权限、`404` 路径未找到、`405` 方法不允许、`500` 系统异常。
- 业务错误码：`1_00X_YYY_ZZZ` 格式，`X` 为模块号（见 [4.1 错误码总表](#41-错误码总表)）。
- 前端建议：响应拦截器已自动解包 `code === 0` 的场景，非 0 时抛出 `Error(message)`，业务层 `try/catch` 后展示 `message`。
- 事件流例外：`POST /chat` 建流后的失败以 SSE `conversation.chat.failed` 帧下发（`last_error.code = 500`），HTTP 仍为 200；建流前的参数校验失败仍走普通 `CommonResult`。

---

## 2. 接口索引

> 共 **15 个 Controller / 43 个接口**。路径省略 `/api` 前缀。

| # | 模块 | 方法 | 路径 | 说明 | 鉴权 |
| --- | --- | --- | --- | --- | --- |
| 1 | 认证 | POST | `/system/auth/login` | 账号密码登录 | 免登录 |
| 2 | 文件 | POST | `/infra/file/upload` | 通用文件上传 | 需登录 |
| 3 | 模板生成 | POST | `/doc-template/generate` | 发起模板复刻生成 | 免登录 |
| 4 | 模板生成 | GET | `/doc-template/history` | 生成历史记录 | 免登录 |
| 5 | 模板生成 | GET | `/doc-template/detail` | 生成详情 | 免登录 |
| 6 | 知识库空间 | GET | `/knowledge-base/space/list` | 空间列表 | 需登录 |
| 7 | 知识库空间 | POST | `/knowledge-base/space/create` | 新建空间 | 需登录 |
| 8 | 知识库空间 | PUT | `/knowledge-base/space/update` | 更新空间 | 需登录 |
| 9 | 知识库空间 | DELETE | `/knowledge-base/space/delete` | 删除空间 | 需登录 |
| 10 | 知识库文档 | GET | `/knowledge-base/document/list` | 文档列表 | 需登录 |
| 11 | 知识库文档 | POST | `/knowledge-base/document/create` | 新建文档记录 | 需登录 |
| 12 | 知识库文档 | POST | `/knowledge-base/document/upload` | 上传文件并提交解析 | 需登录 |
| 13 | 知识库文档 | GET | `/knowledge-base/document/parse-status` | 轮询解析状态 / 分片预览 | 需登录 |
| 14 | 知识库文档 | POST | `/knowledge-base/document/vectorize` | 确认分片后向量化 | 需登录 |
| 15 | 知识库文档 | DELETE | `/knowledge-base/document/delete` | 删除文档 | 需登录 |
| 16 | 知识库文档 | POST | `/knowledge-base/document/abandon` | 放弃未向量化文档 | 需登录 |
| 17 | 文档分片 | GET | `/knowledge-base/chunk/list` | 文档分片列表 | 需登录 |
| 18 | 工作流定义 | GET | `/workflow/text` | **联调专用**：同步执行内置定义 | 免登录 |
| 19 | 工作流定义 | POST | `/workflow/save` | 保存工作流定义 | 免登录 |
| 20 | 工作流定义 | GET | `/workflow/detail` | 工作流详情（含画布） | 免登录 |
| 21 | 工作流定义 | GET | `/workflow/list` | 工作流列表 | 免登录 |
| 22 | 对话执行 | POST | `/chat` | 发起执行 / 人工确认续跑（SSE） | 需登录 |
| 23 | 对话执行 | POST | `/chat/conversation` | 新建会话 | 需登录 |
| 24 | 对话执行 | GET | `/chat/conversation/list` | 会话列表 | 需登录 |
| 25 | 作业批改 | POST | `/homework/upload` | 作业文件夹批量上传 | 免登录 |
| 26 | 作业批改 | POST | `/homework/grade` | 提交批改请求 | 免登录 |
| 27 | 模拟会议 | POST | `/meeting/create` | 创建会议 | 免登录 |
| 28 | 模拟会议 | POST | `/meeting/join` | 学生入会 | 免登录 |
| 29 | 模拟会议 | GET | `/meeting/detail` | 会议详情（老师控制台） | 免登录 |
| 30 | 模拟会议 | GET | `/meeting/state` | 会议状态（学生侧刷新恢复） | 免登录 |
| 31 | 模拟会议 | POST | `/meeting/message` | 聊天消息落库 | 免登录 |
| 32 | 模拟会议 | POST | `/meeting/leave` | 学生离会（补记台账） | 免登录 |
| 33 | 模拟会议 | POST | `/meeting/close` | 结束会议 | 免登录 |
| 34 | 模拟会议 | GET | `/meeting/digital/status` | 语音播报可用性 | 免登录 |
| 35 | PPT 讲稿 | POST | `/ppt/speech/optimize` | 生成 / 优化讲稿 | 免登录 |
| 36 | PPT 讲稿 | POST | `/ppt/speech/speak` | 讲稿流式播报（SSE） | 免登录 |
| 37 | 测试联调 | GET | `/test/ping` | 连通性测试 | 需登录 |
| 38 | 测试联调 | GET | `/test2/ping` | 连通性测试 | 需登录 |
| 39 | 测试联调 | GET | `/test2/read-pptx` | 读取本地 PPTX 验证解析 | 免登录 |
| 40 | 测试联调 | GET | `/test2/file-text` | 读取作业文件文本（联调桩） | 免登录 |
| 41 | 管理端身份 | GET | `/admin/auth/profile` | 当前账号资料 + 角色 + 权限 | 需登录 + `admin:dashboard` |
| 42 | 管理端身份 | GET | `/admin/auth/permissions` | 当前账号权限码 | 需登录 + `admin:dashboard` |
| 43 | 认证 | POST | `/system/auth/refresh` | 刷新令牌换取新令牌（旧 refreshToken 一次性消费） | 免登录 |

---

## 3. 接口详情

### 3.1 认证模块

#### 3.1.1 登录

| 项 | 值 |
| --- | --- |
| 请求方法 | `POST` |
| 请求路径 | `/system/auth/login` |
| 鉴权 | 免登录 |
| Content-Type | `application/json` |

**请求参数（Body）**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `username` | String | 是 | 登录账号 |
| `password` | String | 是 | 登录密码（明文传输，内网部署） |

**请求示例**

```bash
curl -X POST http://localhost:8086/api/system/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

**响应字段（data，`AuthLoginRespVO`）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `userId` | Long | 用户 ID |
| `roles` | String[] | 角色编码集合（只含启用角色）：`normal` / `member` / `admin` / `auditor` |
| `permissions` | String[] | 权限码集合，来自 `system_menu.permission`（角色与菜单均需启用） |
| `accessToken` | String | 访问令牌，后续请求写入 `Authorization` |
| `refreshToken` | String | 刷新令牌（有效期 7 天） |
| `expiresTime` | LocalDateTime | accessToken 过期时间 |

**响应示例**

```json
{
  "code": 0,
  "message": "",
  "data": {
    "userId": 1,
    "roles": ["admin"],
    "permissions": ["admin:dashboard", "system:user:list", "system:role:list"],
    "accessToken": "5f1c9a7be3d84c0d8a2f6b7c9d1e0a3b",
    "refreshToken": "9a8b7c6d5e4f3a2b1c0d9e8f7a6b5c4d",
    "expiresTime": "2026-10-18 18:30:00"
  }
}
```

**可能错误**

| code | message |
| --- | --- |
| `1002002000` | 登录失败，账号密码不正确 |
| `1002002001` | 登录失败，账号被禁用 |

> 角色与权限随登录一并下发；管理端初始化仍以 `/admin/auth/profile` 为权威。
> **安全提示**：当前 `AdminUserServiceImpl.isPasswordMatch` 恒为 `true`（演示便利），上线前必须改为 BCrypt 校验。

---

#### 3.1.2 刷新令牌

| 项 | 值 |
| --- | --- |
| 请求方法 | `POST` |
| 请求路径 | `/system/auth/refresh` |
| 鉴权 | 免登录（accessToken 可能已过期） |
| Content-Type | `application/json` |

**请求参数（Body，`AuthRefreshReqVO`）**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `refreshToken` | String | 是 | 登录或上次刷新下发的刷新令牌；一次性使用 |

**请求示例**

```bash
curl -X POST http://localhost:8086/api/system/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"9a8b7c6d5e4f3a2b1c0d9e8f7a6b5c4d"}'
```

**响应字段（data）**：结构同 [3.1.1](#311-登录) 的 `AuthLoginRespVO`，即新的 `accessToken`、新的 `refreshToken`、`expiresTime`，以及重新解析的 `roles` / `permissions`。

**响应示例**

```json
{
  "code": 0,
  "message": "",
  "data": {
    "userId": 1,
    "roles": ["admin"],
    "permissions": ["admin:dashboard", "system:user:list"],
    "accessToken": "b6e2d1f0a9c84e7d8b3a5c2f1e0d9b8a",
    "refreshToken": "3c1d9e8f7a6b5c4d2e0f1a3b5c7d9e8f",
    "expiresTime": "2026-10-18 20:30:00"
  }
}
```

**可能错误**

| code | message | 触发条件 |
| --- | --- | --- |
| `401` | 刷新令牌不能为空 | 请求体缺少 `refreshToken` |
| `401` | 刷新令牌不存在或已失效 | 已被消费、伪造或已删除 |
| `401` | 刷新令牌已过期，请重新登录 | 超过 7 天 |
| `401` | 刷新令牌已被使用，请重新登录 | 并发刷新时后到的请求 |

> 旧 refreshToken 在刷新成功后立即从 `system_oauth2_refresh_token` 删除；重复使用同一个 refreshToken 会被拒绝，客户端应清空本地登录态并重新登录。

---

### 3.2 文件模块

#### 3.2.1 文件上传

| 项 | 值 |
| --- | --- |
| 请求方法 | `POST` |
| 请求路径 | `/infra/file/upload` |
| 鉴权 | 需登录 |
| Content-Type | `multipart/form-data` |

**请求参数（form-data）**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `file` | File | 是 | 待上传文件，单文件上限 `50MB`，**空文件会被拒绝** |

**允许的扩展名**：`pdf`、`doc`、`docx`、`md`、`markdown`、`txt`、`ppt`、`pptx`、`xls`、`xlsx`、`png`、`jpg`、`jpeg`、`gif`、`webp`

**请求示例**

```bash
curl -X POST http://localhost:8086/api/infra/file/upload \
  -H "Authorization: Bearer <token>" \
  -F "file=@D:/files/教学模板.docx"
```

**响应字段（data，`FileUploadRespVO`）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | Long | 文件 ID（`infra_file` 主键），供后续业务引用 |
| `name` | String | 原始文件名（含扩展名） |
| `size` | Long | 文件大小（字节） |
| `type` | String | 文件 MIME 类型 |
| `url` | String | 访问地址（MinIO 预签名 URL，**7 天有效**） |

**响应示例**

```json
{
  "code": 0,
  "message": "",
  "data": {
    "id": 12,
    "name": "教学模板.docx",
    "size": 20480,
    "type": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "url": "http://localhost:9000/test-bucket/infra/file/xxx.docx?X-Amz-Expires=604800&..."
  }
}
```

**可能错误**

| code | message |
| --- | --- |
| `1001000000` | 上传文件不能为空 |
| `1001000001` | 文件大小超过上限（50MB） |
| `1001000002` | 不支持的文件类型 |

---

### 3.3 文档模板生成模块

> 模块前缀 `/doc-template`，整体免登录。业务流程：上传模板 docx → 提交生成任务 → 查询历史/详情。

#### 3.3.1 发起模板复刻生成

| 项 | 值 |
| --- | --- |
| 请求方法 | `POST` |
| 请求路径 | `/doc-template/generate` |
| 鉴权 | 免登录 |
| Content-Type | `application/json` |
| 超时建议 | 前端建议不设超时（生成耗时较长） |

**请求参数（Body，`GenerateReqVO`）**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `templateFileId` | Long | 是 | 模板文件 ID（`/infra/file/upload` 返回的 `id`），必须为 `.docx` |
| `spaceId` | String | 否 | 知识库空间 ID；为空或 `"default"` 时不启用检索增强 |
| `description` | String | 是 | AI 描述说明，**不超过 500 字** |
| `quality` | String | 否 | 清晰度参数：`480p` / `720p` / `1080p` |

**请求示例**

```bash
curl -X POST http://localhost:8086/api/doc-template/generate \
  -H "Content-Type: application/json" \
  -d '{
    "templateFileId": 12,
    "spaceId": "3",
    "description": "按模板生成一节《企业级应用开发》教案，重点讲 Spring Boot 自动装配。",
    "quality": "1080p"
  }'
```

**响应字段（data，`GenerationRespVO`）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `generationId` | Long | 生成任务 ID，可用于 `/doc-template/detail` 查询 |
| `status` | Integer | 任务状态：`1` 成功 / `2` 部分成功 / `3` 失败 |
| `durationMs` | Long | 任务耗时（毫秒） |
| `errorMsg` | String | 失败/部分失败的错误摘要，成功时为空 |
| `docs` | Array | 生成文件列表，见下方 `DocFileRespVO` |

**`docs[]` 元素字段（`DocFileRespVO`）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | Long | 结果记录 ID（`doc_template_file` 主键） |
| `fileId` | Long | 文件 ID（`infra_file` 主键） |
| `name` | String | 结果文件名（含扩展名） |
| `type` | String | 文档类型：`WORD` / `PDF` / `MARKDOWN` |
| `size` | Long | 文件大小（字节） |
| `tag` | String | 份数定位标签：复刻 / 优化 / 精简 |
| `summary` | String | 内容摘要 |
| `src` | String | 访问地址（MinIO 预签名 URL） |
| `createTime` | LocalDateTime | 创建时间 |

**响应示例**

```json
{
  "code": 0,
  "message": "",
  "data": {
    "generationId": 8,
    "status": 1,
    "durationMs": 45210,
    "errorMsg": "",
    "docs": [
      {
        "id": 31,
        "fileId": 45,
        "name": "企业级应用开发-教案.docx",
        "type": "WORD",
        "size": 32768,
        "tag": "复刻",
        "summary": "围绕 Spring Boot 自动装配原理设计的 45 分钟教案",
        "src": "http://localhost:9000/test-bucket/doc-template/xxx.docx?X-Amz-Expires=604800&...",
        "createTime": "2026-10-18 10:12:33"
      }
    ]
  }
}
```

**可能错误**

| code | message |
| --- | --- |
| `1004000000` | 请先上传模板文档 |
| `1004000001` | 请填写 AI 描述说明 |
| `1004000002` | AI 描述说明不能超过 500 字 |
| `1004000003` | 模板文件不存在或已被删除 |
| `1004000004` | 仅支持 .docx 模板文档 |
| `1004000006` | 文档生成失败：{原因} |
| `1004000007` | 文档处理服务（docx-mcp）不可用：{原因} |
| `1004000008` | AI 返回内容解析失败：{原因} |

> 号段提示：`1_004_000_xxx` 当前被本模块与 `homework` 模块**共用**，详见 [4.1](#41-错误码总表) 末尾说明。

---

#### 3.3.2 生成历史记录

| 项 | 值 |
| --- | --- |
| 请求方法 | `GET` |
| 请求路径 | `/doc-template/history` |
| 鉴权 | 免登录 |

**请求参数（Query）**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `limit` | Integer | 否 | 返回条数上限；不传或传 `0` 表示不限制 |

**请求示例**

```bash
curl "http://localhost:8086/api/doc-template/history?limit=10"
```

**响应字段（data[]，`GenerationHistoryRespVO`）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | Long | 生成任务 ID |
| `title` | String | 标题（描述摘要） |
| `kb` | String | 知识库名称；未启用检索时为「默认知识库」 |
| `type` | String | 类型（固定「模板生成」） |
| `time` | String | 生成时间，格式 `yyyy-MM-dd HH:mm` |
| `status` | String | 状态文案：成功 / 部分成功 / 失败 |
| `docCount` | Integer | 产出文档数量 |

**响应示例**

```json
{
  "code": 0,
  "message": "",
  "data": [
    {
      "id": 8,
      "title": "按模板生成一节《企业级应用开发》教案",
      "kb": "默认知识库",
      "type": "模板生成",
      "time": "2026-10-18 10:12",
      "status": "成功",
      "docCount": 1
    }
  ]
}
```

---

#### 3.3.3 生成详情

| 项 | 值 |
| --- | --- |
| 请求方法 | `GET` |
| 请求路径 | `/doc-template/detail` |
| 鉴权 | 免登录 |

**请求参数（Query）**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | Long | 是 | 生成任务 ID |

**请求示例**

```bash
curl "http://localhost:8086/api/doc-template/detail?id=8"
```

**响应字段（data）**：结构同 [3.3.1](#331-发起模板复刻生成) 的 `GenerationRespVO`

**可能错误**

| code | message |
| --- | --- |
| `1004000005` | 生成记录不存在 |

---

### 3.4 知识库空间模块

> 模块前缀 `/knowledge-base/space`，需登录。

#### 3.4.1 空间列表

| 项 | 值 |
| --- | --- |
| 请求方法 | `GET` |
| 请求路径 | `/knowledge-base/space/list` |
| 鉴权 | 需登录 |

**请求参数**：无

**请求示例**

```bash
curl http://localhost:8086/api/knowledge-base/space/list -H "Authorization: Bearer <token>"
```

**响应字段（data[]，`SpaceRespVO`）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | Long | 空间 ID |
| `name` | String | 空间名称 |
| `description` | String | 空间描述 |
| `documentCount` | Long | 空间内文档数量 |
| `createTime` | LocalDateTime | 创建时间 |

**响应示例**

```json
{
  "code": 0,
  "message": "",
  "data": [
    {
      "id": 3,
      "name": "课程资料库",
      "description": "企业级应用开发课程相关文档",
      "documentCount": 6,
      "createTime": "2026-10-10 09:00:00"
    }
  ]
}
```

---

#### 3.4.2 新建空间

| 项 | 值 |
| --- | --- |
| 请求方法 | `POST` |
| 请求路径 | `/knowledge-base/space/create` |
| 鉴权 | 需登录 |
| Content-Type | `application/json` |

**请求参数（Body，`SpaceSaveReqVO`）**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `name` | String | 是 | 空间名称 |
| `description` | String | 否 | 空间描述 |

> `id` 字段为修改时使用，新建时无需传。

**请求示例**

```bash
curl -X POST http://localhost:8086/api/knowledge-base/space/create \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"name":"课程资料库","description":"企业级应用开发课程相关文档"}'
```

**响应字段（data）**：`Long`，新建空间 ID

```json
{ "code": 0, "message": "", "data": 3 }
```

---

#### 3.4.3 更新空间

| 项 | 值 |
| --- | --- |
| 请求方法 | `PUT` |
| 请求路径 | `/knowledge-base/space/update` |
| 鉴权 | 需登录 |
| Content-Type | `application/json` |

**请求参数（Body，`SpaceSaveReqVO`）**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | Long | 是 | 待更新空间 ID |
| `name` | String | 是 | 空间名称 |
| `description` | String | 否 | 空间描述 |

**请求示例**

```bash
curl -X PUT http://localhost:8086/api/knowledge-base/space/update \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"id":3,"name":"课程资料库（已更新）","description":"补充了 2026 秋季学期资料"}'
```

**响应字段（data，`SpaceRespVO`）**：同 [3.4.1](#341-空间列表) 的列表元素结构

**可能错误**

| code | message |
| --- | --- |
| `1003000000` | 知识库空间不存在 |

---

#### 3.4.4 删除空间

| 项 | 值 |
| --- | --- |
| 请求方法 | `DELETE` |
| 请求路径 | `/knowledge-base/space/delete` |
| 鉴权 | 需登录 |

**请求参数（Query）**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | Long | 是 | 待删除空间 ID |

**请求示例**

```bash
curl -X DELETE "http://localhost:8086/api/knowledge-base/space/delete?id=3" \
  -H "Authorization: Bearer <token>"
```

**响应字段（data）**：`Boolean`，成功恒为 `true`

```json
{ "code": 0, "message": "", "data": true }
```

---

### 3.5 知识库文档模块

> 模块前缀 `/knowledge-base/document`，需登录。
> 文档处理采用「异步任务」四步模型：
> **① `POST /upload` 上传并提交解析 → ② `GET /parse-status` 轮询解析状态（完成后返回分片预览）→ ③ `POST /vectorize` 确认分片后向量化 → ④ `POST /abandon` 放弃**。

#### 3.5.1 文档列表

| 项 | 值 |
| --- | --- |
| 请求方法 | `GET` |
| 请求路径 | `/knowledge-base/document/list` |
| 鉴权 | 需登录 |

**请求参数（Query）**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `spaceId` | Long | 是 | 所属空间 ID |

**请求示例**

```bash
curl "http://localhost:8086/api/knowledge-base/document/list?spaceId=3" \
  -H "Authorization: Bearer <token>"
```

**响应字段（data[]，`DocumentRespVO`）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | Long | 文档 ID |
| `spaceId` | Long | 所属空间 ID |
| `name` | String | 文档名称 |
| `type` | String | 文档类型：`PDF` / `WORD` / `MARKDOWN` |
| `status` | Integer | 处理状态：`0` 待处理 / `1` 解析中 / `2` 已完成（已向量化）/ `3` 失败 / `4` 待确认（已解析未向量化） |
| `parseProgress` | Integer | 解析进度 `0-100` |
| `parseError` | String | 解析失败原因 |
| `createTime` | LocalDateTime | 创建时间 |

**响应示例**

```json
{
  "code": 0,
  "message": "",
  "data": [
    {
      "id": 21,
      "spaceId": 3,
      "name": "课程大纲.pdf",
      "type": "PDF",
      "status": 2,
      "parseProgress": 100,
      "parseError": null,
      "createTime": "2026-10-11 14:05:00"
    },
    {
      "id": 22,
      "spaceId": 3,
      "name": "实验指导.docx",
      "type": "WORD",
      "status": 4,
      "parseProgress": 100,
      "parseError": null,
      "createTime": "2026-10-12 10:30:00"
    }
  ]
}
```

---

#### 3.5.2 新建文档记录

| 项 | 值 |
| --- | --- |
| 请求方法 | `POST` |
| 请求路径 | `/knowledge-base/document/create` |
| 鉴权 | 需登录 |
| Content-Type | `application/json` |

**请求参数（Body，`DocumentSaveReqVO`）**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `spaceId` | Long | 是 | 所属空间 ID |
| `name` | String | 是 | 文档名称 |
| `type` | String | 是 | 文档类型：`PDF` / `WORD` / `MARKDOWN` |

**请求示例**

```bash
curl -X POST http://localhost:8086/api/knowledge-base/document/create \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"spaceId":3,"name":"实验指导.docx","type":"WORD"}'
```

**响应字段（data）**：`Long`，新建文档 ID

```json
{ "code": 0, "message": "", "data": 22 }
```

**可能错误**

| code | message |
| --- | --- |
| `1003000000` | 知识库空间不存在 |

---

#### 3.5.3 上传文件并提交解析

| 项 | 值 |
| --- | --- |
| 请求方法 | `POST` |
| 请求路径 | `/knowledge-base/document/upload` |
| 鉴权 | 需登录 |
| Content-Type | `multipart/form-data` |
| 超时建议 | 前端建议取消默认超时（大文件较慢） |

**请求参数（form-data）**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `files` | File[] | 是 | 文件列表，支持多文件；单文件上限 `50MB` |
| `spaceId` | Long | 是 | 所属空间 ID |

> 本接口**不做扩展名白名单**，按文件名后缀直接落库与提交解析引擎；空文件、超限文件会被拒绝。

**请求示例**

```bash
curl -X POST http://localhost:8086/api/knowledge-base/document/upload \
  -H "Authorization: Bearer <token>" \
  -F "files=@D:/files/课程大纲.pdf" \
  -F "files=@D:/files/实验指导.docx" \
  -F "spaceId=3"
```

**响应字段（data[]，每个元素对应一个文件）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `documentId` | Long | 文档 ID，后续轮询解析状态使用 |
| `fileName` | String | 原始文件名 |
| `fileSize` | Long | 文件大小（字节） |
| `type` | String | 文件扩展名（小写，如 `pdf`、`docx`） |
| `status` | Integer | 文档状态，提交成功固定为 `1`（解析中） |

**响应示例**

```json
{
  "code": 0,
  "message": "",
  "data": [
    { "documentId": 21, "fileName": "课程大纲.pdf", "fileSize": 512000, "type": "pdf", "status": 1 },
    { "documentId": 22, "fileName": "实验指导.docx", "fileSize": 204800, "type": "docx", "status": 1 }
  ]
}
```

**可能错误**

| code | message |
| --- | --- |
| `500` | 上传文件不能为空 |
| `500` | 文件大小超过上限（50MB） |
| `1003000000` | 知识库空间不存在 |
| `500` | 提交解析任务失败：{原因} |

---

#### 3.5.4 轮询解析状态 / 获取分片预览

| 项 | 值 |
| --- | --- |
| 请求方法 | `GET` |
| 请求路径 | `/knowledge-base/document/parse-status` |
| 鉴权 | 需登录 |
| 轮询建议 | 解析中建议每 `2~3` 秒轮询一次，直到 `status` 为 `2` / `3` / `4` |

**请求参数（Query）**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `documentId` | Long | 是 | 文档 ID |

**请求示例**

```bash
curl "http://localhost:8086/api/knowledge-base/document/parse-status?documentId=22" \
  -H "Authorization: Bearer <token>"
```

**响应字段（data）**：`Map<String, Object>`，字段随解析进度动态变化

**① 解析中 / 失败时**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `documentId` | Long | 文档 ID |
| `fileName` | String | 文档名称 |
| `type` | String | 文档类型 |
| `status` | Integer | 文档状态：`0` / `1` / `3` |
| `progress` | Integer | 解析进度 `0-100` |
| `error` | String | 失败原因（仅失败或查询异常时存在） |

```json
{
  "code": 0,
  "message": "",
  "data": {
    "documentId": 22,
    "fileName": "实验指导.docx",
    "type": "WORD",
    "status": 1,
    "progress": 65
  }
}
```

**② 解析完成时**（在①基础上追加）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `totalChunks` | Integer | 分片总数 |
| `chunks` | Array | 分片预览列表 |
| `chunks[].index` | Integer | 分片序号（从 0 开始） |
| `chunks[].content` | String | 分片正文 |

```json
{
  "code": 0,
  "message": "",
  "data": {
    "documentId": 22,
    "fileName": "实验指导.docx",
    "type": "WORD",
    "status": 4,
    "progress": 100,
    "totalChunks": 2,
    "chunks": [
      { "index": 0, "content": "实验一：搭建 Spring Boot 开发环境……" },
      { "index": 1, "content": "实验二：编写第一个 REST 接口……" }
    ]
  }
}
```

> `status = 4`（待确认）表示分片已落库、等待调用 `/vectorize`；`status = 2` 表示已完成向量化，此时返回的是库中已确认的分片。
> 解析成功后的落块与 AI 语义分片在同一请求内完成（服务端有重入保护），因此首次进入 `4` 前可能多次返回 `1`。

**可能错误**

| code | message |
| --- | --- |
| `1003000001` | 知识库文档不存在 |

---

#### 3.5.5 确认分片并向量化

| 项 | 值 |
| --- | --- |
| 请求方法 | `POST` |
| 请求路径 | `/knowledge-base/document/vectorize` |
| 鉴权 | 需登录 |
| Content-Type | `multipart/form-data` |

**请求参数（form-data）**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `documentId` | Long | 是 | 文档 ID |
| `spaceId` | Long | 是 | 所属空间 ID |
| `chunks` | String | 是 | 教师校对后的分片列表，**JSON 字符串**，元素含 `index`、`content` |

**请求示例**

```bash
curl -X POST http://localhost:8086/api/knowledge-base/document/vectorize \
  -H "Authorization: Bearer <token>" \
  -F "documentId=22" \
  -F "spaceId=3" \
  -F 'chunks=[{"index":0,"content":"实验一：搭建 Spring Boot 开发环境……"},{"index":1,"content":"实验二：编写第一个 REST 接口……"}]'
```

**响应字段（data）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `documentId` | Long | 文档 ID |
| `savedChunks` | Integer | 实际入库并向量化的分片数 |
| `status` | String | 固定为 `"completed"` |

**响应示例**

```json
{ "code": 0, "message": "", "data": { "documentId": 22, "savedChunks": 2, "status": "completed" } }
```

**可能错误**

| code | message |
| --- | --- |
| `1003000000` | 知识库空间不存在 |
| `1003000001` | 知识库文档不存在 |
| `1003000002` | 知识库文档已完成向量化，不可重复提交 |

---

#### 3.5.6 删除文档

| 项 | 值 |
| --- | --- |
| 请求方法 | `DELETE` |
| 请求路径 | `/knowledge-base/document/delete` |
| 鉴权 | 需登录 |

**请求参数（Query）**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | Long | 是 | 文档 ID |

**请求示例**

```bash
curl -X DELETE "http://localhost:8086/api/knowledge-base/document/delete?id=22" \
  -H "Authorization: Bearer <token>"
```

**响应字段（data）**：`Boolean`，成功恒为 `true`

```json
{ "code": 0, "message": "", "data": true }
```

**可能错误**

| code | message |
| --- | --- |
| `1003000001` | 知识库文档不存在 |

---

#### 3.5.7 放弃未向量化的文档

| 项 | 值 |
| --- | --- |
| 请求方法 | `POST` |
| 请求路径 | `/knowledge-base/document/abandon` |
| 鉴权 | 需登录 |

> 对未确认分片的文档执行**逻辑删除**，删除后不再出现在列表。
> 对已完成向量化的文档调用本接口**不会报错**，服务端直接忽略（视为最终入库结果，不执行删除）。

**请求参数（Query）**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `documentId` | Long | 是 | 文档 ID |

**请求示例**

```bash
curl -X POST "http://localhost:8086/api/knowledge-base/document/abandon?documentId=22" \
  -H "Authorization: Bearer <token>"
```

**响应字段（data）**：`Boolean`，成功恒为 `true`

```json
{ "code": 0, "message": "", "data": true }
```

**可能错误**

| code | message |
| --- | --- |
| `1003000001` | 知识库文档不存在 |

---

### 3.6 知识库文档分片模块

#### 3.6.1 文档分片列表

| 项 | 值 |
| --- | --- |
| 请求方法 | `GET` |
| 请求路径 | `/knowledge-base/chunk/list` |
| 鉴权 | 需登录 |

**请求参数（Query）**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `documentId` | Long | 是 | 文档 ID |

**请求示例**

```bash
curl "http://localhost:8086/api/knowledge-base/chunk/list?documentId=22" \
  -H "Authorization: Bearer <token>"
```

**响应字段（data[]，`ChunkRespVO`）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | Long | 分片 ID |
| `documentId` | Long | 所属文档 ID |
| `index` | Integer | 分片序号（从 0 开始） |
| `content` | String | 分片正文 |

**响应示例**

```json
{
  "code": 0,
  "message": "",
  "data": [
    { "id": 501, "documentId": 22, "index": 0, "content": "实验一：搭建 Spring Boot 开发环境……" },
    { "id": 502, "documentId": 22, "index": 1, "content": "实验二：编写第一个 REST 接口……" }
  ]
}
```

---

### 3.7 工作流定义模块

> 模块前缀 `/workflow`，整体免登录。
> **执行入口已迁移到 `POST /chat`**：原先的 `/workflow/run`、`/workflow/resume`、`/workflow/get_process` 已移除，本模块只负责定义的保存与查询。
> 画布定义整体存放在 `workflow` 表的 `schema_json` 中，保存即覆盖，**不做版本管理**。

#### 3.7.1 联调执行（非业务接口）

| 项 | 值 |
| --- | --- |
| 请求方法 | `GET` |
| 请求路径 | `/workflow/text` |
| 鉴权 | 免登录 |
| 响应类型 | 无响应体（`void`，HTTP 200） |

> 同步执行 classpath 内置定义（`text.json`）一次，并把终态状态键打到服务端日志。**不是业务接口**，仅供本地验证画布定义是否能跑通；失败抛 `GraphStateException`（HTTP 500）。

**请求参数**：无

```bash
curl http://localhost:8086/api/workflow/text
```

---

#### 3.7.2 保存工作流定义

| 项 | 值 |
| --- | --- |
| 请求方法 | `POST` |
| 请求路径 | `/workflow/save` |
| 鉴权 | 免登录 |
| Content-Type | `application/json` |

> `id` 为空表示新建，非空表示更新已有工作流；保存即覆盖当前记录。

**请求参数（Body，`WorkflowSaveReqVO`）**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | String | 否 | 工作流 ID（雪花 ID 字符串）；空 = 新建，非空 = 更新 |
| `name` | String | 是 | 工作流名称（≤100 字符） |
| `description` | String | 否 | 工作流说明（≤500 字符） |
| `schemaJson` | String | 是 | 画布结构（JSON 字符串，顶层含 `nodes` / `edges`） |

> 新建记录的 `status` 固定为 `0`（草稿）；本接口**不接受** `status` 入参。

**请求示例**

```bash
curl -X POST http://localhost:8086/api/workflow/save \
  -H "Content-Type: application/json" \
  -d '{
    "name": "作业批改工作流",
    "description": "教师确认后逐份批改",
    "schemaJson": "{\"nodes\":[],\"edges\":[]}"
  }'
```

**响应字段（data）**：`String`，工作流 ID（雪花 ID 以字符串返回，避免 JS 精度丢失）

```json
{ "code": 0, "message": "", "data": "1858888000012345678" }
```

**可能错误**（均为 `code = 400`，`message` 如下）

| message |
| --- |
| 工作流名称不能为空 |
| 工作流名称不能超过 100 个字符 |
| 工作流说明不能超过 500 个字符 |
| 画布定义不能为空 |
| 画布定义不是合法的 JSON: {原因} |
| 工作流 id 非法: {id} |
| 工作流不存在: {id} |

---

#### 3.7.3 工作流详情

| 项 | 值 |
| --- | --- |
| 请求方法 | `GET` |
| 请求路径 | `/workflow/detail` |
| 鉴权 | 免登录 |

**请求参数（Query）**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `workflow_id` | String | 是 | 工作流 ID（注意参数名为下划线形式） |

**请求示例**

```bash
curl "http://localhost:8086/api/workflow/detail?workflow_id=1858888000012345678"
```

**响应字段（data，`WorkflowDetailRespVO`）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | String | 工作流 ID |
| `name` | String | 工作流名称 |
| `description` | String | 工作流说明 |
| `status` | Integer | 状态：`0` 草稿 / `1` 已发布 |
| `builtin` | Boolean | 是否为内置模板（固定 ID `1000000000000000001`） |
| `stepCount` | Integer | 顶层节点数（内嵌 `blocks` 不计入） |
| `schemaJson` | String | 画布结构（JSON 字符串，需前端 `JSON.parse`） |
| `updateTime` | String | 更新时间，格式 `yyyy-MM-dd HH:mm:ss` |

**响应示例**

```json
{
  "code": 0,
  "message": "",
  "data": {
    "id": "1858888000012345678",
    "name": "作业批改工作流",
    "description": "教师确认后逐份批改",
    "status": 0,
    "builtin": false,
    "stepCount": 5,
    "schemaJson": "{\"nodes\":[],\"edges\":[]}",
    "updateTime": "2026-10-18 09:30:00"
  }
}
```

**可能错误**

| code | message |
| --- | --- |
| `400` | 工作流 id 不能为空 |
| `400` | 工作流不存在: {id} |

---

#### 3.7.4 工作流列表

| 项 | 值 |
| --- | --- |
| 请求方法 | `GET` |
| 请求路径 | `/workflow/list` |
| 鉴权 | 免登录 |

> 按更新时间倒序；列表**不含画布结构**（`schemaJson`）。

**请求参数**：无

```bash
curl http://localhost:8086/api/workflow/list
```

**响应字段（data[]，`WorkflowVO`）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | String | 工作流 ID |
| `name` | String | 工作流名称 |
| `description` | String | 工作流说明 |
| `status` | Integer | 状态：`0` 草稿 / `1` 已发布 |
| `builtin` | Boolean | 是否为内置模板 |
| `stepCount` | Integer | 顶层节点数 |
| `updateTime` | String | 更新时间，格式 `yyyy-MM-dd HH:mm:ss` |

**响应示例**

```json
{
  "code": 0,
  "message": "",
  "data": [
    {
      "id": "1000000000000000001",
      "name": "标准批改作业",
      "description": "内置模板",
      "status": 1,
      "builtin": true,
      "stepCount": 6,
      "updateTime": "2026-10-14 15:20:00"
    }
  ]
}
```

---

### 3.8 工作流对话执行模块

> 模块前缀 `/chat`，**需登录**。
> **执行模型**：`POST /chat` 是唯一执行入口，`text/event-stream` 推送事件；首轮执行与「人工确认后续跑」共用同一端点，由服务端按「该会话最新一条执行记录的状态」分流，客户端**不传状态**。
> **会话**：执行前必须先调 `POST /chat/conversation` 建立会话并绑定一个**已落库**的工作流；画布定义不内联提交。
> **续跑限制**：中断点与图检查点保存在内存中，进程重启后该会话不可续跑（需重新发起）。

#### 3.8.1 新建会话

| 项 | 值 |
| --- | --- |
| 请求方法 | `POST` |
| 请求路径 | `/chat/conversation` |
| 鉴权 | 需登录 |
| Content-Type | `application/json` |

**请求参数（Body，`ConversationCreateReqVO`）**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `workflowId` | String | 是 | 工作流 ID（必须已落库） |
| `title` | String | 否 | 会话标题；缺省取工作流名称（截到 200 字符） |

**请求示例**

```bash
curl -X POST http://localhost:8086/api/chat/conversation \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"workflowId":"1858888000012345678","title":"第 3 次作业批改"}'
```

**响应字段（data，`ConversationVO`）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `conversationId` | String | 会话编号（UUID），后续 `/chat` 全程回传 |
| `workflowId` | String | 会话绑定的工作流 ID |
| `workflowName` | String | 流程名称（冗余，列表直接展示） |
| `title` | String | 会话标题 |
| `createTime` | String | 创建时间 `yyyy-MM-dd HH:mm:ss` |
| `updateTime` | String | 最近活跃时间（每次新建执行时刷新） |

**响应示例**

```json
{
  "code": 0,
  "message": "",
  "data": {
    "conversationId": "0f2a8f0e-8f4a-4c1e-9a1b-6d0b2c7e5a11",
    "workflowId": "1858888000012345678",
    "workflowName": "作业批改工作流",
    "title": "第 3 次作业批改",
    "createTime": "2026-10-18 09:35:00",
    "updateTime": "2026-10-18 09:35:00"
  }
}
```

**可能错误**（`code = 400`）

| message |
| --- |
| 工作流 id 不能为空 |
| 工作流不存在: {workflowId} |

---

#### 3.8.2 会话列表

| 项 | 值 |
| --- | --- |
| 请求方法 | `GET` |
| 请求路径 | `/chat/conversation/list` |
| 鉴权 | 需登录 |

> 按最近活跃时间倒序。当前实现**未按 `creator` 过滤**，返回全部未删除会话。

**请求参数**：无

```bash
curl http://localhost:8086/api/chat/conversation/list -H "Authorization: Bearer <token>"
```

**响应字段（data[]）**：元素结构同 [3.8.1](#381-新建会话) 的 `ConversationVO`

---

#### 3.8.3 发起执行 / 人工确认续跑

| 项 | 值 |
| --- | --- |
| 请求方法 | `POST` |
| 请求路径 | `/chat` |
| 鉴权 | 需登录 |
| Content-Type | `application/json` |
| 响应类型 | `text/event-stream`（SSE 帧为业务数据本身，**不经** `CommonResult` 包装） |

**请求参数（Body，`ChatReqVO`）**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `conversationId` | String | 是 | 会话编号 |
| `workflowId` | String | 否 | 工作流 ID；传入时校验与会话绑定的一致 |
| `inputs` | Object | 否 | **首轮业务输入**：变量名 → 取值（如 `file_tree` / `description` / `know`）；续跑忽略；以 `__` 开头的键一律忽略 |
| `message` | Object | 续跑必填 | 本轮输入，见下方 `ChatMessageVO` |

**`message` 字段（`ChatMessageVO`）**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `type` | String | 是 | 固定 `card_submit`（表单卡片提交） |
| `nodeId` | String | 否 | 提交的是哪个中断节点的卡片（仅用于对齐校验） |
| `values` | Object | 续跑必填 | 表单字段取值：字段名 → 取值；**不能为空** |

**分流规则**

| 该会话最新执行状态 | 行为 |
| --- | --- |
| 无执行记录 | 新起一条执行 |
| `4`（中断） | 续跑，`message.values` 作为人工反馈注入 |
| `2` / `3` / `5`（终态） | 新起一条执行 |
| `0` / `1`（执行中） | 拒绝，同一会话不允许并发执行 |

**请求示例（首轮）**

```bash
curl -N -X POST http://localhost:8086/api/chat \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "conversationId": "0f2a8f0e-8f4a-4c1e-9a1b-6d0b2c7e5a11",
    "inputs": {
      "file_tree": ["张三_作业.docx", "李四_作业.docx"],
      "description": "按评分细则批改，重点关注格式规范"
    }
  }'
```

**请求示例（续跑，提交人工确认清单）**

```bash
curl -N -X POST http://localhost:8086/api/chat \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "0f2a8f0e-8f4a-4c1e-9a1b-6d0b2c7e5a11",
    "message": {
      "type": "card_submit",
      "nodeId": "100003",
      "values": { "files": ["张三_作业.docx", "李四_作业.docx"] }
    }
  }'
```

**建流前校验失败**（返回普通 `CommonResult`，`code = 400`，`Content-Type: application/json`）

| message |
| --- |
| 会话编号 conversation_id 不能为空 |
| 会话不存在: {conversationId} |
| 会话绑定的工作流与请求不一致，请重新建立会话 |
| 工作流不存在: {workflowId} |
| 该会话已有正在执行的工作流，请等待本次执行结束 |
| 该会话的人工确认已失效（服务重启后不可续跑），请重新发起执行 |
| 人工确认内容不能为空（message.values） |

**SSE 事件契约**

`event:` 名与 `data`（JSON，字段为 `snake_case`）如下表。**每帧**都带 `chat_id` / `conversation_id` / `execute_id` / `created_at`（`created_at` 为秒级时间戳；`conversation_id` 跨轮不变，`chat_id` 每轮新生成，`execute_id` 跨轮复用）。

| 事件 | 时机 | `data` 关键字段 |
| --- | --- | --- |
| `conversation.chat.created` | 每轮第一帧：请求已受理 | `workflow_id`、`status = created`、`last_error{code,msg}` |
| `conversation.chat.in_progress` | 第二帧：图开始执行 | `status = in_progress`、`last_error{code,msg}` |
| `conversation.message.completed` | 一条消息完成（节点产出 / 表单卡片 / 正文） | `role`、`type`、`content_type`、`content`，见下表 |
| `conversation.chat.requires_action` | 中断终态：等待人工确认（**不发 completed**） | `status = requires_action`、`completed_at` |
| `conversation.chat.completed` | 成功终态 | `status = completed`、`completed_at` |
| `conversation.chat.failed` | 失败终态 | `status = failed`、`completed_at`、`last_error{code,msg}` |
| `done` | 流关闭，**每轮必发、与成败无关** | `conversation_id`、`execute_id` |

> `conversation.message.delta`（逐字增量）一期**保留事件名但不发送**；判断终态只看三个终态帧，不看流是否结束。HTTP 状态码恒为 200，失败信息在 `conversation.chat.failed` 帧里。

**`conversation.message.completed` 的三种 `content`**

**① 节点产出（`type = verbose`，`content_type = verbose`）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `content.msg_type` | String | 固定 `node_finished` |
| `content.data.node` | String | 节点 id |
| `content.data.state` | Object | 该节点执行后的完整状态快照 |

```json
{
  "msg_type": "node_finished",
  "data": {
    "node": "100002",
    "state": { "100002": { "plan": ["张三_作业.docx"] } }
  }
}
```

**② 表单卡片（`type = answer`，`content_type = card`）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `content.response_type` | String | 固定 `card` |
| `content.card_type` | String | 固定 `INPUT` |
| `content.node_id` | String | 中断节点 id |
| `content.title` | String | 卡片标题（节点标题） |
| `content.data` | Array | 只读回显的上游产出（`Map` 列表） |
| `content.fields` | Array | 待用户填写的字段声明（`Map` 列表） |

```json
{
  "response_type": "card",
  "card_type": "INPUT",
  "node_id": "100003",
  "title": "人工确认",
  "data": [{ "plan": ["张三_作业.docx", "李四_作业.docx"] }],
  "fields": [{ "name": "files", "label": "确认批改清单", "type": "list" }]
}
```

**③ 正文（`type = answer`，`content_type = text`）**：`content` 为字符串。结束节点当前仍是 TODO，**一期不发**。

**事件流示例（首轮 → 中断 → 续跑 → 完成）**

```
event:conversation.chat.created
data:{"chat_id":"c1...","conversation_id":"0f2a...","execute_id":"8b31...","created_at":1760772000,"workflow_id":"1858888000012345678","status":"created","last_error":{"code":0,"msg":""}}

event:conversation.chat.in_progress
data:{"chat_id":"c1...","conversation_id":"0f2a...","execute_id":"8b31...","created_at":1760772000,"status":"in_progress","last_error":{"code":0,"msg":""}}

event:conversation.message.completed
data:{"chat_id":"c1...","conversation_id":"0f2a...","execute_id":"8b31...","created_at":1760772000,"role":"assistant","type":"verbose","content_type":"verbose","content":{"msg_type":"node_finished","data":{"node":"100002","state":{...}}}}

event:conversation.message.completed
data:{"chat_id":"c1...","conversation_id":"0f2a...","execute_id":"8b31...","created_at":1760772000,"role":"assistant","type":"answer","content_type":"card","content":{"response_type":"card","card_type":"INPUT","node_id":"100003","title":"人工确认","data":[...],"fields":[...]}}

event:conversation.chat.requires_action
data:{"chat_id":"c1...","conversation_id":"0f2a...","execute_id":"8b31...","created_at":1760772000,"status":"requires_action","completed_at":1760772012}

event:done
data:{"conversation_id":"0f2a...","execute_id":"8b31..."}

（客户端再次 POST /chat 提交 message.values，续跑）

event:conversation.chat.created
data:{...,"status":"created"}

event:conversation.message.completed
data:{...,"type":"verbose","content_type":"verbose","content":{"msg_type":"node_finished","data":{"node":"100004","state":{...}}}}

event:conversation.chat.completed
data:{"chat_id":"c2...","conversation_id":"0f2a...","execute_id":"8b31...","created_at":1760772100,"status":"completed","completed_at":1760772180}

event:done
data:{"conversation_id":"0f2a...","execute_id":"8b31..."}
```

> 与 Coze 的差异：外层不再是 `CommonResult`；事件名对齐 Coze，但档掉了冗余的 `verbose` / `finish_reason` 信号；载荷字段统一 `snake_case`，REST 请求体仍是 `camelCase`。

---

### 3.9 作业批改模块

> 模块前缀 `/homework`，整体免登录（便于未登录态联调）。
> 两步式：`POST /upload` 批量上传作业文件夹 → `POST /grade` 提交批改配置。

#### 3.9.1 作业文件夹批量上传

| 项 | 值 |
| --- | --- |
| 请求方法 | `POST` |
| 请求路径 | `/homework/upload` |
| 鉴权 | 免登录 |
| Content-Type | `multipart/form-data` |

**请求参数（form-data）**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `files` | File[] | 是 | 作业文件列表，**最多 50 份**，单文件 ≤ `50MB` |
| `paths` | String[] | 是 | 与 `files` **按顺序一一对应**的相对路径，如 `电子技术基础-第3次作业/20230101-张三/第三章习题.docx` |
| `folderName` | String | 否 | 作业文件夹显示名；为空时取首个路径的根目录，仍为空则兜底「本次作业」 |

**路径规则**：反斜杠统一转 `/`，去掉前导 `/` 与首尾空白；拒绝空路径、空片段、`.` / `..` 片段与重复路径。

**允许的扩展名**：`pdf`、`doc`、`docx`、`md`、`markdown`、`txt`、`ppt`、`pptx`、`xls`、`xlsx`、`png`、`jpg`、`jpeg`、`gif`、`webp`

> 与 `/infra/file/upload` 的差别：本接口**允许 0 字节文件**（空占位文件是常见情况），且错误信息会带上出错的相对路径。

**请求示例**

```bash
curl -X POST http://localhost:8086/api/homework/upload \
  -F "files=@D:/作业/张三/第三章习题.docx" \
  -F "files=@D:/作业/李四/第三章习题.docx" \
  -F "paths=电子技术基础-第3次作业/张三/第三章习题.docx" \
  -F "paths=电子技术基础-第3次作业/李四/第三章习题.docx" \
  -F "folderName=电子技术基础-第3次作业"
```

**响应字段（data，`HomeworkUploadRespVO`）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `taskId` | Long | 批改任务 ID（`homework_task.id`），第二步回传 |
| `folderName` | String | 作业文件夹名 |
| `fileCount` | Integer | 作业份数 |
| `totalSize` | Long | 文件总大小（字节） |
| `paths` | String[] | 文件相对路径清单 |
| `files` | Array | 每份文件明细，见下方 `HomeworkFileItemVO` |

**`files[]` 元素字段（`HomeworkFileItemVO`）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | Long | 作业文件 ID（`homework_file.id`） |
| `fileId` | Long | 关联文件 ID（`infra_file.id`），批改取文件以此为准 |
| `name` | String | 文件基础名（含扩展名） |
| `path` | String | 相对路径 |
| `size` | Long | 文件大小（字节） |
| `type` | String | 文件 MIME 类型 |
| `url` | String | 访问地址（MinIO 预签名 URL，7 天有效，仅作辅助） |

**响应示例**

```json
{
  "code": 0,
  "message": "",
  "data": {
    "taskId": 15,
    "folderName": "电子技术基础-第3次作业",
    "fileCount": 2,
    "totalSize": 409600,
    "paths": [
      "电子技术基础-第3次作业/张三/第三章习题.docx",
      "电子技术基础-第3次作业/李四/第三章习题.docx"
    ],
    "files": [
      {
        "id": 31,
        "fileId": 98,
        "name": "第三章习题.docx",
        "path": "电子技术基础-第3次作业/张三/第三章习题.docx",
        "size": 204800,
        "type": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "url": "http://localhost:9000/test-bucket/infra/file/xxx.docx?X-Amz-Expires=604800&..."
      }
    ]
  }
}
```

**可能错误**

| code | message |
| --- | --- |
| `1004000000` | 上传文件不能为空 |
| `1004000001` | 文件与相对路径数量不一致 |
| `1004000002` | 文件相对路径不合法 |
| `1004000003` | 单次批改最多 50 份作业 |
| `1004000004` | 文件大小超过上限（50MB）：{相对路径} |
| `1004000005` | 不支持的文件类型：{相对路径} |

> 号段提示：以上 `1_004_000_xxx` 与 `doctemplate` 模块**同号**，详见 [4.1](#41-错误码总表)。

---

#### 3.9.2 提交批改请求

| 项 | 值 |
| --- | --- |
| 请求方法 | `POST` |
| 请求路径 | `/homework/grade` |
| 鉴权 | 免登录 |
| Content-Type | `application/json` |

> **当前实现只接收并记录请求内容**（写日志），真实批改链路（知识库检索 + 工作流编排 + 大模型评分）待接入；接口契约已定，前端可在上传成功后串行调用。

**请求参数（Body，`HomeworkGradeReqVO`）**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `taskId` | Long | 是 | 第一步上传返回的 `taskId` |
| `spaceIds` | String[] | 否 | 选中的知识库 ID 列表（未选择时为空数组） |
| `files` | Array | 是 | 作业文件明细，顺序与上传返回的 `files` 一致，元素见 `HomeworkFileItemVO` |
| `description` | String | 否 | AI 描述说明（批改要求） |
| `exportFormat` | String | 否 | 导出结果格式：`xlsx` / `csv` |
| `workflowId` | String | 否 | 选择的工作流 ID |

**请求示例**

```bash
curl -X POST http://localhost:8086/api/homework/grade \
  -H "Content-Type: application/json" \
  -d '{
    "taskId": 15,
    "spaceIds": ["default", "2", "1"],
    "files": [
      { "id": 31, "fileId": 98, "name": "第三章习题.docx", "path": "电子技术基础-第3次作业/张三/第三章习题.docx", "size": 204800, "type": "application/vnd.openxmlformats-officedocument.wordprocessingml.document" }
    ],
    "description": "按评分细则批改，重点关注格式规范",
    "exportFormat": "xlsx",
    "workflowId": "1000000000000000001"
  }'
```

**响应字段（data）**：`Boolean`，成功恒为 `true`

```json
{ "code": 0, "message": "", "data": true }
```

> 批改取文件务必以 `fileId` 为准：`fileId` → `infra_file`（bucket + path）→ MinIO 字节流。预签名 URL 7 天过期，只作打开原文件用。

---

### 3.10 模拟会议模块

> 模块前缀 `/meeting`，整体免登录。会议内身份**不依赖登录态**：
> 老师用 `hostToken`（建会时**只返回一次**），学生用 `attendeeToken`（入会时签发）。
> **实时媒体（屏幕共享）与聊天广播走 LiveKit**（信令 `7880` / 媒体 `7881`），后端只签发令牌、删房间，不参与实时转发。
> **在线人数不在任何接口里**：唯一真相是 LiveKit 房间的参与者事件；接口返回的 `attendeeCount` 与 `attendees` 是**累计入会台账**。

#### 3.10.1 创建会议

| 项 | 值 |
| --- | --- |
| 请求方法 | `POST` |
| 请求路径 | `/meeting/create` |
| 鉴权 | 免登录 |
| Content-Type | `application/json` |

**请求参数（Body，`MeetingCreateReqVO`）**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `title` | String | 是 | 会议标题（≤100 字符） |

**请求示例**

```bash
curl -X POST http://localhost:8086/api/meeting/create \
  -H "Content-Type: application/json" \
  -d '{"title":"企业级应用开发 第 5 次课"}'
```

**响应字段（data，`MeetingCreateRespVO`）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `meetingId` | Long | 会议主键 |
| `meetingNo` | String | 6 位会议号，同时是入会链接路径段 |
| `joinUrl` | String | 学生入会链接（`public-base-url` + `/meeting/{meetingNo}`） |
| `hostToken` | String | 主持人凭据，**仅创建时返回一次** |

**响应示例**

```json
{
  "code": 0,
  "message": "",
  "data": {
    "meetingId": 108,
    "meetingNo": "482913",
    "joinUrl": "http://192.168.1.5:5173/meeting/482913",
    "hostToken": "3f7c1d9e8a4b4c2f9e6d5a1b0c8e7f42"
  }
}
```

**可能错误**

| code | message |
| --- | --- |
| `1007000000` | 请填写会议标题 |
| `1007000008` | 会议标题长度不能超过 100 个字符 |
| `1007000007` | 会议号生成失败，请重试 |

---

#### 3.10.2 学生入会

| 项 | 值 |
| --- | --- |
| 请求方法 | `POST` |
| 请求路径 | `/meeting/join` |
| 鉴权 | 免登录 |
| Content-Type | `application/json` |

**请求参数（Body，`MeetingJoinReqVO`）**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `meetingNo` | String | 是 | 6 位会议号 |
| `nickname` | String | 是 | 参会昵称（≤20 字符；同会议内重名自动追加 `(2)`、`(3)`…） |

**请求示例**

```bash
curl -X POST http://localhost:8086/api/meeting/join \
  -H "Content-Type: application/json" \
  -d '{"meetingNo":"482913","nickname":"张三"}'
```

**响应字段（data，`MeetingJoinRespVO`）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `attendeeId` | Long | 参会人 ID |
| `attendeeToken` | String | 参会凭据（`/meeting/state`、`/meeting/leave`、`/meeting/message` 使用） |
| `meeting` | Object | 会议信息，见 `MeetingInfoVO` |
| `attendees` | Array | 累计入会台账，见 `MeetingAttendeeVO` |
| `messages` | Array | 最近历史消息（上限 50 条），见 `MeetingMessageVO` |
| `realtime` | Object | LiveKit 接入信息 `{ livekitUrl, livekitToken }`；学生令牌**只能订阅** |

**响应示例**

```json
{
  "code": 0,
  "message": "",
  "data": {
    "attendeeId": 512,
    "attendeeToken": "9b2c4d6e8f0a1b3c5d7e9f0a2b4c6d8e",
    "meeting": {
      "id": 108,
      "meetingNo": "482913",
      "title": "企业级应用开发 第 5 次课",
      "status": 1,
      "startTime": "2026-10-18 14:00:00",
      "endTime": null,
      "attendeeCount": 1
    },
    "attendees": [
      { "id": 512, "nickname": "张三", "role": 2, "joinTime": "2026-10-18 14:02:10", "leaveTime": null }
    ],
    "messages": [],
    "realtime": {
      "livekitUrl": "ws://192.168.1.5:7880",
      "livekitToken": "eyJhbGciOiJIUzI1NiIs..."
    }
  }
}
```

**可能错误**

| code | message |
| --- | --- |
| `1007000001` | 会议不存在 |
| `1007000002` | 会议已结束 |
| `1007000003` | 请输入你的名字 |
| `1007000004` | 名字长度不能超过 20 个字符 |
| `1007000010` | 实时通道未配置：请先启动 LiveKit（…）并配置 LIVEKIT_API_KEY / LIVEKIT_API_SECRET |

---

#### 3.10.3 会议详情（老师控制台）

| 项 | 值 |
| --- | --- |
| 请求方法 | `GET` |
| 请求路径 | `/meeting/detail` |
| 鉴权 | 免登录（凭 `hostToken`） |

**请求参数（Query）**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `meetingNo` | String | 是 | 会议号 |
| `hostToken` | String | 是 | 主持人凭据 |

**请求示例**

```bash
curl "http://localhost:8086/api/meeting/detail?meetingNo=482913&hostToken=<hostToken>"
```

**响应字段（data，`MeetingDetailRespVO`）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `meeting` | Object | 会议信息 `MeetingInfoVO` |
| `attendees` | Array | 累计入会台账 |
| `messages` | Array | 最近历史消息，按时间正序 |
| `realtime` | Object | LiveKit 接入信息；老师令牌**可发布**（能共享屏幕）；LiveKit 未配置时为 `null` |

> 会议已结束时本接口仍可查询历史；此时 `meeting.status = 2`。

**可能错误**

| code | message |
| --- | --- |
| `1007000001` | 会议不存在 |
| `1007000006` | 主持人凭据无效 |

---

#### 3.10.4 学生侧会议状态

| 项 | 值 |
| --- | --- |
| 请求方法 | `GET` |
| 请求路径 | `/meeting/state` |
| 鉴权 | 免登录（凭 `attendeeToken`） |

> 刷新页面或断线重连后调用：补齐成员与消息，并**重新签发** LiveKit 只读令牌。

**请求参数（Query）**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `meetingNo` | String | 是 | 会议号 |
| `attendeeToken` | String | 是 | 参会凭据 |

**请求示例**

```bash
curl "http://localhost:8086/api/meeting/state?meetingNo=482913&attendeeToken=<attendeeToken>"
```

**响应字段（data）**：结构同 [3.10.3](#3103-会议详情老师控制台) 的 `MeetingDetailRespVO`（`realtime` 为学生只读令牌）

**可能错误**

| code | message |
| --- | --- |
| `1007000001` | 会议不存在 |
| `1007000009` | 参会凭据无效，请重新入会 |

---

#### 3.10.5 聊天消息落库

| 项 | 值 |
| --- | --- |
| 请求方法 | `POST` |
| 请求路径 | `/meeting/message` |
| 鉴权 | 免登录 |
| Content-Type | `application/json` |

> 实时聊天走 LiveKit 的 DataChannel（不经后端），本接口**只把消息写进历史**。调用方应在发送 DataChannel 之后串行调用；失败只影响历史，不影响实时。

**请求参数（Body，`MeetingMessageReqVO`）**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `meetingNo` | String | 是 | 会议号 |
| `attendeeToken` | String | 学生发送时必填 | 学生参会凭据 |
| `hostToken` | String | 老师发送时必填 | 主持人凭据（老师昵称固定「老师」） |
| `content` | String | 是 | 消息正文（≤500 字符） |

**请求示例**

```bash
curl -X POST http://localhost:8086/api/meeting/message \
  -H "Content-Type: application/json" \
  -d '{"meetingNo":"482913","attendeeToken":"<attendeeToken>","content":"老师，这里没听懂"}'
```

**响应字段（data，`MeetingMessageVO`）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | Long | 消息 ID |
| `attendeeId` | Long | 发送人 ID（老师发言为 `null`） |
| `nickname` | String | 发送人昵称 |
| `role` | Integer | `1` 老师 / `2` 学生 / `3` 数字人 |
| `content` | String | 消息正文 |
| `kind` | Integer | `1` 文字 / `2` 数字人播报 |
| `createTime` | LocalDateTime | 创建时间 |

**可能错误**

| code | message |
| --- | --- |
| `1007000001` | 会议不存在 |
| `1007000002` | 会议已结束 |
| `1007000011` | 消息内容不能为空 |
| `1007000012` | 消息长度不能超过 500 个字符 |
| `1007000013` | 发送者凭据无效，请重新入会 |

---

#### 3.10.6 学生离会

| 项 | 值 |
| --- | --- |
| 请求方法 | `POST` |
| 请求路径 | `/meeting/leave` |
| 鉴权 | 免登录 |
| Content-Type | `application/json` |

> 由学生端在**离开会议室路由**时用 `navigator.sendBeacon` 触发；只是给台账补 `leaveTime`，**幂等**（只更新 `leaveTime IS NULL` 的行）。刷新页面**不会**调用——刷新后凭据继续复用、不会重新 join。
> 本接口**不维护「在线」状态**：在线与否只看 LiveKit 房间事件。

**请求参数（Body，`MeetingLeaveReqVO`）**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `meetingNo` | String | 是 | 会议号 |
| `attendeeToken` | String | 是 | 参会凭据 |

**请求示例**

```bash
curl -X POST http://localhost:8086/api/meeting/leave \
  -H "Content-Type: application/json" \
  -d '{"meetingNo":"482913","attendeeToken":"<attendeeToken>"}'
```

**响应字段（data）**：`Boolean`，成功恒为 `true`

**可能错误**

| code | message |
| --- | --- |
| `1007000001` | 会议不存在 |
| `1007000002` | 会议已结束 |
| `1007000009` | 参会凭据无效，请重新入会 |

---

#### 3.10.7 结束会议

| 项 | 值 |
| --- | --- |
| 请求方法 | `POST` |
| 请求路径 | `/meeting/close` |
| 鉴权 | 免登录 |
| Content-Type | `application/json` |

> 老师主动动作：把会议置为已结束、给台账全部补离会时间，并**删除 LiveKit 房间**（房间内学生立即收到 `Disconnected`）。删房间是 best-effort，失败只记日志（学生端还有 `/meeting/state` 轮询兜底）。

**请求参数（Body，`MeetingCloseReqVO`）**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `meetingNo` | String | 是 | 会议号 |
| `hostToken` | String | 是 | 主持人凭据 |

**请求示例**

```bash
curl -X POST http://localhost:8086/api/meeting/close \
  -H "Content-Type: application/json" \
  -d '{"meetingNo":"482913","hostToken":"<hostToken>"}'
```

**响应字段（data）**：`Boolean`，成功恒为 `true`

**可能错误**

| code | message |
| --- | --- |
| `1007000001` | 会议不存在 |
| `1007000002` | 会议已结束 |
| `1007000006` | 主持人凭据无效 |

---

#### 3.10.8 语音播报可用性

| 项 | 值 |
| --- | --- |
| 请求方法 | `GET` |
| 请求路径 | `/meeting/digital/status` |
| 鉴权 | 免登录 |

> 前端据此显示「语音不可用」，而不是点了播报却静默无声。只检查开关与 DashScope 合成配置是否齐备，**不探测任何进程**。

**请求参数**：无

```bash
curl http://localhost:8086/api/meeting/digital/status
```

**响应字段（data，`DigitalStatusVO`）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `enabled` | Boolean | 配置总开关（`tb.lecture.digital-human.enabled`） |
| `available` | Boolean | 开关打开且合成配置齐备 |

```json
{ "code": 0, "message": "", "data": { "enabled": true, "available": true } }
```

---

### 3.11 PPT 讲稿模块

> 模块前缀 `/ppt/speech`，整体免登录。无状态接口，**不落库、不解析 pptx**：正文与备注由前端提取后随请求上报。

#### 3.11.1 生成 / 优化讲稿

| 项 | 值 |
| --- | --- |
| 请求方法 | `POST` |
| 请求路径 | `/ppt/speech/optimize` |
| 鉴权 | 免登录 |
| Content-Type | `application/json` |
| 超时建议 | 整篇是一次长请求，前端建议不设超时，离开页面用 `AbortController` 中止 |

> `pages` 长度为 1 即「优化本页」，长度为 N 即「优化全部」；后端按批调用模型，整篇只发一次请求。

**请求参数（Body，`PptSpeechOptimizeReqVO`）**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `targetSeconds` | Integer | 否 | 本场目标总时长（秒）；为空时不约束篇幅，只要求口语化 |
| `pages` | Array | 是 | 需要生成讲稿的页面，顺序即幻灯片顺序，**上限 200 页** |

**`pages[]` 元素（`PptSpeechPageVO`）**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `index` | Integer | 是 | 页码，从 1 开始（回填时靠它对位） |
| `title` | String | 否 | 该页标题（前端取正文首行） |
| `text` | String | 否 | 该页正文纯文本 |
| `remark` | String | 否 | 该页现有备注 —— AI 的重写对象 |

**请求示例**

```bash
curl -X POST http://localhost:8086/api/ppt/speech/optimize \
  -H "Content-Type: application/json" \
  -d '{
    "targetSeconds": 600,
    "pages": [
      { "index": 1, "title": "课程导论", "text": "企业级应用开发综合实践", "remark": "" },
      { "index": 2, "title": "Spring Boot 概述", "text": "自动装配、起步依赖", "remark": "" }
    ]
  }'
```

**响应字段（data，`PptSpeechOptimizeRespVO`）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `pages` | Array | 生成成功的页（按请求顺序），见下方 `PptSpeechResultVO` |
| `failedIndexes` | Integer[] | 两轮（正常 + 补跑一次）后仍未成功的页码；为空表示全部成功 |

**`pages[]` 元素（`PptSpeechResultVO`）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `index` | Integer | 页码（与请求一一对应） |
| `remark` | String | 优化后的讲稿正文（写回前端该页的 remark） |
| `chars` | Integer | 字数（去掉所有空白后统计） |
| `seconds` | Integer | 预计朗读秒数（字数 ÷ 语速常数 4.0 字/秒） |

**响应示例**

```json
{
  "code": 0,
  "message": "",
  "data": {
    "pages": [
      { "index": 1, "remark": "同学们好，这节课我们进入企业级应用开发综合实践……", "chars": 96, "seconds": 24 },
      { "index": 2, "remark": "先看 Spring Boot 的两个核心能力：起步依赖与自动装配……", "chars": 84, "seconds": 21 }
    ],
    "failedIndexes": []
  }
}
```

**可能错误**

| code | message |
| --- | --- |
| `1008000000` | 请提供需要优化的页面 |
| `1008000001` | 单次最多优化 200 页，请分多次进行 |
| `1008000002` | 讲稿生成失败，请稍后重试 |

> 部分失败**不走错误码**：成功的页照常返回，失败的页码放进 `failedIndexes`，只有一页都没成功时才整体报错。

---

#### 3.11.2 讲稿流式播报

| 项 | 值 |
| --- | --- |
| 请求方法 | `POST` |
| 请求路径 | `/ppt/speech/speak` |
| 鉴权 | 免登录 |
| Content-Type | `application/json` |
| 响应类型 | `text/event-stream` |

> 声音**流式回给发起请求的浏览器**（放映页），由该页播放；放映页正被屏幕共享，声音随共享轨到学生端。
> **调用方断开即取消合成**：不需要额外的「停止」接口。

**请求参数（Body，`DigitalSpeakReqVO`）**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `text` | String | 是 | 要播报的文本（讲稿正文），**≤1000 字符** |
| `meetingNo` | String | 否 | 会议号；为空即彩排试听，不落库 |
| `hostToken` | String | 否 | 主持人凭据；与 `meetingNo` 一起给出时才以数字人身份落库 |

**请求示例**

```bash
curl -N -X POST http://localhost:8086/api/ppt/speech/speak \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{"text":"同学们好，这节课我们进入企业级应用开发综合实践……","meetingNo":"482913","hostToken":"<hostToken>"}'
```

**SSE 事件**

| 事件 | 时机 | `data` 关键字段 |
| --- | --- | --- |
| `status` | 合成开始 | `state = "started"`、`sampleRate = 48000`、`channels = 1`、`encoding = "pcm_s16le"` |
| `chunk` | 每个音频分片 | **纯文本 base64**（不带 JSON 引号），解码即 PCM 字节 |
| `status` | 合成结束 | `state = "done"`、`bytes`、`firstChunkMs`、`durationMs` |
| `error` | 合成失败 | `message` |

```
event:status
data:{"state":"started","sampleRate":48000,"channels":1,"encoding":"pcm_s16le"}

event:chunk
data:UklGRiQAAABXQVZFZm10IBAAAAABAAEA...

event:status
data:{"state":"done","bytes":182400,"firstChunkMs":420,"durationMs":1900}
```

**建流前校验失败**（返回普通 `CommonResult`，`Content-Type: application/json`）

| code | message |
| --- | --- |
| `1007000014` | 数字人未启用 |
| `1007000016` | 请填写要播报的内容 |
| `1007000017` | 播报内容长度不能超过 1000 个字符 |
| `1007000001` | 会议不存在 |
| `1007000002` | 会议已结束 |
| `1007000006` | 主持人凭据无效 |

> 前端读取器按响应 `Content-Type` 分辨「事件流」与「JSON 错误体」。

---

### 3.12 测试与联调模块

#### 3.12.1 连通性测试

| 项 | 值 |
| --- | --- |
| 请求方法 | `GET` |
| 请求路径 | `/test/ping` |
| 鉴权 | **需登录**（不在白名单） |

**请求参数**：无

```bash
curl http://localhost:8086/api/test/ping -H "Authorization: Bearer <token>"
```

**响应字段（data）**：`String`，固定返回 `pong`

```json
{ "code": 0, "message": "", "data": "pong" }
```

---

#### 3.12.2 连通性测试（test2）

| 项 | 值 |
| --- | --- |
| 请求方法 | `GET` |
| 请求路径 | `/test2/ping` |
| 鉴权 | **需登录**（不在白名单） |

**请求参数**：无

```bash
curl http://localhost:8086/api/test2/ping -H "Authorization: Bearer <token>"
```

**响应字段（data）**：`String`，固定返回 `pong`

---

#### 3.12.3 读取本地 PPTX 验证解析

| 项 | 值 |
| --- | --- |
| 请求方法 | `GET` |
| 请求路径 | `/test2/read-pptx` |
| 鉴权 | 免登录 |

> 验证性接口：读取服务端**硬编码路径**的 `.pptx` 文件，逐页解析文本框与备注，用于验证解析能力。

**请求参数**：无

```bash
curl http://localhost:8086/api/test2/read-pptx
```

**响应字段（data[]，`PptxPage`）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `index` | int | 幻灯片页码（从 1 开始） |
| `text` | String | 该页所有文本框拼接的正文 |
| `remark` | String | 该页备注（演讲者备注） |

**响应示例**

```json
{
  "code": 0,
  "message": "",
  "data": [
    { "index": 1, "text": "企业级应用开发综合实践\n课程导论", "remark": "开场介绍课程目标" },
    { "index": 2, "text": "第一章 Spring Boot 概述", "remark": "" }
  ]
}
```

**可能错误**

| code | message |
| --- | --- |
| `500` | 读取PPTX失败: {原因} |

---

#### 3.12.4 读取作业文件文本（联调桩）

| 项 | 值 |
| --- | --- |
| 请求方法 | `GET` |
| 请求路径 | `/test2/file-text` |
| 鉴权 | 免登录 |

> **联调桩，不是业务接口**：不接收请求体，要读哪些文件由服务端方法里的模拟数据（`mockGradeReq()`）决定，直接改代码即可切换。
> 逐个文件按 `fileId` 从 MinIO 取字节并以 Tika 提取文本，用于验证后续 AI 批改的取文本链路。

**请求参数**：无

```bash
curl http://localhost:8086/api/test2/file-text
```

**响应字段（data，`HomeworkFileTextRespVO`）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `total` | Integer | 请求的文件总数 |
| `success` | Integer | 成功提取到文本的数量 |
| `failed` | Integer | 失败数量 |
| `items` | Array | 逐条结果，见下方 `FileTextItemVO` |

**`items[]` 元素（`FileTextItemVO`）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | Long | 作业文件 ID（回显） |
| `fileId` | Long | 实际使用的 `infra_file.id`；请求未带时按 `taskId + path` 回查补齐 |
| `name` | String | 文件基础名 |
| `path` | String | 相对路径 |
| `type` | String | 文件 MIME 类型 |
| `size` | Long | 文件大小（可能为 0，不代表文件为空） |
| `source` | String | `fileId` 来源：`param` 请求体直接携带 / `lookup` 回查补齐 |
| `text` | String | 提取到的文本（超过 20000 字符截断）；失败时为空 |
| `textLength` | Integer | 截断前的原始字符数；失败时为 0 |
| `truncated` | Boolean | 文本是否被截断 |
| `error` | String | 失败原因；成功时为 `null` |

**响应示例**

```json
{
  "code": 0,
  "message": "",
  "data": {
    "total": 2,
    "success": 1,
    "failed": 1,
    "items": [
      {
        "id": 31,
        "fileId": 98,
        "name": "test1.txt",
        "path": "test/test1.txt",
        "type": "text/plain",
        "size": 1024,
        "source": "param",
        "text": "第一章 习题……",
        "textLength": 128,
        "truncated": false,
        "error": null
      }
    ]
  }
}
```

**可能错误**

| code | message |
| --- | --- |
| `1006000000` | 作业文件清单不能为空 |

---

### 3.13 管理端身份模块

> 模块前缀 `/admin/auth`，**需登录**，并额外要求 `admin:dashboard` 权限（管理员与审核员拥有）。
> 角色与权限由后端按 RBAC 实时解析（`system_user_role` → `system_role` → `system_role_menu` → `system_menu`），
> 前端不保存、也不自行拼装身份快照。管理端其余业务接口当前仍为前端 mock。

#### 3.13.1 当前账号资料

| 项 | 值 |
| --- | --- |
| 请求方法 | `GET` |
| 请求路径 | `/admin/auth/profile` |
| 鉴权 | 需登录 + `admin:dashboard` |

**请求参数**：无（依赖请求头 Token）

**请求示例**

```bash
curl http://localhost:8086/api/admin/auth/profile \
  -H "Authorization: Bearer <accessToken>"
```

**响应字段（data，`AdminProfileRespVO`）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `userId` | Long | 用户 ID |
| `username` | String | 登录账号 |
| `nickname` | String | 昵称 |
| `avatar` | String | 头像 URL（可为 `null`） |
| `roles` | String[] | 角色编码集合（只含启用角色） |
| `permissions` | String[] | 权限码集合（只含启用菜单，去重） |

**响应示例**

```json
{
  "code": 0,
  "message": "",
  "data": {
    "userId": 1,
    "username": "admin",
    "nickname": "管理员",
    "avatar": null,
    "roles": ["admin"],
    "permissions": ["admin:dashboard", "system:user:list", "system:role:list"]
  }
}
```

**可能错误**

| HTTP | code | message |
| --- | --- | --- |
| 401 | `401` | 账号未登录 |
| 403 | `403` | 没有该操作权限 |
| 200 | `1002000000` | 用户不存在 |

> 未登录与无权限都由安全框架直接返回，响应体仍是 `{code, message, data}` 结构。

---

#### 3.13.2 当前账号权限码

| 项 | 值 |
| --- | --- |
| 请求方法 | `GET` |
| 请求路径 | `/admin/auth/permissions` |
| 鉴权 | 需登录 + `admin:dashboard` |

**请求参数**：无

**请求示例**

```bash
curl http://localhost:8086/api/admin/auth/permissions \
  -H "Authorization: Bearer <accessToken>"
```

**响应字段（data）**：`String[]`，权限码集合；无权限时为空数组

```json
{
  "code": 0,
  "message": "",
  "data": ["admin:dashboard", "system:user:list", "system:role:list"]
}
```

**可能错误**：同 [3.13.1](#3131-当前账号资料)（401 / 403）。

---

## 4. 附录

### 4.1 错误码总表

**全局错误码**（`GlobalErrorCodeConstants`）

| code | message |
| --- | --- |
| `0` | 成功 |
| `400` | 请求参数不正确 |
| `401` | 账号未登录 |
| `403` | 没有该操作权限 |
| `404` | 请求未找到 |
| `405` | 请求方法不正确 |
| `423` | 请求失败，请稍后重试 |
| `429` | 请求过于频繁，请稍后重试 |
| `500` | 系统异常 |
| `501` | 功能未实现/未开启 |
| `502` | 错误的配置项 |
| `900` | 重复请求，请稍后重试 |
| `901` | 演示模式，禁止写操作 |
| `999` | 未知错误 |

**模块业务错误码**

| code | 模块 | message |
| --- | --- | --- |
| `1001000000` | infra | 上传文件不能为空 |
| `1001000001` | infra | 文件大小超过上限（50MB） |
| `1001000002` | infra | 不支持的文件类型 |
| `1002000000` | test（user） | 用户不存在 |
| `1002000001` | test（user） | 用户账号已经存在 |
| `1002000002` | test（user） | 邮箱已经存在 |
| `1002002000` | test（auth） | 登录失败，账号密码不正确 |
| `1002002001` | test（auth） | 登录失败，账号被禁用 |
| `1003000000` | knowledgebase | 知识库空间不存在 |
| `1003000001` | knowledgebase | 知识库文档不存在 |
| `1003000002` | knowledgebase | 知识库文档已完成向量化，不可重复提交 |
| `1004000000` | doctemplate | 请先上传模板文档 |
| `1004000001` | doctemplate | 请填写 AI 描述说明 |
| `1004000002` | doctemplate | AI 描述说明不能超过 500 字 |
| `1004000003` | doctemplate | 模板文件不存在或已被删除 |
| `1004000004` | doctemplate | 仅支持 .docx 模板文档 |
| `1004000005` | doctemplate | 生成记录不存在 |
| `1004000006` | doctemplate | 文档生成失败：{原因} |
| `1004000007` | doctemplate | 文档处理服务（docx-mcp）不可用：{原因} |
| `1004000008` | doctemplate | AI 返回内容解析失败：{原因} |
| `1004000000` | homework | 上传文件不能为空 |
| `1004000001` | homework | 文件与相对路径数量不一致 |
| `1004000002` | homework | 文件相对路径不合法 |
| `1004000003` | homework | 单次批改最多 50 份作业 |
| `1004000004` | homework | 文件大小超过上限（50MB）：{相对路径} |
| `1004000005` | homework | 不支持的文件类型：{相对路径} |
| `1005000000` | workflow | 工作流大模型调用失败：{原因} |
| `1006000000` | test2 | 作业文件清单不能为空 |
| `1007000000` | lecture（meeting） | 请填写会议标题 |
| `1007000001` | lecture（meeting） | 会议不存在 |
| `1007000002` | lecture（meeting） | 会议已结束 |
| `1007000003` | lecture（meeting） | 请输入你的名字 |
| `1007000004` | lecture（meeting） | 名字长度不能超过 {n} 个字符 |
| `1007000006` | lecture（meeting） | 主持人凭据无效 |
| `1007000007` | lecture（meeting） | 会议号生成失败，请重试 |
| `1007000008` | lecture（meeting） | 会议标题长度不能超过 {n} 个字符 |
| `1007000009` | lecture（meeting） | 参会凭据无效，请重新入会 |
| `1007000010` | lecture（meeting） | 实时通道未配置：请先启动 LiveKit 并配置 LIVEKIT_API_KEY / LIVEKIT_API_SECRET |
| `1007000011` | lecture（meeting） | 消息内容不能为空 |
| `1007000012` | lecture（meeting） | 消息长度不能超过 {n} 个字符 |
| `1007000013` | lecture（meeting） | 发送者凭据无效，请重新入会 |
| `1007000014` | lecture（digital） | 数字人未启用 |
| `1007000016` | lecture（digital） | 请填写要播报的内容 |
| `1007000017` | lecture（digital） | 播报内容长度不能超过 {n} 个字符 |
| `1008000000` | lecture（ppt） | 请提供需要优化的页面 |
| `1008000001` | lecture（ppt） | 单次最多优化 {n} 页，请分多次进行 |
| `1008000002` | lecture（ppt） | 讲稿生成失败，请稍后重试 |

> **号段冲突（代码现状）**：`1_004_000_xxx` 同时被 `doctemplate` 与 `homework` 使用，两模块的 `0~5` 号含义不同。排查线上错误码时需结合接口路径判断归属；新增错误码前应先重新分配号段。
>
> `1_007_000_005`、`1_007_000_015`、`1_007_000_018`、`1_007_000_019` 已作废（编号不复用）。

### 4.2 枚举速查

| 枚举 | 取值 |
| --- | --- |
| 角色编码 | `normal` / `member` / `admin` / `auditor`（来自 `system_role.code`） |
| 管理端入口权限 | `admin:dashboard` |
| 知识库文档状态 | `0` 待处理 / `1` 解析中 / `2` 已完成（已向量化）/ `3` 失败 / `4` 待确认（已解析未向量化） |
| 文档类型 | `PDF` / `WORD` / `MARKDOWN` |
| 模板生成任务状态 | `1` 成功 / `2` 部分成功 / `3` 失败 |
| 工作流定义状态 | `0` 草稿 / `1` 已发布 |
| 工作流执行状态（`workflow_run.status`） | `0` 待运行 / `1` 执行中 / `2` 成功 / `3` 失败 / `4` 中断（等待人工确认，非终态）/ `5` 已取消 |
| `/chat` 状态（帧内 `status`） | `created` / `in_progress` / `requires_action` / `completed` / `failed` |
| `/chat` 事件名 | `conversation.chat.created` / `conversation.chat.in_progress` / `conversation.message.completed` / `conversation.chat.requires_action` / `conversation.chat.completed` / `conversation.chat.failed` / `done` |
| `/chat` 消息类型 `type` | `answer`（卡片 / 正文）/ `verbose`（节点产出） |
| `/chat` 内容类型 `content_type` | `card` / `verbose` / `text` |
| `/chat` 卡片类型 `card_type` | `INPUT` |
| `/chat` 续跑消息类型 `message.type` | `card_submit` |
| 会议状态 | `1` 进行中 / `2` 已结束 |
| 参会 / 消息角色 `role` | `1` 老师 / `2` 学生 / `3` 数字人 |
| 会议消息类型 `kind` | `1` 文字 / `2` 数字人播报 |
| 讲稿播报 PCM 格式 | `pcm_s16le`，`48000Hz`，单声道 |
| 作业导出格式 `exportFormat` | `xlsx` / `csv` |
| 变量类型 `type` | `string` / `integer` / `float` / `boolean` / `list` / `object` / `file` |

### 4.3 数据模型

**`CommonResult<T>`（统一响应）**

`code: int`、`message: String`、`data: T`

**`AuthLoginRespVO`（登录响应）**

`userId: Long`、`roles: String[]`、`permissions: String[]`、`accessToken: String`、`refreshToken: String`、`expiresTime: LocalDateTime`

**`AuthRefreshReqVO`（刷新令牌请求）**

`refreshToken: String`

**`AdminProfileRespVO`（管理端账号资料）**

`userId: Long`、`username: String`、`nickname: String`、`avatar: String`、`roles: String[]`、`permissions: String[]`

**`FileUploadRespVO`（文件上传响应）**

`id: Long`、`name: String`、`size: Long`、`type: String`、`url: String`

**`GenerateReqVO`（模板生成请求）**

`templateFileId: Long`、`spaceId: String`、`description: String`、`quality: String`

**`GenerationRespVO`（模板生成响应）**

`generationId: Long`、`status: Integer`、`durationMs: Long`、`errorMsg: String`、`docs: DocFileRespVO[]`

**`DocFileRespVO`（生成文件）**

`id: Long`、`fileId: Long`、`name: String`、`type: String`、`size: Long`、`tag: String`、`summary: String`、`src: String`、`createTime: LocalDateTime`

**`GenerationHistoryRespVO`（生成历史）**

`id: Long`、`title: String`、`kb: String`、`type: String`、`time: String`、`status: String`、`docCount: Integer`

**`SpaceRespVO`（知识库空间）**

`id: Long`、`name: String`、`description: String`、`documentCount: Long`、`createTime: LocalDateTime`

**`DocumentRespVO`（知识库文档）**

`id: Long`、`spaceId: Long`、`name: String`、`type: String`、`status: Integer`、`parseProgress: Integer`、`parseError: String`、`createTime: LocalDateTime`

**`ChunkRespVO`（文档分片）**

`id: Long`、`documentId: Long`、`index: Integer`、`content: String`

**`WorkflowSaveReqVO`（保存工作流）**

`id: String`、`name: String`、`description: String`、`schemaJson: String`

**`WorkflowDetailRespVO`（工作流详情）**

`id: String`、`name: String`、`description: String`、`status: Integer`、`builtin: Boolean`、`stepCount: Integer`、`schemaJson: String`、`updateTime: String`

**`WorkflowVO`（工作流列表项）**

`id: String`、`name: String`、`description: String`、`status: Integer`、`builtin: Boolean`、`stepCount: Integer`、`updateTime: String`

**`ConversationCreateReqVO`（新建会话请求）**

`workflowId: String`、`title: String`

**`ConversationVO`（会话）**

`conversationId: String`、`workflowId: String`、`workflowName: String`、`title: String`、`createTime: String`、`updateTime: String`

**`ChatReqVO`（执行 / 续跑请求）**

`conversationId: String`、`workflowId: String`、`inputs: Map<String,Object>`、`message: ChatMessageVO`

**`ChatMessageVO`（一轮输入）**

`type: String`、`nodeId: String`、`values: Map<String,Object>`

**`HomeworkUploadRespVO`（作业上传响应）**

`taskId: Long`、`folderName: String`、`fileCount: Integer`、`totalSize: Long`、`paths: String[]`、`files: HomeworkFileItemVO[]`

**`HomeworkFileItemVO`（作业文件）**

`id: Long`、`fileId: Long`、`name: String`、`path: String`、`size: Long`、`type: String`、`url: String`

**`HomeworkGradeReqVO`（批改请求）**

`taskId: Long`、`spaceIds: String[]`、`files: HomeworkFileItemVO[]`、`description: String`、`exportFormat: String`、`workflowId: String`

**`MeetingCreateRespVO`（建会响应）**

`meetingId: Long`、`meetingNo: String`、`joinUrl: String`、`hostToken: String`

**`MeetingJoinRespVO`（入会响应）**

`attendeeId: Long`、`attendeeToken: String`、`meeting: MeetingInfoVO`、`attendees: MeetingAttendeeVO[]`、`messages: MeetingMessageVO[]`、`realtime: MeetingRealtimeVO`

**`MeetingDetailRespVO`（会议详情 / 学生侧状态）**

`meeting: MeetingInfoVO`、`attendees: MeetingAttendeeVO[]`、`messages: MeetingMessageVO[]`、`realtime: MeetingRealtimeVO`

**`MeetingInfoVO`（会议信息）**

`id: Long`、`meetingNo: String`、`title: String`、`status: Integer`、`startTime: LocalDateTime`、`endTime: LocalDateTime`、`attendeeCount: Integer`（累计入会人数，**不是在线人数**）

**`MeetingAttendeeVO`（参会台账行）**

`id: Long`、`nickname: String`、`role: Integer`、`joinTime: LocalDateTime`、`leaveTime: LocalDateTime`（`null` = 尚未显式离开，**不等于在线**）

**`MeetingMessageVO`（会议消息）**

`id: Long`、`attendeeId: Long`、`nickname: String`、`role: Integer`、`content: String`、`kind: Integer`、`createTime: LocalDateTime`

**`MeetingRealtimeVO`（实时通道接入信息）**

`livekitUrl: String`、`livekitToken: String`

**`DigitalStatusVO`（语音可用性）**

`enabled: Boolean`、`available: Boolean`

**`PptSpeechOptimizeReqVO`（讲稿生成请求）**

`targetSeconds: Integer`、`pages: PptSpeechPageVO[]`

**`PptSpeechPageVO`（一页 PPT）**

`index: Integer`、`title: String`、`text: String`、`remark: String`

**`PptSpeechOptimizeRespVO`（讲稿生成响应）**

`pages: PptSpeechResultVO[]`、`failedIndexes: Integer[]`

**`PptSpeechResultVO`（单页讲稿）**

`index: Integer`、`remark: String`、`chars: Integer`、`seconds: Integer`

**`DigitalSpeakReqVO`（播报请求）**

`text: String`、`meetingNo: String`、`hostToken: String`

**`PptxPage`（PPTX 页）**

`index: int`、`text: String`、`remark: String`

**`HomeworkFileTextRespVO`（作业文本读取结果）**

`total: Integer`、`success: Integer`、`failed: Integer`、`items: FileTextItemVO[]`

**`FileTextItemVO`（单文件文本结果）**

`id: Long`、`fileId: Long`、`name: String`、`path: String`、`type: String`、`size: Long`、`source: String`、`text: String`、`textLength: Integer`、`truncated: Boolean`、`error: String`

---

> 说明：本文档以 `backend/` 实际代码为准，路径与字段均取自 Controller / VO 定义。若代码变更，请同步更新本文件。
