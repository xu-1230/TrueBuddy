# doc.md · TrueBuddy 主规范（总控）

> 项目：TrueBuddy —— AI 驱动的教学辅助平台
> 本文件是 `docs/` 的唯一索引与全局硬约束清单；其余规范按主题分文件，同一事实只在一处展开。
> 最近核对代码现状：2026-11-13 ｜ **与代码冲突时一律以代码为准**，并在对应文档按「现状 / 规范要求 / 待整改」记录。

---

## 1. 项目概述

面向一线教师的备课、批改、上课与资料整理，把 AI 能力落到五类产物：Word 教案（模板复刻）、作业批改、PPT 讲稿与放映、可检索知识库、会议实时播报。本地/内网 Web 应用。

| 组成 | 技术 |
| --- | --- |
| `backend/` | Spring Boot 4 多模块 Maven（Java 17），端口 `8086`、context-path `/api` |
| `frontend/` | Vue 3 + Vite + Pinia + Element Plus（纯 JS，无 TypeScript） |
| `docx-mcp/` | Python MCP 工具服务（uv），由后端 stdio 子进程拉起 |
| 数据/存储 | PostgreSQL + pgvector（`127.0.0.1:5432/tb`）、MinIO（9000）、自托管 LiveKit（7880，仓库外 `D:\ctf\livekit`） |

## 2. 模块地图

| 后端模块 | 路由前缀 | 前端页面 | 业务文档 |
| --- | --- | --- | --- |
| doctemplate 模板复刻 | `/doc-template` | `views/doctemplate/` | [doc-doctemplate.md](./doc-doctemplate.md) |
| homework 作业批改 | `/homework` | `views/homework/` | [doc-homework.md](./doc-homework.md) |
| knowledgebase 知识库 | `/knowledge-base/*` | `views/knowledgebase/` | [doc-knowledgebase.md](./doc-knowledgebase.md) |
| lecture 智能课堂（会议/PPT/数字人） | `/meeting`、`/ppt/speech` | `views/meeting/`、`views/ppt/` | [doc-lecture.md](./doc-lecture.md) |
| workflow 工作流（包根 `org.example.textflow`） | `/workflow`、`/chat` | `views/workflow2/` | [doc-workflow.md](./doc-workflow.md) |
| infra 文件；test / test2 验证 | `/infra/file`、`/test*` | — | —（test* 是业务验证模块，非测试代码） |

展示页 `/guides`、`/docs` 与管理端 `/admin/**` 路由表见 doc-conventions.md §5。

## 3. 文档地图（唯一索引）

| 文档 | 内容 |
| --- | --- |
| [doc-conventions.md](./doc-conventions.md) | 编码/组件/路由/状态/测试/文档书写规范、项目结构 |
| [doc-database.md](./doc-database.md) | PostgreSQL 全部表结构（事实源 `backend/sql/`） |
| [doc-api.md](./doc-api.md) | 接口约定、接口清单、Controller 映射、错误码 |
| [doc-ui.md](./doc-ui.md) | 样式/主题/动效 + 展示页模块全流程 |
| [doc-integration.md](./doc-integration.md) | 第三方集成（LLM/TTS/解析/存储/LiveKit/MCP） |
| [doc-devops.md](./doc-devops.md) | 配置、环境变量、启动、容器化、LiveKit 部署 |
| [adr/](./adr/) | 不可逆决策记录 0001–0004（append-only，不回溯修改） |
| [specs/](./specs/) | 单次变更规格与验收口径（过程记录，保留不改写） |
| [ui.html](./ui.html) | 视觉样张：先改样张 → 再改 `theme.css` |

阅读顺序：本文 → doc-conventions.md → 所改模块的 doc-{模块}.md → doc-api / doc-database 按需。分端细则：`frontend|backend/AGENT - {端}技术规约.md`。

## 4. 全局硬约束

1. 统一返回 `CommonResult<T>`（`code=0` 成功）；例外：`POST /chat`、`POST /ppt/speech/speak` 返回 `SseEmitter`，`GET /workflow/text` 返回 `void`；前端拦截器自动解包。
2. 业务 Mapper 用 MyBatis 注解 SQL（禁 XML）；workflow 模块例外走 `JdbcTemplate`；DO 不得暴露到接口层。
3. 前端纯 JS：`===`、`@/` 别名、`<script setup>`、`<style scoped>`。
4. 未落地能力必须显式标注「规划」，不得被其它模块当现状依赖；已删除的端点/文件不得再作为现状描述。
5. 配置真实值统一在 **`backend/.env`**（已被 .gitignore 忽略，不进版本库），`application.yml` 只保留 `${VAR}` 占位符，经 `spring.config.import` 自动加载（见 adr/0005）。裸占位符缺键会启动失败——`.env` 必须覆盖全部被引用的键，键位清单见 doc-devops.md §2。**历史提交中的旧密钥已暴露，建议轮换**（doc-devops.md §3）。
6. 长任务走 SSE（`fetch` + `ReadableStream` 读，未用 `EventSource`）；终态看帧不看流。
7. 在线状态唯一真相源是 LiveKit 房间事件（adr/0002）；数据库只存累计入会台账。
8. 质量门禁：前端 `npm run lint` + `npm test`；后端 `./mvnw clean install` + `./mvnw test`，全过才提交。
9. 不可逆决策 → 新增 `adr/NNNN-*.md`；多步变更 → 先落 `specs/YYYY-MM-DD-*.md`，完成后回写对应 doc-*.md。
10. 文档与代码的同步要求见 doc-conventions.md §11。

## 5. 命名与存放

规范文件 `doc-{主题}.md`；决策记录 `adr/NNNN-标题.md`（四位递增，不复用不删除）；变更规格 `specs/YYYY-MM-DD-变更名.md`；临时/过程文档不进 `docs/`。
