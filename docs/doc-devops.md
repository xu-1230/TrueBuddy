# doc-devops · 部署运维

> 项目：TrueBuddy ｜ 规范入口：[doc.md](./doc.md) ｜ 最近核对代码现状：2026-11-13
> 适用范围：配置落点、环境变量、启动前置、容器化、LiveKit 自托管部署与质量门禁
> 相关：[doc.md](./doc.md)（硬约束）、[doc-integration.md](./doc-integration.md)（外部服务）、[doc-conventions.md](./doc-conventions.md) §12（门禁细则）

---

## 1. 服务与端口

| 服务 | 地址 | 说明 |
| --- | --- | --- |
| 后端 tb-server | `8086`，context-path `/api` | SSE 请求不超时（`request-timeout: -1ms`），`DispatcherType.ASYNC` 放行 |
| 前端 dev server | `5173` | Vite 代理 `/api` → `8086`；LiveKit 信令不走代理、直连 7880 |
| PostgreSQL + pgvector | `127.0.0.1:5432/tb` | pgvector HNSW / COSINE，`initialize-schema: true`（向量表由 Spring AI 自动建） |
| MinIO | `http://localhost:9000` | 桶 `test-bucket`（默认值） |
| LiveKit（自托管） | `7880`（信令）/ `7881`（TCP 兜底）/ UDP 50000-50100 | 仓库外 `D:\ctf\livekit`，见 §7 |
| docx-mcp | 无端口 | 后端 stdio 子进程拉起，见 doc-integration.md §8 |

## 2. 配置落点与环境变量

- **配置一律写在 `backend/tb-server/src/main/resources/application.yml`**：真实值放 `${ENV_KEY:字面值}` 的**冒号后面**。仓库根 `.env` 自 2026-10-10 起不再被加载（`spring.config.import` 已移除），`cp .env.example .env` 这一步已作废；根 `.env.example` 降级为「可覆盖的变量名清单」，环境变量优先级仍高于配置文件。
- **改了 `application.yml` 必须重启后端**才生效。
- ⚠️ **空值也会顶掉默认值**（实测踩坑）：`${VAR:默认值}` 只在 VAR **未定义**时用默认值，VAR 定义为空串就直接用空串。IDE 运行配置挂了 EnvFile 插件时，`.env` 里任何 `VAR=`（空值）都会顶掉 yml 真实值，现象是「yml 明明填了 Key，控制台却说未配置」。排查顺序：① `.env` 别留空行；② 关掉该插件对后端的注入；③ `$env:VAR` 看当前值（**未定义与空串不是一回事**）。

环境变量清单（必填项缺失的表现见「用途」列；默认值以 `application.yml` / `.env.example` 为准）：

| 变量 | 必填 | 用途 |
| --- | --- | --- |
| `OPENAI_API_KEY` | 是 | 对话模型（模板生成 / 讲稿 / 工作流 LLM 节点 / 语义分片） |
| `OPENAI_EMBEDDING_API_KEY` | 是 | 向量化（知识库 RAG） |
| `DOCMIND_ACCESS_KEY_ID` / `DOCMIND_ACCESS_KEY_SECRET` | 是 | 文档智能解析；**缺失时后端启动即失败** |
| `LIVEKIT_API_KEY` / `LIVEKIT_API_SECRET` | 是（会议） | 与 `D:\ctf\livekit\.env` **同一对值**；缺失时建会可用、入会被明确拒绝（不静默失败） |
| `DASHSCOPE_API_KEY` | 是（播报） | CosyVoice 流式合成，可与 embedding 共用同一百炼 Key |
| `MINIO_SECRET_KEY` | 是 | MinIO 密钥（现状带弱默认值，见 §3） |
| `OPENAI_BASE_URL` / `OPENAI_CHAT_MODEL` | 否 | 对话模型基址与模型名 |
| `OPENAI_EMBEDDING_BASE_URL` / `OPENAI_EMBEDDING_MODEL` | 否 | 向量化基址与模型名 |
| `DOCMIND_ENDPOINT` / `DOCMIND_LLM_ENHANCEMENT` / `DOCMIND_ENHANCEMENT_MODE` / `DOCMIND_FORMULA_ENHANCEMENT` / `DOCMIND_OUTPUT_HTML_TABLE` | 否 | 解析行为开关（增强模式必须 `llm-enhancement=true`） |
| `LIVEKIT_URL` / `LIVEKIT_PORT` / `LIVEKIT_TOKEN_TTL_HOURS` | 否 | 信令地址（留空按局域网 IP 推导）/ 端口 / 令牌有效期 |
| `TB_DH_ENABLED` / `TB_DH_MAX_SPEAK` / `TB_DH_TTS_MODEL` / `TB_DH_TTS_VOICE` / `TB_DH_MAX_TEXT_LENGTH` | 否 | 数字人播报开关与合成参数（`tb.lecture.digital-human.*`） |
| `TB_MEETING_PUBLIC_BASE_URL` / `TB_MEETING_FRONTEND_PORT` / `TB_MEETING_HISTORY_LIMIT` | 否 | 入会链接基址（留空自动探测局域网 IPv4）、前端端口、历史消息条数 |
| `TB_PPT_MAX_PAGE_INPUT_CHARS` 等 `TB_PPT_*` | 否 | 讲稿生成的单页输入上限与分批大小 |
| `MINIO_ENDPOINT` / `MINIO_ACCESS_KEY` / `MINIO_BUCKET` | 否 | MinIO 地址 / AK / 桶名 |
| `TB_DOCTEMPLATE_TEMP_DIR` | 否 | 文档生成临时目录 |

> 原 worker 相关变量（`TB_DH_WORKER_URL` / `TB_DH_MAX_SAY` / `TB_DH_PROBE_TIMEOUT_MS` 等）随 worker 删除已作废；`DB_USERNAME` / `DB_PASSWORD` 现未被 yml 引用（数据库凭据为 `postgres` / 空密码，见 §3 #4）。

## 3. 安全待整改（如实记录，不引用密钥明文）

| # | 位置 | 现状 | 规范要求 |
| --- | --- | --- | --- |
| 1 | `application.yml` → `spring.ai.openai.api-key` | 明文密钥直接写入 | 占位符注入 |
| 2 | `application.yml` → `spring.ai.openai.embedding.api-key` | 明文默认值 | 去默认值 |
| 3 | `application.yml` → `minio.secret-key` | 明文默认值 | 去默认值 |
| 4 | `application.yml` → `spring.datasource.username/password` | `postgres` / 空密码硬编码 | 占位符注入 |
| 5 | `AdminUserServiceImpl.isPasswordMatch` | 恒返回 `true`（演示便利） | 上线前改 BCrypt 校验，并修正明文存储 |
| 6 | 密钥进入 git 跟踪的 yml | 2026-10-10 决策的已知代价 | 毕业设计环境接受；对外发布前必须清理历史 |

## 4. 启动前置与命令

| 能力 | 前置 |
| --- | --- |
| 教案生成 / 知识库 | yml 中 `OPENAI_API_KEY`、`OPENAI_EMBEDDING_API_KEY`、DocMind AK/SK（缺则启动失败）；本机装有 `uv`（拉起 docx-mcp） |
| 模拟会议 | 先启动自托管 LiveKit（§7）；`LIVEKIT_API_KEY/SECRET` 与其 `.env` 同值 |
| 讲稿播报 | `tb.lecture.digital-human.enabled=true` + `DASHSCOPE_API_KEY` 有值，**无需任何额外进程**；`GET /meeting/digital/status` 应回 `available: true` |

```bash
cd backend
./mvnw clean install -DskipTests
java -jar tb-server/target/tb-server-0.0.1-SNAPSHOT.jar
```

```bash
cd frontend
npm install
npm run dev          # 5173，/api 代理到 8086
```

## 5. 前端容器化（后端暂无容器化）

- `frontend/Dockerfile`：多阶段——`node:22-alpine`（npmmirror 镜像源）执行 `vite build` → `nginx:1.27-alpine` 只托管 `dist/`；`REGISTRY_PREFIX` 默认 `docker.1ms.run/library`。
- `frontend/docker-compose.yml`：单服务，**8080:80**；`extra_hosts: host.docker.internal:host-gateway`（供反代访问宿主机后端）。
- `frontend/nginx.conf` 要点：`location /api/` 运行时解析反代到 `http://host.docker.internal:8086`（后端未启动页面也能打开）；**SSE/WebSocket 不缓冲**（`proxy_buffering off`、升级头 map、读写超时 300s）；`client_max_body_size 200m`；`/assets` 缓存 30d；SPA `try_files ... /index.html` 回退，`index.html` no-cache。

## 6. 质量门禁与测试现状

| 范围 | 命令 | 通过标准 |
| --- | --- | --- |
| 前端 lint | `cd frontend && npm run lint` | oxlint + ESLint 无 error |
| 前端测试 | `cd frontend && npm test` | `node --test "tests/*.test.js"` 全部通过（25 个测试文件） |
| 前端构建 | `cd frontend && npm run build` | 构建成功（`build-artifact.test.js` 会检查产物） |
| 后端构建 | `cd backend && ./mvnw clean install` | 全模块编译打包成功 |
| 后端测试 | `cd backend && ./mvnw test` | 11 个 JUnit 测试类通过（lecture 8 / workflow 3 / knowledgebase 1）；真连外部服务的探针用例靠 `Assumptions` 自动跳过 |

> docx-mcp 目前**零测试**（有 `.pytest_cache` 但无 test 文件）。测试规范与优先级见 doc-conventions.md §10。

## 7. LiveKit 自托管部署（仓库外 `D:\ctf\livekit`）

| 项 | 说明 |
| --- | --- |
| 版本 | `livekit-server:v1.9.12`（钉死），镜像走 `docker.m.daocloud.io` |
| 目录内容 | `.env`（API key/secret、LAN_IP）、`livekit.yaml.template`（`rtc.node_ip` 注入、UDP 收敛 50000-50100、TCP 兜底 7881、room 上限 20）、`start.ps1`、钉版本的 `docker-compose.yml`、`make-token.mjs`、`gate/` 验证页 |
| 启动 | `start.ps1` 读 `.env` → 探测 WLAN 网卡 IP（排除虚拟网卡）→ 生成无 BOM 的 `livekit.generated.yaml` → `docker compose up -d`；停止用 `-Stop` |
| ⚠️ 换网络必踩坑 | 换热点 / IP 后重跑 `start.ps1` **还必须紧跟 `docker compose restart`**，否则容器沿用旧 `node_ip`：信令 200 但媒体黑屏。核对：`docker logs livekit-server \| grep starting` |

## 8. 部署形态现状（如实记录）

- **无 CI**（无 `.github/`）、**后端无 Dockerfile**、无部署脚本——门禁为本地人工执行（§6）。
- `frontend/package.json` scripts：`dev` / `build` / `preview` / `test` / `lint`（`run-s lint:oxlint lint:eslint`）；Node 要求 `^22.18.0 || >=24.12.0`。
- 数据库无迁移工具：建表 SQL 手工放 `backend/sql/`（每表一文件 + 日期命名的增量补丁），见 doc-database.md §1。
