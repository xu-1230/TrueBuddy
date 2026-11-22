# 2026-11-22 LiveKit 纳入 Docker 编排

> 项目：TrueBuddy ｜ 规范入口：[doc.md](../doc.md) ｜ 状态：已落地
> 本文是单次变更的过程记录，完成后结论回写 [doc-deployment.md](../doc-deployment.md) 与 [doc-devops.md](../doc-devops.md)，本文不回溯修改。

## 1. 范围

- `deploy/docker-compose.yml` 新增 `livekit` 服务，五服务统一编排。
- `deploy/.env.example` 新增 `LIVEKIT_IMAGE` / `LIVEKIT_TCP_PORT`，LiveKit 地址由 compose 生成。
- 同步 `docs/doc-deployment.md`、`docs/doc-devops.md` 的端口、配置、验证与排障口径。
- 不修改业务代码；不生成/挂载 LiveKit 配置文件。

## 2. 口径与决策

| # | 决策 | 依据 |
| --- | --- | --- |
| 1 | 用 LiveKit 1.9.12 CLI 对应的环境变量：`LIVEKIT_KEYS` / `LIVEKIT_PORT` / `LIVEKIT_RTC_TCP_PORT` / `LIVEKIT_RTC_PORT_RANGE_*` / `NODE_IP` / `UDP_PORT` | `/livekit-server help-verbose` 实测支持；`LIVEKIT_CONFIG` YAML 在本版本不认 `server`/`room.max_rooms`，已弃用 |
| 2 | `NODE_IP` 设为 `PUBLIC_HOST`，UDP 收敛 50000-50100，TCP 兜底 7881 | 沿用 doc-devops §7 既有口径；换 IP 后需重建容器 |
| 3 | 后端 `LIVEKIT_URL` 由 compose 覆盖为 `ws://${PUBLIC_HOST}:${LIVEKIT_PORT}` | 浏览器必须能直连；容器内网地址不可用于前端 |
| 4 | LiveKit API key/secret 与后端共用同一对 `LIVEKIT_API_KEY` / `LIVEKIT_API_SECRET` | 令牌签发与服务端校验必须同密钥 |
| 5 | 镜像默认官方 `livekit/livekit-server:v1.9.12`，可用 `LIVEKIT_IMAGE` 覆盖 | 兼容受限网络；本机用 daocloud 镜像源 |

## 3. 验收项

1. `docker compose config -q` 通过；五服务全部 `Up`。
2. `http://<PUBLIC_HOST>:7880` 可连到 LiveKit 信令端口；UDP 50000-50100 已映射。
3. LiveKit 日志显示节点使用 `PUBLIC_HOST` 作为 advertised IP，keys 加载成功。
4. 后端启动日志无 LiveKit 配置错误；会议创建/入会链路在浏览器中可用。
5. 文档不再写“LiveKit 需单独部署”。
