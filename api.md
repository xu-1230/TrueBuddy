# TrueBuddy API 接口文档

> 项目：TrueBuddy（AI 驱动的教学辅助平台）
> 后端：Spring Boot 多模块（`backend/`） ｜ 文档生成日期：2026-09-26
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
  - [3.1 认证与身份模块](#31-认证与身份模块)
  - [3.2 文件模块](#32-文件模块)
  - [3.3 文档模板生成模块](#33-文档模板生成模块)
  - [3.4 知识库空间模块](#34-知识库空间模块)
  - [3.5 知识库文档模块](#35-知识库文档模块)
  - [3.6 知识库文档分片模块](#36-知识库文档分片模块)
  - [3.7 自定义工作流模块](#37-自定义工作流模块)
  - [3.8 测试模块](#38-测试模块)
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
| 数据格式 | 请求/响应体 `application/json`；文件上传 `multipart/form-data` |
| 字符编码 | UTF-8 |
| 上传上限 | 单文件 / 单请求均为 `50MB` |

> 下文所有「请求路径」均**省略**上下文前缀 `/api`。实际调用需拼上，例如 `GET /test/ping` → `GET http://localhost:8086/api/test/ping`。

### 1.2 统一响应结构

**所有接口**统一返回 `CommonResult<T>`：

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
- Token 有效期：`accessToken` 默认 **2 小时**，`refreshToken` 默认 **7 天**。

**免登录（白名单）接口**，无需携带 Token：

| 路径 | 说明 |
| --- | --- |
| `/system/auth/login` | 登录 |
| `/test2/read-pptx` | PPTX 读取验证 |
| `/doc-template/**` | 文档模板生成模块全部接口 |
| `/workflow/**` | 自定义工作流模块全部接口 |

除白名单外，其余接口（`/knowledge-base/**`、`/infra/**`、`/test/**`、`/identity/**` 等）**必须携带有效 Token**，否则返回 HTTP `401` 或 `code = 401`。

### 1.4 通用约定

| 项 | 约定 |
| --- | --- |
| HTTP 方法语义 | `GET` 查询 ｜ `POST` 新建或动作 ｜ `PUT` 更新 ｜ `DELETE` 删除 |
| 时间字段 | `LocalDateTime` 类型，JSON 序列化为 `yyyy-MM-dd HH:mm:ss`；部分工作流字段为**秒级时间戳**（`Long`） |
| 长整型 ID | 普通模块为 `Long`（JSON 数字）；工作流模块的 `workflow_id` 为 **String**（雪花 ID 超出 JS 安全整数范围） |
| 入参 | 一律使用 `XxxReqVO` / `XxxSaveReqVO`；文件字段使用 `multipart/form-data` |
| 出参 | 一律使用 `XxxRespVO` 或 `List<XxxRespVO>`；列表接口**当前未分页** |
| 文件上传 | 不要手动设置 `Content-Type`，由客户端自动生成带 boundary 的 `multipart/form-data` |
| 跨域 | 后端全局开启 CORS；前端开发环境另经 Vite 代理 |

### 1.5 错误码规范

- 全局错误码：`0` 成功、`400` 请求参数错误、`401` 未登录、`403` 无权限、`404` 路径未找到、`405` 方法不允许、`500` 系统异常。
- 业务错误码：`1_00X_YYY_ZZZ` 格式，`X` 为模块号（见 [4.1 错误码总表](#41-错误码总表)）。
- 前端建议：响应拦截器已自动解包 `code === 0` 的场景，非 0 时抛出 `Error(message)`，业务层 `try/catch` 后展示 `message`。

---

## 2. 接口索引

> 共 **10 个 Controller / 27 个接口**。路径省略 `/api` 前缀。

| # | 模块 | 方法 | 路径 | 说明 | 鉴权 |
| --- | --- | --- | --- | --- | --- |
| 1 | 认证身份 | POST | `/system/auth/login` | 账号密码登录 | 免登录 |
| 2 | 认证身份 | GET | `/identity/normal` | 普通用户身份校验 | 需登录 |
| 3 | 认证身份 | GET | `/identity/member` | 会员身份校验 | 需登录 |
| 4 | 认证身份 | GET | `/identity/admin` | 管理员身份校验 | 需登录 |
| 5 | 认证身份 | GET | `/test/ping` | 连通性测试 | 需登录 |
| 6 | 文件 | POST | `/infra/file/upload` | 通用文件上传 | 需登录 |
| 7 | 模板生成 | POST | `/doc-template/generate` | 发起模板复刻生成 | 免登录 |
| 8 | 模板生成 | GET | `/doc-template/history` | 生成历史记录 | 免登录 |
| 9 | 模板生成 | GET | `/doc-template/detail` | 生成详情 | 免登录 |
| 10 | 知识库空间 | GET | `/knowledge-base/space/list` | 空间列表 | 需登录 |
| 11 | 知识库空间 | POST | `/knowledge-base/space/create` | 新建空间 | 需登录 |
| 12 | 知识库空间 | PUT | `/knowledge-base/space/update` | 更新空间 | 需登录 |
| 13 | 知识库空间 | DELETE | `/knowledge-base/space/delete` | 删除空间 | 需登录 |
| 14 | 知识库文档 | GET | `/knowledge-base/document/list` | 文档列表 | 需登录 |
| 15 | 知识库文档 | POST | `/knowledge-base/document/create` | 新建文档记录 | 需登录 |
| 16 | 知识库文档 | POST | `/knowledge-base/document/upload` | 上传文件并提交解析 | 需登录 |
| 17 | 知识库文档 | GET | `/knowledge-base/document/parse-status` | 轮询解析状态 / 分片预览 | 需登录 |
| 18 | 知识库文档 | POST | `/knowledge-base/document/vectorize` | 确认分片后向量化 | 需登录 |
| 19 | 知识库文档 | DELETE | `/knowledge-base/document/delete` | 删除文档 | 需登录 |
| 20 | 知识库文档 | POST | `/knowledge-base/document/abandon` | 放弃未向量化文档 | 需登录 |
| 21 | 文档分片 | GET | `/knowledge-base/chunk/list` | 文档分片列表 | 需登录 |
| 22 | 工作流 | POST | `/workflow/run` | 提交执行 / 恢复人工确认（SSE 事件流） | 免登录 |
| 23 | 工作流 | GET | `/workflow/get_process` | 查询执行状态与结果（2s 轮询兜底） | 免登录 |
| 24 | 工作流 | POST | `/workflow/save` | 保存工作流定义 | 免登录 |
| 25 | 工作流 | GET | `/workflow/detail` | 工作流详情 | 免登录 |
| 26 | 工作流 | GET | `/workflow/list` | 工作流列表 | 免登录 |
| 27 | 测试 | GET | `/test2/read-pptx` | 读取本地 PPTX 验证解析 | 免登录 |

---

## 3. 接口详情

### 3.1 认证与身份模块

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

**响应字段（data）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `userId` | Long | 用户 ID |
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
    "accessToken": "5f1c9a7be3d84c0d8a2f6b7c9d1e0a3b",
    "refreshToken": "9a8b7c6d5e4f3a2b1c0d9e8f7a6b5c4d",
    "expiresTime": "2026-09-26 16:30:00"
  }
}
```

**可能错误**

| code | message |
| --- | --- |
| `1002002000` | 登录失败，账号密码不正确 |
| `1002002001` | 登录失败，账号被禁用 |

---

#### 3.1.2 普通用户身份校验

| 项 | 值 |
| --- | --- |
| 请求方法 | `GET` |
| 请求路径 | `/identity/normal` |
| 鉴权 | 需登录 |

**请求参数**：无（依赖请求头 Token）

**请求示例**

```bash
curl http://localhost:8086/api/identity/normal \
  -H "Authorization: Bearer 5f1c9a7be3d84c0d8a2f6b7c9d1e0a3b"
```

**响应字段（data）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data` | String | 校验通过时返回 `当前用户是：普通用户` |

**响应示例**

```json
{ "code": 0, "message": "", "data": "当前用户是：普通用户" }
```

> 若当前登录用户身份不是「普通用户」，返回 `code = 1002002002`，`message = "当前用户无该身份权限"`。

---

#### 3.1.3 会员身份校验

| 项 | 值 |
| --- | --- |
| 请求方法 | `GET` |
| 请求路径 | `/identity/member` |
| 鉴权 | 需登录 |

**请求参数**：无

**请求示例**

```bash
curl http://localhost:8086/api/identity/member -H "Authorization: Bearer <token>"
```

**响应字段（data）**：`String`，通过时为 `当前用户是：会员`

```json
{ "code": 0, "message": "", "data": "当前用户是：会员" }
```

---

#### 3.1.4 管理员身份校验

| 项 | 值 |
| --- | --- |
| 请求方法 | `GET` |
| 请求路径 | `/identity/admin` |
| 鉴权 | 需登录 |

**请求参数**：无

**请求示例**

```bash
curl http://localhost:8086/api/identity/admin -H "Authorization: Bearer <token>"
```

**响应字段（data）**：`String`，通过时为 `当前用户是：管理员`

```json
{ "code": 0, "message": "", "data": "当前用户是：管理员" }
```

---

#### 3.1.5 连通性测试

| 项 | 值 |
| --- | --- |
| 请求方法 | `GET` |
| 请求路径 | `/test/ping` |
| 鉴权 | 需登录 |

**请求参数**：无

**请求示例**

```bash
curl http://localhost:8086/api/test/ping -H "Authorization: Bearer <token>"
```

**响应字段（data）**：`String`，固定返回 `pong`

```json
{ "code": 0, "message": "", "data": "pong" }
```

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
| `file` | File | 是 | 待上传文件，单文件上限 `50MB` |

**允许的扩展名**：`pdf`、`doc`、`docx`、`md`、`markdown`、`txt`、`ppt`、`pptx`、`xls`、`xlsx`、`png`、`jpg`、`jpeg`、`gif`、`webp`

**请求示例**

```bash
curl -X POST http://localhost:8086/api/infra/file/upload \
  -H "Authorization: Bearer <token>" \
  -F "file=@D:/files/教学模板.docx"
```

**响应字段（data）**

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

**请求参数（Body）**

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

**响应字段（data）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `generationId` | Long | 生成任务 ID，可用于 `/doc-template/detail` 查询 |
| `status` | Integer | 任务状态：`1` 成功 / `2` 部分成功 / `3` 失败 |
| `durationMs` | Long | 任务耗时（毫秒） |
| `errorMsg` | String | 失败/部分失败的错误摘要，成功时为空 |
| `docs` | Array | 生成文件列表，见下方 `DocFileRespVO` |

**`docs[]` 元素字段**

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
        "createTime": "2026-09-26 10:12:33"
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
      "time": "2026-09-26 10:12",
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
        "createTime": "2026-09-26 10:12:33"
      }
    ]
  }
}
```

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
      "createTime": "2026-09-20 09:00:00"
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

**请求参数（Body）**

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

**请求参数（Body）**

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

**响应示例**

```json
{
  "code": 0,
  "message": "",
  "data": {
    "id": 3,
    "name": "课程资料库（已更新）",
    "description": "补充了 2026 秋季学期资料",
    "documentCount": 6,
    "createTime": "2026-09-20 09:00:00"
  }
}
```

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
      "createTime": "2026-09-21 14:05:00"
    },
    {
      "id": 22,
      "spaceId": 3,
      "name": "实验指导.docx",
      "type": "WORD",
      "status": 4,
      "parseProgress": 100,
      "parseError": null,
      "createTime": "2026-09-22 10:30:00"
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

**请求参数（Body）**

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
| `1001000001` | 文件大小超过上限（50MB） |
| `1001000002` | 不支持的文件类型 |
| `1003000000` | 知识库空间不存在 |

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
| `error` | String | 失败原因（仅失败时存在） |

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

> 对未确认分片的文档执行**逻辑删除**，删除后不再出现在列表；已完成向量化的文档不受影响。

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
| `1003000002` | 知识库文档已完成向量化，不可重复提交 |

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

### 3.7 自定义工作流模块

> 模块前缀 `/workflow`，整体免登录。
> **执行模型（SSE 实时推送 + 轮询兜底）**：`POST /run` 提交执行并以 `text/event-stream` 推送事件
> （`created` → `message`\* → `completed` → `done`）；`GET /get_process?workflow_id=` 为轮询兜底，
> 断线重连 / 刷新页面后仍能取回进度与终态。
> **人工确认**：执行到「人工确认」节点时**暂停**，推 `message`（`type = requires_action`）并
> **保持连接打开**；调用方再调一次 `POST /run`（带 `executeId` + `confirmedPlan`）提交清单，
> 后续事件继续推回原连接。
> 画布由调用方经 `schemaJson` 传入，后端不内置默认画布；执行会话存内存，默认 **30 分钟** 有效期，
> 进程重启即失效。

#### 3.7.1 提交执行 / 恢复人工确认

| 项 | 值 |
| --- | --- |
| 请求方法 | `POST` |
| 请求路径 | `/workflow/run` |
| 鉴权 | 免登录 |
| Content-Type | `application/json` |
| 响应类型 | `text/event-stream`（两种分支统一；SSE 负载为业务数据本身，**不经** `CommonResult` 包装） |

**单接口双语义**：请求体不含 `executeId` → 新建执行；含 `executeId` → 视为恢复等待态会话。

**请求参数（Body）**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `schemaJson` | String | 新建时必填 | 画布 schema（JSON 字符串，顶层含 `nodes` / `edges` / `versions`） |
| `executeId` | String | 恢复时必填 | 执行会话 id；传值即视为「恢复等待态会话」 |
| `confirmedPlan` | String[] | 恢复时必填 | 已确认的批改清单（作业文件路径列表），非空 |
| `workflowId` | String | 否 | 本次执行所用画布对应的工作流定义 id；传入后事件里的 `workflowId` 有值，否则为 `null` |
| `inputs` | Object | 否 | 开始节点输入（按变量名覆盖） |
| `fileTree` | String[] | 否 | 作业文件夹文件树（相对路径列表，目录以 `/` 结尾） |
| `knowledgeList` | String[] | 否 | 知识库列表（ID 或名称） |
| `aiDescription` | String | 否 | AI 批改说明 |
| `fullScore` | Integer | 否 | 本次批改满分 |

**请求示例（新建执行）**

```bash
curl -N -X POST http://localhost:8086/api/workflow/run \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "schemaJson": "{\"nodes\":[...],\"edges\":[...],\"versions\":{}}",
    "fileTree": ["张三_作业.docx", "李四_作业.docx", "素材/", "素材/参考图.png"],
    "knowledgeList": ["课程资料库"],
    "aiDescription": "按评分细则批改，重点关注格式规范",
    "fullScore": 100
  }'
```

**请求示例（恢复人工确认）**

```bash
curl -N -X POST http://localhost:8086/api/workflow/run \
  -H "Content-Type: application/json" \
  -d '{"executeId":"wf-7689720381804118025","confirmedPlan":["张三_作业.docx","李四_作业.docx"]}'
```

**响应**

- 新建执行：事件流，事件契约见 §3.7.2；
- 恢复人工确认：本次响应只推一条 `done`（负载 `{"accepted":true}`）后立即结束，
  后续执行事件继续推回**原连接**。

**可能错误**

| code | message | 出现方式 |
| --- | --- | --- |
| `1005000000` | 工作流定义为空，无法执行 | SSE `error` 事件（随后补发 `done`） |
| `1005000001` | 工作流定义解析失败：{原因} | SSE `error` 事件 |
| `1005000002` | 不支持的工作流节点类型：{类型} | SSE `error` 事件 |
| `1005000003` | 工作流建图失败：{原因} | SSE `error` 事件 |
| `1005000004` | 工作流执行失败：{原因} | SSE `error` 事件（执行期异常） |
| `1005000007` | 缺少必填的工作流输入：{变量名} | SSE `error` 事件 |
| `1005000010` | 工作流定义 JSON 格式不正确：{原因} | SSE `error` 事件 |
| `1005000005` | 工作流会话不存在或已过期，请重新发起 | 普通响应（恢复分支会话已过期） |
| `1005000015` | 工作流当前不处于等待确认状态：{executeId} | 普通响应（恢复分支） |
| `1005000016` | 确认后的批改清单不能为空 | 普通响应（恢复分支） |

> 画布校验失败也在**事件流内**回传（先 `error` 再 `done`），调用方只需处理一种协议；
> HTTP 状态码仍为 `200`。恢复分支若被业务校验拦下，则由全局异常处理器返回普通
> `CommonResult`（`Content-Type: application/json`）。

---

#### 3.7.2 SSE 事件契约

`event:` 名与 `data`（JSON，即业务数据本身）：

| 事件 | 时机 | `data` 关键字段 |
| --- | --- | --- |
| `created` | 会话已建立、任务已提交 | `executeId`、`workflowId`、`nodeResults`（`NodeResultVO[]`，全部「未执行」） |
| `message` | 增量过程事件 | `type` 决定附加字段，见下表 |
| `completed` | 整体执行完成 | 与 `WorkflowProcessRespVO` 同构（含 `result`、`exeHistoryStatus = 2`、`exeCostMs`、`message`） |
| `error` | 校验失败 / 执行异常 | `code`、`message`（其后**必定**补发 `done`） |
| `done` | 流终止（必发，最后一条） | `{}`；恢复请求的应答为 `{ accepted: true }` |

**`message` 事件的 `type` 与附加字段**

| `type` | 时机 | 附加字段 |
| --- | --- | --- |
| `node_started` | 节点开始执行（含循环节点） | `nodeId`、`nodeResult` |
| `node_finished` | 节点执行完成 | `nodeId`、`nodeResult` |
| `record` | 循环内每完成一份批改 | `nodeId`、`index`（从 1 开始）、`total`、`record`（`GradingRecordVO`） |
| `requires_action` | 执行到人工确认节点暂停 | `nodeId`、`nodeName`、`plan`（`PlanItemVO[]`）、`defaultPlan`（`String[]`） |

> 心跳：服务端每 20 秒发一条注释帧（`:heartbeat`），调用方解析时可忽略。
> 收到 `requires_action` 后**连接保持打开**，提交确认清单（§3.7.1 恢复分支）后同一条连接会继续推
> `node_started` / `node_finished` / `record` / `completed` / `done`。

**事件流示例（新建 → 中断 → 恢复 → 完成）**

```
event:created
data:{"executeId":"wf-7689720381804118025","workflowId":null,"nodeResults":[...]}

event:message
data:{"type":"node_finished","nodeId":"plan","nodeResult":{...}}

event:message
data:{"type":"requires_action","nodeId":"confirm","nodeName":"人工确认","plan":[...],"defaultPlan":["张三_作业.docx"]}

（调用方提交确认清单后，同一条连接继续）

event:message
data:{"type":"node_started","nodeId":"grading_loop","nodeResult":{...}}

event:message
data:{"type":"record","nodeId":"grading_loop","index":1,"total":2,"record":{...}}

event:completed
data:{"executeId":"wf-7689720381804118025","exeHistoryStatus":2,"result":{...}}

event:done
data:{}
```

---

#### 3.7.3 查询执行状态（轮询兜底）

| 项 | 值 |
| --- | --- |
| 请求方法 | `GET` |
| 请求路径 | `/workflow/get_process` |
| 鉴权 | 免登录 |
| 轮询建议 | 每 **2 秒** 一次；`exeHistoryStatus` 为 `2` / `3` 时结束（`1` / `4` 继续） |

**请求参数（Query）**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `workflow_id` | String | 是 | 执行会话 id（SSE 的 `created` 事件下发，或恢复请求的入参） |

**请求示例**

```bash
curl "http://localhost:8086/api/workflow/get_process?workflow_id=wf-7689720381804118025"
```

**响应字段（data，`WorkflowProcessRespVO`）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `executeId` | String | 执行会话 id |
| `workFlowId` | String | 画布对应的工作流定义 id；未关联时为 `null` |
| `exeHistoryStatus` | Integer | 执行状态：`1` 执行中 / `2` 执行成功 / `3` 执行失败 / `4` 等待用户输入（**非终态**） |
| `executeStatus` | Integer | 执行结果码：`1` 正常 / `2` 异常 |
| `workflowExeCost` | String | 总耗时（人类可读，如 `0.000s` / `2s` / `1m32s`） |
| `exeCostMs` | Long | 总耗时（毫秒） |
| `nodeEvents` | Array | 事件列表（预留，固定空数组） |
| `nodeResults` | Array | 节点级执行结果，见下方 `NodeResultVO` |
| `tokenAndCost` | Object | token 与费用（本项目固定零值占位） |
| `projectId` | String | 项目 id（固定 `"0"`） |
| `message` | String | 提示信息：执行中为空串，成功为完成提示，失败为失败原因 |
| `result` | Object | 业务结果 `WorkflowRunRespVO`；**执行中为 `null`，终态返回完整结果** |
| `pendingConfirm` | Object | 待确认清单；**仅 `exeHistoryStatus = 4` 时有值**，见下方 `WorkflowPendingConfirmVO` |

**`pendingConfirm` 字段（`WorkflowPendingConfirmVO`，仅等待用户输入时返回）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `nodeId` | String | 暂停节点 id（人工确认节点） |
| `nodeName` | String | 暂停节点标题 |
| `plan` | Array | 规划出的批改清单（全部条目），元素见下方 `PlanItemVO` |
| `defaultPlan` | String[] | 默认勾选清单（仅 `needGrading = true` 的文件路径） |

**`nodeResults[]` 元素（`NodeResultVO`）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `nodeId` | String | 节点 id |
| `nodeName` | String | 节点标题 |
| `nodeType` | String | 节点类型：`Start` / `LLM` / `Loop` / `HumanConfirm` / `End` |
| `nodeStatus` | Integer | 节点状态：`1` 未执行 / `2` 执行中 / `3` 执行成功 / `4` 执行失败 |
| `errorInfo` | String | 失败原因（成功为空串） |
| `errorLevel` | String | 错误级别（预留，固定空串） |
| `input` | String | 节点输入（JSON 字符串） |
| `output` | String | 节点输出（JSON 字符串） |
| `nodeExeCost` | String | 节点耗时（人类可读） |
| `nodeExeCostMs` | Long | 节点耗时（毫秒） |
| `needAsync` | Boolean | 是否需要异步（预留，固定 `false`） |
| `logVersion` | Integer | 日志版本（固定 `1`） |

**`tokenAndCost` 字段**

| 字段 | 值 |
| --- | --- |
| `inputTokens` / `outputTokens` / `totalTokens` | 固定 `"0 Tokens"` |
| `inputCost` / `outputCost` / `totalCost` | 固定 `"0.00"` |

**`result` 字段（`WorkflowRunRespVO`，仅终态返回）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `plan` | Array | 规划出的批改清单（全部条目），元素见下方 `PlanItemVO` |
| `defaultPlan` | String[] | 默认批改清单（仅 `needGrading = true` 的文件路径） |
| `confirmedPlan` | String[] | 本次实际批改的清单（人工确认提交后写入状态） |
| `records` | Array | 逐份批改结果，见下方 `GradingRecordVO` |
| `content` | String | 结束节点渲染出的最终展示内容 |
| `fullScore` | Integer | 本次批改满分 |
| `nodeTrace` | String[] | 已执行节点顺序（联调用） |
| `state` | Object | 最终状态快照，键为 `{nodeId}.{outputName}`（联调用） |

**`plan[]` 元素（`PlanItemVO`）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `filePath` | String | 作业文件相对路径 |
| `fileName` | String | 作业文件名 |
| `needGrading` | Boolean | 是否需要批改（非文本类文件为 `false`） |
| `reason` | String | 判定理由 |

**`records[]` 元素（`GradingRecordVO`）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `fileName` | String | 作业文件名 |
| `filePath` | String | 作业文件相对路径 |
| `totalScore` | Integer | 得分（满分由入参 `fullScore` 决定；解析失败时为 `null`） |
| `grade` | String | 等级：优秀 / 良好 / 及格 / 待改进 |
| `dimensions` | Object | 三维度得分：`accuracy`（准确性）/ `completeness`（完整性）/ `expression`（表达），各 `0-100` |
| `comment` | String | 教师评语 |

**响应示例（执行中）**

```json
{
  "code": 0,
  "message": "",
  "data": {
    "executeId": "wf-7689720381804118025",
    "workFlowId": null,
    "exeHistoryStatus": 1,
    "executeStatus": 1,
    "workflowExeCost": "12s",
    "exeCostMs": 12000,
    "nodeEvents": [],
    "nodeResults": [
      {
        "nodeId": "100001",
        "nodeName": "开始",
        "nodeType": "Start",
        "nodeStatus": 3,
        "errorInfo": "",
        "errorLevel": "",
        "input": "{\"fileTree\":[\"张三_作业.docx\"]}",
        "output": "{\"fileTree\":[\"张三_作业.docx\"]}",
        "nodeExeCost": "0.002s",
        "nodeExeCostMs": 2,
        "needAsync": false,
        "logVersion": 1
      },
      {
        "nodeId": "109380",
        "nodeName": "逐份批改",
        "nodeType": "Loop",
        "nodeStatus": 2,
        "errorInfo": "",
        "errorLevel": "",
        "input": "{\"items\":[\"张三_作业.docx\"]}",
        "output": "",
        "nodeExeCost": "0.000s",
        "nodeExeCostMs": 0,
        "needAsync": false,
        "logVersion": 1
      }
    ],
    "tokenAndCost": {
      "inputTokens": "0 Tokens", "outputTokens": "0 Tokens", "totalTokens": "0 Tokens",
      "inputCost": "0.00", "outputCost": "0.00", "totalCost": "0.00"
    },
    "projectId": "0",
    "message": "",
    "result": null
  }
}
```

**响应示例（执行成功）**

```json
{
  "code": 0,
  "message": "",
  "data": {
    "executeId": "wf-7689720381804118025",
    "workFlowId": "7573236273650122804",
    "exeHistoryStatus": 2,
    "executeStatus": 1,
    "workflowExeCost": "1m32s",
    "exeCostMs": 92000,
    "nodeEvents": [],
    "nodeResults": ["…同上，所有节点 nodeStatus 均为 3…"],
    "tokenAndCost": {
      "inputTokens": "0 Tokens", "outputTokens": "0 Tokens", "totalTokens": "0 Tokens",
      "inputCost": "0.00", "outputCost": "0.00", "totalCost": "0.00"
    },
    "projectId": "0",
    "message": "工作流执行完成",
    "result": {
      "plan": [
        { "filePath": "张三_作业.docx", "fileName": "张三_作业.docx", "needGrading": true, "reason": "文本作业" },
        { "filePath": "素材/参考图.png", "fileName": "参考图.png", "needGrading": false, "reason": "非文本文件" }
      ],
      "defaultPlan": ["张三_作业.docx"],
      "confirmedPlan": ["张三_作业.docx"],
      "records": [
        {
          "fileName": "张三_作业.docx",
          "filePath": "张三_作业.docx",
          "totalScore": 88,
          "grade": "良好",
          "dimensions": { "accuracy": 90.0, "completeness": 85.0, "expression": 88.0 },
          "comment": "整体结构完整，建议补充异常处理说明"
        }
      ],
      "content": "本次共批改 1 份作业，平均分 88",
      "fullScore": 100,
      "nodeTrace": ["__START__", "100001", "109380", "__END__"],
      "state": { "109380.results": ["…"] }
    }
  }
}
```

**响应示例（执行失败）**

```json
{
  "code": 0,
  "message": "",
  "data": {
    "executeId": "wf-7689720381804118025",
    "workFlowId": null,
    "exeHistoryStatus": 3,
    "executeStatus": 2,
    "workflowExeCost": "5s",
    "exeCostMs": 5000,
    "nodeEvents": [],
    "nodeResults": ["…失败节点 nodeStatus 为 4、errorInfo 非空…"],
    "tokenAndCost": {
      "inputTokens": "0 Tokens", "outputTokens": "0 Tokens", "totalTokens": "0 Tokens",
      "inputCost": "0.00", "outputCost": "0.00", "totalCost": "0.00"
    },
    "projectId": "0",
    "message": "工作流执行失败：xxx",
    "result": null
  }
}
```

**可能错误**

| code | message |
| --- | --- |
| `1005000005` | 工作流会话不存在或已过期，请重新发起 |

> 与 Coze 的差异：外层用项目 `CommonResult`（`message` 而非 `msg`）；字段名统一 camelCase（Coze 的 `NodeName` / `NodeType` 不照搬）；未实现 `isBatch` / `batch` / `raw_output` / `extra` 等字段（循环内部子节点不拆项，因此批改进度只在终态 `result.records` 中体现）；`tokenAndCost` 与 `nodeEvents` 为占位。

---

#### 3.7.4 保存工作流定义

| 项 | 值 |
| --- | --- |
| 请求方法 | `POST` |
| 请求路径 | `/workflow/save` |
| 鉴权 | 免登录 |
| Content-Type | `application/json` |

> `id` 为空表示新建，非空表示更新已有工作流；保存即覆盖当前记录，**不做版本管理**。

**请求参数（Body）**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | Long | 否 | 工作流 ID；空 = 新建，非空 = 更新 |
| `name` | String | 否 | 工作流名称（为空时后端兜底为「未命名工作流」） |
| `desc` | String | 否 | 工作流说明 |
| `schemaJson` | String | 是 | 画布结构（JSON 字符串） |
| `status` | Integer | 否 | 状态：`0` 草稿 / `1` 已发布（为空按 `0` 处理） |

**请求示例**

```bash
curl -X POST http://localhost:8086/api/workflow/save \
  -H "Content-Type: application/json" \
  -d '{
    "name": "作业批改工作流",
    "desc": "教师确认后逐份批改",
    "schemaJson": "{\"nodes\":[],\"edges\":[]}",
    "status": 1
  }'
```

**响应字段（data）**：`String`，工作流 ID（雪花 ID 以字符串返回，避免 JS 精度丢失）

```json
{ "code": 0, "message": "", "data": "1858888000012345678" }
```

**可能错误**

| code | message |
| --- | --- |
| `1005000013` | 工作流画布内容不能为空 |
| `1005000010` | 工作流定义 JSON 格式不正确：{原因} |
| `1005000011` | 工作流不存在或已被删除 |

---

#### 3.7.5 工作流详情

| 项 | 值 |
| --- | --- |
| 请求方法 | `GET` |
| 请求路径 | `/workflow/detail` |
| 鉴权 | 免登录 |

**请求参数（Query）**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | Long | 是 | 工作流 ID |

**请求示例**

```bash
curl "http://localhost:8086/api/workflow/detail?id=1858888000012345678"
```

**响应字段（data，`WorkflowDetailRespVO`）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `workflow` | Object | 工作流主体，见下方字段表 |

**`workflow` 字段（`WorkflowVO`）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `workflow_id` | String | 工作流 ID |
| `name` | String | 工作流名称 |
| `desc` | String | 工作流说明 |
| `schema_json` | String | 画布结构（JSON 字符串，需前端 `JSON.parse`） |
| `status` | Integer | 状态：`0` 草稿 / `1` 已发布 |
| `create_time` | Long | 创建时间（秒级时间戳） |
| `update_time` | Long | 更新时间（秒级时间戳） |

**响应示例**

```json
{
  "code": 0,
  "message": "",
  "data": {
    "workflow": {
      "workflow_id": "1858888000012345678",
      "name": "作业批改工作流",
      "desc": "教师确认后逐份批改",
      "schema_json": "{\"nodes\":[],\"edges\":[]}",
      "status": 1,
      "create_time": 1758888000,
      "update_time": 1758888900
    }
  }
}
```

**可能错误**

| code | message |
| --- | --- |
| `1005000011` | 工作流不存在或已被删除 |

---

#### 3.7.6 工作流列表

| 项 | 值 |
| --- | --- |
| 请求方法 | `GET` |
| 请求路径 | `/workflow/list` |
| 鉴权 | 免登录 |

> 按更新时间倒序；列表**不含画布结构**（`schema_json` 与 `desc` 为 `null`），避免大字段拖慢列表。

**请求参数**：无

**请求示例**

```bash
curl http://localhost:8086/api/workflow/list
```

**响应字段（data[]，`WorkflowVO`）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `workflow_id` | String | 工作流 ID |
| `name` | String | 工作流名称 |
| `desc` | String | 工作流说明（列表返回 `null`） |
| `schema_json` | String | 画布结构（列表返回 `null`） |
| `status` | Integer | 状态：`0` 草稿 / `1` 已发布 |
| `create_time` | Long | 创建时间（秒级时间戳） |
| `update_time` | Long | 更新时间（秒级时间戳） |

**响应示例**

```json
{
  "code": 0,
  "message": "",
  "data": [
    {
      "workflow_id": "1858888000012345678",
      "name": "作业批改工作流",
      "desc": null,
      "schema_json": null,
      "status": 1,
      "create_time": 1758888000,
      "update_time": 1758888900
    }
  ]
}
```

---

### 3.8 测试模块

#### 3.8.1 读取本地 PPTX 验证解析

| 项 | 值 |
| --- | --- |
| 请求方法 | `GET` |
| 请求路径 | `/test2/read-pptx` |
| 鉴权 | 免登录 |

> 验证性接口：读取服务端**硬编码路径**的 `.pptx` 文件，按月/页解析文本框与备注，用于解析能力验证。

**请求参数**：无

**请求示例**

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

## 4. 附录

### 4.1 错误码总表

**全局错误码**

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

**模块业务错误码**（号段：`infra = 1_001`、`test = 1_002`、`knowledgebase = 1_003`、`doctemplate = 1_004`、`workflow = 1_005`）

| code | 模块 | message |
| --- | --- | --- |
| `1001000000` | infra | 上传文件不能为空 |
| `1001000001` | infra | 文件大小超过上限（50MB） |
| `1001000002` | infra | 不支持的文件类型 |
| `1002000000` | test | 用户不存在 |
| `1002000001` | test | 用户账号已经存在 |
| `1002000002` | test | 邮箱已经存在 |
| `1002002000` | test | 登录失败，账号密码不正确 |
| `1002002001` | test | 登录失败，账号被禁用 |
| `1002002002` | test | 当前用户无该身份权限 |
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
| `1005000000` | workflow | 工作流定义为空，无法执行 |
| `1005000001` | workflow | 工作流定义解析失败：{原因} |
| `1005000002` | workflow | 不支持的工作流节点类型：{类型} |
| `1005000003` | workflow | 工作流建图失败：{原因} |
| `1005000004` | workflow | 工作流执行失败：{原因} |
| `1005000005` | workflow | 工作流会话不存在或已过期，请重新发起 |
| `1005000007` | workflow | 缺少必填的工作流输入：{变量名} |
| `1005000010` | workflow | 工作流定义 JSON 格式不正确：{原因} |
| `1005000011` | workflow | 工作流不存在或已被删除 |
| `1005000013` | workflow | 工作流画布内容不能为空 |
| `1005000015` | workflow | 工作流当前不处于等待确认状态：{executeId} |
| `1005000016` | workflow | 确认后的批改清单不能为空 |

### 4.2 枚举速查

| 枚举 | 取值 |
| --- | --- |
| 用户身份 `UserTypeEnum` | `0` 普通用户 / `1` 会员 / `2` 管理员 |
| 知识库文档状态 | `0` 待处理 / `1` 解析中 / `2` 已完成（已向量化）/ `3` 失败 / `4` 待确认（已解析未向量化） |
| 文档类型 | `PDF` / `WORD` / `MARKDOWN` |
| 模板生成任务状态 | `1` 成功 / `2` 部分成功 / `3` 失败 |
| 执行状态 `exeHistoryStatus` | `1` 执行中 / `2` 执行成功 / `3` 执行失败 / `4` 等待用户输入（非终态） |
| 执行结果码 `executeStatus` | `1` 正常 / `2` 异常 |
| SSE 事件名 | `created` / `message` / `completed` / `error` / `done` |
| `message` 事件子类型 `type` | `node_started` / `node_finished` / `record` / `requires_action` |
| 节点状态 `nodeStatus` | `1` 未执行 / `2` 执行中 / `3` 执行成功 / `4` 执行失败 |
| 节点类型 `nodeType` | `Start` / `LLM` / `Loop` / `HumanConfirm` / `End` |
| 工作流定义状态 | `0` 草稿 / `1` 已发布 |
| 批改等级 | 优秀（≥90%）/ 良好（≥75%）/ 及格（≥60%）/ 待改进 |
| 变量类型 `type` | `string` / `integer` / `float` / `boolean` / `list` / `object` / `file` |

### 4.3 数据模型

**`CommonResult<T>`（统一响应）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | int | `0` = 成功 |
| `message` | String | 提示信息 |
| `data` | T | 业务数据 |

**`AuthLoginRespVO`（登录响应）**

`userId: Long`、`accessToken: String`、`refreshToken: String`、`expiresTime: LocalDateTime`

**`FileUploadRespVO`（文件上传响应）**

`id: Long`、`name: String`、`size: Long`、`type: String`、`url: String`

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

**`WorkflowRunRespVO`（工作流执行结果）**

`plan: PlanItemVO[]`、`defaultPlan: String[]`、`confirmedPlan: String[]`、`records: GradingRecordVO[]`、`content: String`、`fullScore: Integer`、`nodeTrace: String[]`、`state: Object`

**`PlanItemVO`（批改清单条目）**

`filePath: String`、`fileName: String`、`needGrading: Boolean`、`reason: String`

**`GradingRecordVO`（批改记录）**

`fileName: String`、`filePath: String`、`totalScore: Integer`、`grade: String`、`dimensions: GradingDimensionsVO`、`comment: String`

**`GradingDimensionsVO`（三维度得分）**

`accuracy: Double`、`completeness: Double`、`expression: Double`

**`WorkflowProcessRespVO`（工作流执行状态）**

`executeId: String`、`workFlowId: String`、`exeHistoryStatus: Integer`、`executeStatus: Integer`、`workflowExeCost: String`、`exeCostMs: Long`、`nodeEvents: Object[]`、`nodeResults: NodeResultVO[]`、`tokenAndCost: TokenAndCostVO`、`projectId: String`、`message: String`、`result: WorkflowRunRespVO`、`pendingConfirm: WorkflowPendingConfirmVO`

**`WorkflowPendingConfirmVO`（待确认清单，仅等待用户输入时返回）**

`nodeId: String`、`nodeName: String`、`plan: PlanItemVO[]`、`defaultPlan: String[]`

**`NodeResultVO`（节点执行结果）**

`nodeId: String`、`nodeName: String`、`nodeType: String`、`nodeStatus: Integer`、`errorInfo: String`、`errorLevel: String`、`input: String`、`output: String`、`nodeExeCost: String`、`nodeExeCostMs: Long`、`needAsync: Boolean`、`logVersion: Integer`

**`TokenAndCostVO`（token 与费用）**

`inputTokens: String`、`outputTokens: String`、`totalTokens: String`、`inputCost: String`、`outputCost: String`、`totalCost: String`

**`WorkflowVO`（工作流）**

`workflow_id: String`、`name: String`、`desc: String`、`schema_json: String`、`status: Integer`、`create_time: Long`、`update_time: Long`

**`PptxPage`（PPTX 页）**

`index: int`、`text: String`、`remark: String`

---

> 说明：本文档以 `backend/` 实际代码为准，路径与字段均取自 Controller / VO 定义。若代码变更，请同步更新本文件。
