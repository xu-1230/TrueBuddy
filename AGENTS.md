# AGENTS.md — 智能体工作入口

本仓库的规范唯一权威来源是 `docs/`，总入口为 **[docs/doc.md](docs/doc.md)**（项目概述、模块地图、文档地图、全局硬约束）。**一切以代码现状为唯一标准**：文档与代码冲突时以代码为准，并在对应文档按「现状 / 规范要求 / 待整改」三段式如实记录。

## 阅读顺序

1. `docs/doc.md`（必读，总控）
2. `docs/doc-conventions.md`（工程规范：编码 / 组件 / 路由 / 状态 / 测试 / 文档书写）
3. 所改业务模块的 `docs/doc-{模块}.md`；接口细节 `docs/doc-api.md`；表结构 `docs/doc-database.md`
4. 分端细则：[frontend/AGENT - 前端技术规约.md](frontend/AGENT%20-%20前端技术规约.md)、[backend/AGENT - 后端技术规约.md](backend/AGENT%20-%20后端技术规约.md)（上游文档即 `docs/doc.md`）

## 变更流程

- **不可逆决策**（放弃某能力、改变唯一真相源、切断某条兼容路径）→ 新增 `docs/adr/NNNN-*.md`（四位序号递增，不删除、不复用、不回溯修改），并在受影响文档回链。
- **多步变更** → 先落 `docs/specs/YYYY-MM-DD-*.md`（范围 / 口径 / 验收项），完成后把结论回写进对应 doc-*.md，spec 本身保留不改。
- 接口 / 路由 / 依赖 / 配置 / 测试变化 → 按 `docs/doc-conventions.md` §11 的同步表更新对应文档。
- 临时与过程文档不进 `docs/`（放 `.repro/`、`backend/tmp/` 等已忽略目录）。
- 未落地的能力在文档中必须显式标注「规划」，不得当作现状依赖。
