# 配置真实值唯一真相源为 gitignored 的 backend/.env

> 2026-11-13 ｜ 相关：[docs/doc-devops.md](../doc-devops.md) §2 / §3、`backend/.env`（不入库）、`backend/tb-server/src/main/resources/application.yml`

## 背景

配置口径已两次反复：v1 只放环境变量（进程重启即丢，故障表现为「昨天还能播报，今天报数字人未启用 / 口令不符」）；v2（2026-10-10）把真实值直接写进 `application.yml` 的 `${VAR:字面值}` 位，代价是密钥进入 git。安全要求提高后，yml 必须回到纯占位符，但又不能退回 v1 的「重启即丢」。

## 决策

1. `application.yml` 只保留 `${VAR}` 占位符（非敏感默认值可保留）。
2. 新增 `spring.config.import`：`optional:file:./.env[.properties]` + `optional:file:./backend/.env[.properties]` 双路径，由 Spring 自己加载 gitignored 的 `backend/.env`，兼容从仓库根或 `backend/` 启动；文件缺失不阻断启动。
3. `backend/.env` 承载全部真实值，键名与 yml 占位符一一对应（数据源 / 邮件 / 对话模型用宽松绑定名 `SPRING_DATASOURCE_*` / `SPRING_MAIL_*` / `SPRING_AI_OPENAI_*`，其余用语义名），被根 `.gitignore` 的 `.env` 规则忽略，不进版本库。
4. yml 中无默认值的裸占位符（如 `${DOCMIND_ENDPOINT}`）要求 `.env` 必须覆盖对应键（值可为空串）——把「漏配」从静默回落变成启动时显式报错。

## 为什么不是别的

- **回到纯环境变量注入（v1）**：重启即丢、依赖 IDE 插件，已被 2026-10-10 的真实故障否决。
- **真实值留在 yml（v2）**：密钥进 git，与安全要求冲突；且历史已暴露，无法收回。
- **只删本次 LiveKit 默认值、其余保留 v2**：口径继续分裂，其余密钥仍在库里。

## 后果

- 重新克隆 / 换机后必须先准备 `backend/.env`（对照根 `.env.example` 的变量名清单），否则裸占位符启动失败。
- **git 历史中旧密钥（DeepSeek / DashScope / DocMind / LiveKit 凭据、QQ 授权码）已暴露，应全部轮换**——删除当前值不能收回历史。
- IDE EnvFile 插件注入的环境变量优先级高于 `.env` 导入；注意「空值顶掉默认值」的坑（doc-devops §2）。
