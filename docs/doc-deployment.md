# doc-deployment · Docker 一键部署

> 项目：TrueBuddy ｜ 规范入口：[doc.md](./doc.md) ｜ 最近核对代码现状：2026-11-22
> 适用范围：仅装有 Docker 的空白主机上完成全栈部署、验证、排障、自启与日志查看
> 相关：[doc-devops.md](./doc-devops.md)（配置与端口）、[doc-integration.md](./doc-integration.md)（外部服务）、[specs/2026-11-19-Docker一键部署.md](./specs/2026-11-19-Docker一键部署.md)、[specs/2026-11-22-LiveKit纳入Docker编排.md](./specs/2026-11-22-LiveKit纳入Docker编排.md)、[specs/2026-11-22-一键部署惯例对齐.md](./specs/2026-11-22-一键部署惯例对齐.md)

## 1. 交付物与架构

| 文件 | 作用 |
| --- | --- |
| `deploy/backend.Dockerfile` | Maven 构建后端全模块；运行层用 `python:3.12-slim` + OpenJDK 21，同容器拉起 docx-mcp stdio 子进程 |
| `deploy/frontend.Dockerfile` | `node:22-alpine` 构建 `vite dist`，`nginx:1.27-alpine` 托管并把 `/api` 反代到 `backend:8086` |
| `deploy/nginx.conf` | compose 版站点配置：SSE/WS 不缓冲、SPA 回退、上传 200m |
| `deploy/mcp-servers-config.json` | 覆盖 jar 内 Windows 路径，指向容器内 `python` + `/app/docx-mcp/main.py` |
| `deploy/docker-compose.yml` | 五服务编排（含 LiveKit）与白名单数据库初始化 |
| `deploy/.env.example` | 全部环境变量模板（含可启动的占位值） |
| `deploy/deploy.sh` / `deploy/deploy.ps1` | Linux/macOS/WSL 与 Windows 的一键脚本 |

服务拓扑：

| 服务 | 镜像 | 容器内端口 | 宿主机端口（默认） | 数据卷 |
| --- | --- | --- | --- | --- |
| `db` | `pgvector/pgvector:pg16` | 5432 | 不映射 | `truebuddy_db-data`（卷名以 compose 项目名为前缀） |
| `minio` | `minio/minio:latest` | 9000 / 9001 | `9000` / `9001` | `minio-data` |
| `backend` | 本地构建 | 8086 | `8086` | `doctemplate-data` |
| `frontend` | 本地构建 | 80 | `8080` | — |
| `livekit` | `livekit/livekit-server:v1.9.12` | 7880/tcp、7881/tcp、UDP 50000-50100 | `7880`、`7881`、UDP `50000-50100` | — |

数据库初始化只挂载 `backend/sql/` 的白名单脚本，**显式排除** `newsql/public.sql` 与 `restore_dropped_tables_20261022.sql`（二者含 `DROP TABLE`，不是迁移脚本）。内置工作流由后端启动时的 `WorkflowDefinitionInitializer` 幂等写入。

## 2. 一键部署

前置：主机只需 Docker Engine 与 Docker Compose 插件；不需要 Java、Node、Python、Maven、psql。

Linux / macOS / WSL：

```bash
cd <仓库根>
bash deploy/deploy.sh
```

Windows（Docker Desktop）：

```powershell
cd <仓库根>
powershell -ExecutionPolicy Bypass -File deploy/deploy.ps1
```

脚本会依次完成：

1. 检查 `docker` / `docker compose` 是否可用；
2. 若根目录 `.env` 不存在，从 `deploy/.env.example` 复制，并把 `PUBLIC_HOST=__AUTO_IP__` 替换为探测到的局域网 IP；
3. 为数据库、MinIO、LiveKit 生成随机本地凭据（仅当值为空或模板占位时），**不回显密钥**；
4. `docker compose build --pull` 拉取基础镜像并构建前后端镜像；
5. `docker compose up -d --wait --wait-timeout 300` 启动五个服务并等待健康检查通过（后端 healthcheck 请求真实 API，前端等待后端 healthy）；
6. 检查 MinIO 与 LiveKit 可达性，打印访问地址。

外部服务密钥（DeepSeek / DashScope / DocMind / 邮件）不会自动生成；用 `bash deploy/manage.sh env-check`（Windows 用 `manage.ps1 env-check`）可只列出仍为占位的键名，不输出值。

首次构建需下载 Maven / npm / pip 依赖，耗时较长；后续仅变更应用层代码时走 Docker 层缓存。

## 3. 配置说明

### 3.1 环境变量

根目录 `.env` 是容器部署的唯一真实值来源（与本地开发的 `backend/.env` 是两套：前者给 compose，后者给裸机启动）。

`deploy/.env.example` 已给全部裸占位符一个可启动的占位值。部署脚本会检查根目录 `.env`，对**缺失的键**按模板补入，已有值一律保留；因此旧 `.env` 不会因缺 `PUBLIC_HOST` 生成 `http://:9000`。需要替换的项：

| 变量 | 是否必须替换 | 说明 |
| --- | --- | --- |
| `PUBLIC_HOST` | 是（脚本自动填） | 浏览器与容器都能访问的宿主机地址，**不能填 `localhost`** |
| `SPRING_AI_OPENAI_API_KEY` 等 `SPRING_AI_OPENAI_*` | AI 功能需要 | 对话模型；占位值只保证容器启动 |
| `OPENAI_EMBEDDING_*` | 知识库需要 | 向量化模型，维度必须与 `application.yml` 的 1024 一致 |
| `DOCMIND_ACCESS_KEY_ID` / `DOCMIND_ACCESS_KEY_SECRET` | 是 | DocMind 校验非空，空串会启动失败 |
| `DASHSCOPE_API_KEY` | 播报功能需要 | 可设 `TB_DH_ENABLED=false` 关闭功能，但键仍须存在 |
| `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | 建议替换 | 同时用于 MinIO 服务与后端凭据 |
| `LIVEKIT_API_KEY` / `LIVEKIT_API_SECRET` | 建议替换 | 同时用于 LiveKit 服务与后端令牌签发，必须同一对值 |
| `POSTGRES_PASSWORD` | 建议替换 | 同时用于数据库服务与后端数据源密码 |

由 compose 覆盖、不要在 `.env` 里手改的键：`SPRING_DATASOURCE_URL`（指向 `db:5432`）、`MINIO_ENDPOINT`、`MINIO_PUBLIC_BASE_URL`、`TB_MEETING_PUBLIC_BASE_URL`、`TB_MEETING_FRONTEND_PORT`、`LIVEKIT_URL`（由 `PUBLIC_HOST` + `LIVEKIT_PORT` 拼出）、`TB_DOCTEMPLATE_TEMP_DIR`、`SPRING_AI_MCP_CLIENT_STDIO_SERVERS_CONFIGURATION`。

### 3.2 端口与镜像源

`FRONTEND_PORT` / `BACKEND_PORT` / `MINIO_PORT` / `MINIO_CONSOLE_PORT` / `LIVEKIT_PORT` / `LIVEKIT_TCP_PORT` 可改宿主机映射，容器内端口不变。LiveKit 另固定映射 UDP `50000-50100`（WebRTC 媒体通道）。改 `FRONTEND_PORT` 或 `LIVEKIT_PORT` 后需同步重建 backend（入会链接与信令地址来自这些值）。

默认从 Docker Hub 拉取基础镜像。网络受限时，在 `.env` 设置：

```dotenv
REGISTRY_PREFIX=docker.1ms.run/library/
NPM_REGISTRY=https://registry.npmmirror.com/
MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public
PIP_INDEX_URL=https://pypi.tuna.tsinghua.edu.cn/simple
```

注意 `REGISTRY_PREFIX` 必须以 `/` 结尾；后三项留空时走各自的官方源。第三方服务镜像还可用 `DB_IMAGE` / `MINIO_IMAGE` / `LIVEKIT_IMAGE` 直接覆盖，例如 `DB_IMAGE=docker.m.daocloud.io/pgvector/pgvector:pg16`。若 Docker Hub 不可达，`docker.aityp.com/r/docker.io/minio/minio` 详情页给出的可用拉取地址是 `swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/minio/minio:RELEASE.2025-09-07T16-13-09Z`（amd64），可直接作为 `MINIO_IMAGE` 的值；该页域名本身不是 registry 前缀。

### 3.3 数据持久化

三个 named volume 保存状态：`db-data`（全部业务表）、`minio-data`（对象文件）、`doctemplate-data`（文档生成临时目录）。`docker compose down` 不删卷；只有显式 `down -v` 才会清空，执行前必须备份。

## 4. 启动与验证

手工等价命令（脚本失败时用于分步定位）：

```bash
cd <仓库根>
docker compose -f deploy/docker-compose.yml --env-file .env build --pull
docker compose -f deploy/docker-compose.yml --env-file .env up -d
docker compose -f deploy/docker-compose.yml --env-file .env ps
```

验收项：

```bash
# 数据库
docker compose -f deploy/docker-compose.yml exec -T db pg_isready -U postgres -d tb

# 后端 API（经前端 nginx 反代；返回 code=0 即通）
docker compose -f deploy/docker-compose.yml exec -T frontend wget -qO- http://backend:8086/api/homework/task/list

# MinIO 存活
docker compose -f deploy/docker-compose.yml exec -T frontend wget -qO- http://minio:9000/minio/health/live

# LiveKit 信令端口（返回 HTTP 响应码即端口可达）
curl -I http://<PUBLIC_HOST>:7880/
```

`docker compose -f deploy/docker-compose.yml logs livekit` 应显示 `starting LiveKit server`，其中 `nodeIP` 等于 `PUBLIC_HOST`、`rtc.portUDP` 为 `50000-50100`。

浏览器验证：`http://<PUBLIC_HOST>:8080` 能打开登录页；登录后依次检查知识库列表、作业批改历史抽屉、文档模板页不报 5xx。MinIO 控制台在 `http://<PUBLIC_HOST>:9001`；会议页建会后学生应能经 `ws://<PUBLIC_HOST>:7880` 入会。

## 5. 自启动与日志

所有服务声明 `restart: unless-stopped`。主机重启后，只要 Docker daemon 开机自启，容器会自动恢复；若 daemon 未自启：

```bash
sudo systemctl enable --now docker
```

常用日志命令：

```bash
# 全部服务滚动日志
docker compose -f deploy/docker-compose.yml logs -f

# 单服务最近 200 行
docker compose -f deploy/docker-compose.yml logs --tail=200 backend

# 直接跟容器日志（不经过 compose）
docker logs -f --tail=200 truebuddy-backend
```

后端日志只输出到控制台，没有文件日志路径；nginx 访问日志在 `frontend` 容器的 `/var/log/nginx/access.log`。

### 5.1 日常管理命令

参考 Supabase `run.sh` 的子命令形态，提供跨平台管理脚本：

```bash
bash deploy/manage.sh <命令> [服务名...]
```

```powershell
powershell -ExecutionPolicy Bypass -File deploy/manage.ps1 <命令> [服务名...]
```

| 命令 | 作用 |
| --- | --- |
| `start` | 启动并等待健康检查 |
| `stop` | 停止并移除容器，保留数据卷 |
| `restart` | 重启全部或指定服务 |
| `recreate` | 强制重建全部或指定服务，用于环境变量变更 |
| `logs` | 滚动查看全部或指定服务日志 |
| `ps` | 查看服务状态 |
| `pull` | 拉取服务镜像 |
| `env-check` | 只列出仍为占位值的外部密钥键名，不输出值 |
| `help` | 查看帮助 |

示例：

```bash
bash deploy/manage.sh logs backend
bash deploy/manage.sh recreate livekit backend frontend
bash deploy/manage.sh env-check
```

## 6. 常见部署问题排查

| 现象 | 定位 | 处理 |
| --- | --- | --- |
| 拉镜像超时 / `manifest unknown` | 仓库不可达或标签写死 | 设置 `REGISTRY_PREFIX`（以 `/` 结尾）后重新执行部署脚本 |
| `backend` 反复重启，日志有 `Could not resolve placeholder` | `.env` 缺键 | 对照 `deploy/.env.example` 补齐；改完 `up -d --force-recreate backend` |
| 后端启动失败，日志有 DocMind 凭据错误 | AK/SK 为空串 | 填非空占位或真实值；DocMind 校验不接受空串 |
| 页面能开但 `/api` 报 502 | nginx 启动时未解析到 backend / 后端未就绪 | 看 `backend` 日志；`docker compose ... restart frontend` |
| 登录后接口 500，数据库报 relation 不存在 | 卷已用旧 schema 初始化过 | 备份后 `down -v` 重建（会清库）；compose 的初始化 SQL 只在空卷执行 |
| MinIO 上传成功但文件链接打不开 | `PUBLIC_HOST` 不是浏览器可达地址 | 改成宿主机局域网 IP，重建 `backend` |
| 文档模板复刻报 MCP 工具不可用 | stdio 进程没起来 | 看 `backend` 日志中 MCP 启动记录；确认 `deploy/mcp-servers-config.json` 被打包进镜像。Dockerfile 已把 `mcp` 钉在 `<2`（源码用 v1 的 `mcp.server.fastmcp`，v2 已改名） |
| 工作流 SSE 一直不返回首字节 | 反代缓冲或超时 | `deploy/nginx.conf` 已关闭 `proxy_buffering` 并设 300s 读超时；确认用的是 compose 版 nginx 配置 |
| 换网络 / 换 IP 后会议链接错误或媒体黑屏 | 入会地址与 LiveKit `NODE_IP` 都由 `PUBLIC_HOST` 拼出 | 修改 `.env` 后 `up -d --force-recreate livekit backend frontend` |
| LiveKit 相关功能不可用 | livekit 容器未起、密钥不一致或 UDP 端口未放行 | 看 `docker compose ... logs livekit`；确认 `LIVEKIT_API_KEY/SECRET` 与后端一致，防火墙放行 UDP 50000-50100；键留空时入会被明确拒绝 |

## 7. 现状与规划

| 项 | 现状 | 规范要求 / 待整改 |
| --- | --- | --- |
| 全栈容器 | **已落地**（2026-11-22）：`deploy/` 下五服务 compose + 双平台一键脚本 + `manage.sh/ps1` 日常管理 | — |
| 外部密钥 | 模板给可启动占位值，真实能力需用户替换 | 对外发布前必须轮换历史泄露密钥（见 doc-devops §3） |
| LiveKit | **已纳入编排**（2026-11-22）：信令 7880 / TCP 7881 / UDP 50000-50100，配置由环境变量注入 | 换网络后需重建 livekit/backend/frontend；媒体黑屏优先查 `PUBLIC_HOST` 与 UDP 放行 |
| HTTPS | 仅 HTTP 明文，未接证书 | 规划：前置 TLS 终止或 Let's Encrypt |
| 数据库迁移 | 仍靠首次建库执行白名单 SQL，无 Flyway/Liquibase | 待整改：引入迁移工具后再允许对已有卷升级 |
