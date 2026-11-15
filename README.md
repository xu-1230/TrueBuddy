<div align="center">

# TrueBuddy · AI 驱动的教学辅助平台

**AI 驱动的教学辅助平台 —— 把 AI 能力落到教师每天真正要交的五类产物上**

`Java 17` · `Spring Boot 4` · `Vue 3` · `PostgreSQL + pgvector` · `MCP`

</div>

---

> 面向一线教师的**本地 / 内网 Web 应用**:备课、批改、上课与资料整理,一站式完成 **Word 教案(模板复刻)、作业批改、PPT 讲稿与放映、可检索知识库、模拟会议实时播报**。

## ✨ 核心特性

| 模块 | 说明 |
| --- | --- |
| 📝 **教案模板复刻** | 上传学校模板 docx,AI 规划内容、MCP 工具逐段替换校验,产出与模板版式完全一致的 Word 教案;知识库 RAG 自动注入备课上下文 |
| ✅ **作业批改** | 作业文件上传与 AI 辅助批改,批改产物统一经对象存储管理 |
| 🖥️ **PPT 讲稿与放映** | 课件上传后分批生成讲稿;放映页逐页流式播报(CosyVoice TTS + 口型驱动),声音随屏幕共享轨直达学生端,无需任何额外进程 |
| 📚 **知识库 RAG** | 阿里云 DocMind 解析 → AI 语义分片 → `text-embedding-v3` 向量化 → pgvector HNSW 余弦检索 |
| 🎓 **模拟会议** | 自托管 LiveKit 实时课堂;媒体与聊天 DataChannel 全程不经过后端,学生"禁止发言"由令牌在服务端强制 |
| 🧩 **可视化工作流** | 拖拽式流程编排,LLM 节点接入 DeepSeek,SSE 实时回传执行过程 |

架构上的几个特点:

- **统一返回 `CommonResult<T>`**,长任务一律走 SSE(`fetch` + `ReadableStream`),前端拦截器自动解包
- **后端多模块 Maven**(`tb-module-*` 按业务拆分,`tb-server` 启动器),业务 Mapper 用 MyBatis 注解 SQL
- **docx 编辑能力通过 MCP 提供**:Python 工具服务由后端以 stdio 子进程拉起,前后端零侵入
- **密钥外迁**:真实配置只存 `backend/.env`(gitignored),`application.yml` 仅保留占位符(ADR 0005)

## 🏗️ 系统架构

```mermaid
flowchart LR
    FE["frontend<br/>Vue 3 SPA :5173"]

    subgraph BE["backend · Spring Boot 4 (Java 17) :8086 /api"]
        SRV["tb-server"]
        M1["doctemplate 教案"]
        M2["homework 批改"]
        M3["knowledgebase 知识库"]
        M4["lecture 课堂"]
        M5["workflow 工作流"]
    end

    MCP["docx-mcp<br/>Python MCP · stdio"]

    subgraph LOCAL["本地 / 内网基础设施"]
        PG[("PostgreSQL<br/>+ pgvector :5432")]
        MO[("MinIO :9000")]
        LK["LiveKit :7880<br/>(自托管 Docker)"]
    end

    subgraph CLOUD["外部 AI 服务"]
        LLM["DeepSeek LLM"]
        EMB["DashScope Embedding"]
        TTS["CosyVoice TTS"]
        DM["阿里云 DocMind"]
    end

    FE -- "HTTP / SSE" --> SRV
    FE -. "livekit-client 直连" .-> LK
    SRV --> M1 & M2 & M3 & M4 & M5
    M1 -. "stdio · MCP" .-> MCP
    M1 & M2 & M3 & M4 & M5 --> PG
    M1 & M2 & M3 --> MO
    M4 --> LK
    M1 & M4 & M5 --> LLM
    M3 --> EMB
    M3 --> DM
    M4 --> TTS
```

## 🛠️ 技术栈

| 层 | 技术 |
| --- | --- |
| 后端 | Java 17 · Spring Boot 4.0 · Spring AI · MyBatis(注解 SQL) · Maven 多模块 |
| 前端 | Vue 3(`<script setup>`,纯 JS) · Vite 8 · Pinia 4 · Element Plus · Vue Flow · livekit-client |
| AI 工具 | docx-mcp(Python ≥3.12,uv 管理,MCP stdio 服务) |
| 数据 | PostgreSQL + pgvector(HNSW / 余弦) · MinIO 对象存储 |
| 实时 | LiveKit(自托管,媒体旁路) · SSE(长任务统一通道) |
| 外部服务 | DeepSeek · 阿里云百炼(Embedding / CosyVoice) · 阿里云 DocMind 文档智能 |

## 📂 项目结构

```
TrueBuddy/
├── backend/                     # Spring Boot 多模块后端
│   ├── tb-dependencies/         #   依赖版本 BOM
│   ├── tb-framework/            #   框架层:公共配置与组件
│   ├── tb-module-doctemplate/   #   教案模板复刻
│   ├── tb-module-homework/      #   作业批改
│   ├── tb-module-knowledgebase/ #   知识库 RAG
│   ├── tb-module-lecture/       #   智能课堂(会议 / PPT / 数字人)
│   ├── tb-module-workflow/      #   可视化工作流
│   ├── tb-module-infra/         #   文件与基础设施
│   ├── tb-server/               #   启动器(:8086,context-path /api)
│   └── sql/                     #   建表 SQL(手工执行,每表一文件)
├── frontend/                    # Vue 3 前端(Vite,:5173,/api 代理到 8086)
├── docx-mcp/                    # Python MCP 工具服务(uv,stdio 由后端拉起)
└── docs/                        # 文档体系:doc-*.md 规范 / adr 决策 / specs 变更规格
```

## 🚀 快速开始

### 前置条件

| 依赖 | 版本 | 用途 |
| --- | --- | --- |
| JDK | 17 | 后端编译与运行 |
| Node.js | `^22.18.0 \|\| >=24.12.0` | 前端开发与构建 |
| Python + uv | Python ≥3.12 | 拉起 docx-mcp 工具服务 |
| PostgreSQL | + pgvector 扩展 | 业务数据与向量检索(`127.0.0.1:5432/tb`) |
| MinIO | 任意近期版本 | 对象存储(`localhost:9000`) |
| Docker | 可选 | 自托管 LiveKit(模拟会议功能需要) |

### 1️⃣ 初始化数据库

手工执行 `backend/sql/` 下的建表脚本(`init.sql` 及各表 `.sql`,向量表由 Spring AI 自动创建)。

### 2️⃣ 准备环境变量

```bash
# 对照仓库根 .env.example 的变量名清单,填写真实值
# 注意:application.yml 只保留 ${VAR} 占位符,缺键会直接启动失败
cp .env.example backend/.env
```

| 变量 | 必填 | 用途 |
| --- | --- | --- |
| `SPRING_AI_OPENAI_API_KEY` 等 | ✅ | 对话模型(DeepSeek 兼容接口) |
| `OPENAI_EMBEDDING_API_KEY` | ✅ | 向量化(知识库 RAG,百炼兼容模式) |
| `DOCMIND_ACCESS_KEY_ID` / `_SECRET` | ✅ | 文档智能解析,**缺失时启动即失败** |
| `MINIO_SECRET_KEY` | ✅ | MinIO 凭据 |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | ✅ | PostgreSQL 凭据 |
| `LIVEKIT_API_KEY` / `_SECRET` | 会议功能 | 与 LiveKit 服务端 `.env` 同一对值 |
| `DASHSCOPE_API_KEY` | 讲稿播报 | CosyVoice 流式语音合成 |
| `SPRING_MAIL_USERNAME` / `_PASSWORD` | 邮件功能 | 注册 / 验证码 / 找回密码 |

完整清单与默认值见 [`.env.example`](./.env.example) 与 [docs/doc-devops.md §2](./docs/doc-devops.md)。

### 3️⃣ 调整 docx-mcp 路径

后端 `mcp-servers-config.json` 中以**绝对路径**引用 docx-mcp,换机器部署需按本机路径修改(详见 [docs/doc-integration.md §8](./docs/doc-integration.md))。

### 4️⃣ 启动后端

```bash
cd backend
./mvnw clean install -DskipTests
java -jar tb-server/target/tb-server-0.0.1-SNAPSHOT.jar
```

### 5️⃣ 启动前端

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173,/api 代理到 8086
```

### 6️⃣(可选)启动 LiveKit

模拟会议功能需要自托管 LiveKit(信令 7880 / TCP 兜底 7881 / UDP 50000-50100),部署要点见 [docs/doc-devops.md §7](./docs/doc-devops.md)。不启动时其余功能完全可用。

### 🐳 Docker 部署(前端)

```bash
cd frontend
docker compose up -d    # nginx 托管 dist,映射 8080:80;/api 反代宿主机 8086
```

> 前端已容器化(多阶段构建,`nginx.conf` 已处理 SSE / WebSocket 不缓冲与 200m 上传);后端暂无 Dockerfile,以本机 Java 进程运行。

## ✅ 质量门禁

提交前需全过以下检查(当前无 CI,门禁为本地人工执行):

| 范围 | 命令 |
| --- | --- |
| 前端 | `cd frontend && npm run lint && npm test` |
| 后端 | `cd backend && ./mvnw clean install && ./mvnw test` |

## 📖 文档

完整文档以 `docs/` 为唯一权威来源,入口 [docs/doc.md](./docs/doc.md):

| 文档 | 内容 |
| --- | --- |
| [doc.md](./docs/doc.md) | 项目概述、模块地图、全局硬约束 |
| [doc-conventions.md](./docs/doc-conventions.md) | 编码 / 组件 / 路由 / 测试 / 文档书写规范 |
| [doc-api.md](./docs/doc-api.md) | 接口约定与清单 |
| [doc-database.md](./docs/doc-database.md) | 全部表结构(事实源 `backend/sql/`) |
| [doc-integration.md](./docs/doc-integration.md) | 第三方集成(LLM / TTS / 解析 / 存储 / LiveKit / MCP) |
| [doc-devops.md](./docs/doc-devops.md) | 配置、环境变量、启动、容器化 |
| [adr/](./docs/adr/) | 不可逆决策记录(append-only) |
| [specs/](./docs/specs/) | 单次变更规格与验收口径 |

## 🤝 贡献

本项目采用 **git flow** 分支模型:

1. 从 `develop` 拉出功能分支:`feat/<topic>` / `fix/<topic>` / `docs/<topic>`
2. 提交信息遵循 Conventional Commits,如 `feat(home): xxx`
3. 通过上述质量门禁后推送,向 **`develop`** 发起 PR
4. 不可逆决策先落 `docs/adr/NNNN-*.md`,多步变更先落 `docs/specs/YYYY-MM-DD-*.md`

## 🗺️ 已知限制

如实记录(详见 docs),欢迎 Issue / PR:

- 无 CI 流水线,门禁为本地人工执行;数据库无迁移工具(SQL 手工执行)
- 后端尚未容器化
- 演示用的管理员口令校验未接入 BCrypt,上线前需整改(docs/doc-devops.md §3)
- 历史提交中含旧密钥,对外发布前建议全部轮换

## 📄 License

暂未设置开源许可证,如需复用请联系作者。
