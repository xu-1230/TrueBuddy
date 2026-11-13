# doc-integration · 第三方集成

> 项目：TrueBuddy ｜ 规范入口：[doc.md](./doc.md) ｜ 最近核对代码现状：2026-11-13
> 适用范围：全部外部服务与进程级集成的现状、代码位置与配置要点
> 相关：[doc-doctemplate.md](./doc-doctemplate.md)（MCP 工具清单）、[doc-lecture.md](./doc-lecture.md)（LiveKit / TTS 链路）、[doc-devops.md](./doc-devops.md)（配置与部署）

---

## 1. 集成总览

| 集成 | 用途 | 调用方模块 | 代码位置 |
| --- | --- | --- | --- |
| DeepSeek（OpenAI 兼容） | 对话 LLM：内容规划 / 改写规则 / 自检补规则 / 讲稿优化 / 工作流 LLM 节点 / 知识库语义分片 | doctemplate、lecture、workflow、knowledgebase | `application.yml` `spring.ai.openai.*` |
| DashScope text-embedding-v3 | 文本向量化（知识库 RAG） | knowledgebase | `application.yml` `spring.ai.openai.embedding.*`（DashScope 兼容模式） |
| DashScope CosyVoice | 语音流式合成（原生 WebSocket → 48kHz 单声道 16bit PCM） | lecture（digital） | `tb-module-lecture/.../digital/CosyVoiceTtsSynth` |
| 阿里云 DocMind 文档智能 | 知识库文档解析（Advance 直传文件流） | knowledgebase | `service/parse/docmind/DocMindParseGateway` |
| MinIO | 对象存储（上传、生成产物、预签名 URL） | infra（被全模块复用） | `tb-module-infra/.../MinioFileStorageService` |
| LiveKit（自托管） | 模拟会议实时媒体与聊天 DataChannel | lecture（livekit 签令牌/删房间）+ 前端 `livekit-client` | `tb-module-lecture/.../livekit/LiveKitTokenService` |
| docx-mcp（Python，stdio） | docx 读写工具服务（解析 / 替换 / 校验 / 填字段） | doctemplate | `DocxMcpGateway(Impl)` + `docx-mcp/main.py` |

## 2. LLM（DeepSeek）

- 基址 `https://api.deepseek.com`，模型 `deepseek-flash`（`OPENAI_BASE_URL` / `OPENAI_CHAT_MODEL` 可覆盖）；Spring AI `ChatClient` 统一封装。
- 消费方：doctemplate 的 `AiContentService`（plan / buildReplacements / buildFixups）、lecture 的 `PptSpeechServiceImpl`（分批讲稿优化）、workflow 的 `LlmNode`、knowledgebase 的 `AiDocumentChunkService`（语义分片，失败回退逐块）。

## 3. Embedding 与向量检索（pgvector）

- `text-embedding-v3`（1024 维）+ Spring AI pgvector VectorStore：`index-type=hnsw`、`distance-type=COSINE_DISTANCE`、`initialize-schema=true`。
- 唯一检索入口现状：`KbDocumentServiceImpl.searchBySpace`（单路向量召回 + spaceId 过滤，异常静默返回空串）；唯一消费方是 doctemplate 的 RAG 上下文。检索链增强属**规划**，见 doc-knowledgebase.md。

## 4. TTS（CosyVoice）

- `CosyVoiceTtsSynth` 走 DashScope 原生 WebSocket 流式合成裸 PCM（48kHz / 单声道 / 16bit），产出统计（首包耗时 / 字节）。
- 消费链路：`POST /ppt/speech/speak`（SSE，`chunk` 帧为裸 base64 PCM）→ 放映页本页 WebAudio 播放并驱动口型 → 声音随屏幕共享轨到学生端（见 doc-lecture.md §5.7）。
- **历史注记**：语音曾由独立 Node worker（`digital-human-worker/`）发布 LiveKit 音频轨，该方案 2026-10-10 整体废弃、进程已删除，现行链路见 doc-lecture.md §5.7，过程记录见 specs/2026-10-10-讲稿播报改为本页播放.md。

## 5. 文档解析（DocMind）

- SDK `docmind_api20220711`，`DocMindParseGateway` 直传文件流；endpoint 默认 `docmind-api.cn-hangzhou.aliyuncs.com`。
- **AK/SK 缺失时 `DocMindProperties` 校验直接启动失败**（不是运行时报错）。解析产物逐块落 `kb_document_parse_block`，作为 AI 语义分片的输入。

## 6. 对象存储（MinIO）

- `MinioFileStorageService`（唯一入口）：上传、`uploadBytes`、预签名 URL；元数据落 `infra_file`（bucket/path/url/size/type），`fileId` 是取文件的权威口径。
- 生成产物（教案 docx）、作业文件、知识库原文件都经它落桶；`doc-template/detail` 等接口按需重新签发 URL（作业 URL 有效期 7 天）。

## 7. 实时会议（LiveKit 自托管）

- 部署在仓库外 `D:\ctf\livekit`（Docker），媒体与聊天 DataChannel **都不经过后端**。
- 后端职责：Hutool 手写 HS256 签发令牌（`LiveKitTokenService`，学生 `canPublish=false` 由令牌在服务端强制）+ 结束会议调 `RoomService.DeleteRoom`；JVM 侧不发布任何媒体轨。
- 配置：`tb.lecture.meeting.livekit-*`；`url` 留空按 `ws://{局域网IP}:7880` 运行时推导；`LIVEKIT_API_KEY/SECRET` 必须与 `D:\ctf\livekit\.env` **同一对值**。部署细节见 doc-devops.md §7。

## 8. docx MCP（stdio 子进程）

- 接入方式：Spring AI MCP Client + `mcp-servers-config.json`（`uv run --directory <绝对路径>\docx-mcp mcp run <绝对路径>\main.py`）——**换机器必须改该文件**；配置不得内联凭据。
- `DocxMcpGatewayImpl` 通过 `ToolCallbackProvider` **按工具名**调用，统一解包 `content` 数组 JSON；句柄 `docId` 依赖同一 MCP 会话。后端只使用 6 个工具，完整工具清单见 doc-doctemplate.md §4。

## 9. 规划中的集成（未落地）

| 能力 | 状态 | 设计 |
| --- | --- | --- |
| 联网搜索（DuckDuckGo 等）+ 网页抓取入库 | **未实现**（全库无对应代码） | 见 doc-knowledgebase.md 规划节（含 SSRF 出口安全） |
| 问答对话流式接口 | **未实现**（knowledgebase 无 SSE 代码与 kb_qa_* 表） | 同上 |
