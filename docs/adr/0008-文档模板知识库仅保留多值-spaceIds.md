# 文档模板知识库选择只保留多值 spaceIds（删除单值 spaceId）

> 2026-11-17 ｜ 相关：[docs/specs/2026-11-17-文档模板知识库多选.md](../specs/2026-11-17-文档模板知识库多选.md)、[docs/specs/2026-11-17-文档模板知识库仅保留多值.md](../specs/2026-11-17-文档模板知识库仅保留多值.md)、[docs/doc-doctemplate.md](../doc-doctemplate.md)、[docs/doc-api.md](../doc-api.md)、[docs/doc-database.md](../doc-database.md)

## 背景

知识库多选首版为了平滑过渡，在请求体与表结构里保留了单值 `spaceId`：

- `GenerateReqVO` 同时有 `List<String> spaceIds` 与 `String spaceId`，后者作兼容回退；
- `doc_template_generation` 同时有 `space_id`（第一个 id）与 `space_ids`（完整列表），历史展示在 `space_ids` 为空时回退 `space_id`。

用户明确要求「全部改为 `List<String> spaceIds`，不保留原来的单值 `spaceId`」。保留两套形状会让空值语义、历史回退与外部调用口径长期分叉，故立此存照。

## 决策

1. **请求只保留 `spaceIds`**：删除 `GenerateReqVO.spaceId`，不再有单值回退；旧请求携带的 `spaceId` 由 Jackson 忽略，不报错。
2. **存储只保留 `space_ids`**：存量行先回填 `space_ids = CAST(space_id AS TEXT)`，随后删除 `space_id` 列与 `idx_doc_template_generation_space_id` 索引；新环境建表不再有 `space_id`。
3. **历史只读 `space_ids`**：删除「`space_ids` 为空时回退 `space_id`」的逻辑；存量数据由迁移回填，不依赖回退。
4. **不做显式 400 拒绝**：旧页面/旧脚本继续发 `spaceId` 时静默忽略，生成照常但检索增强为空。若未来需要硬失败，另立变更。

## 为什么不是别的

- **保留请求回退**：两套字段长期共存，调用方无法从契约判断哪套是真相；用户已明确不要。
- **保留 DB `space_id` 作为 first-id**：`space_ids` 已包含完整信息，first-id 只是冗余；保留会让「唯一真相源」继续分裂，且删列成本只在一次迁移。
- **对旧 `spaceId` 返回 400**：需要保留一个废弃字段或引入未知字段拦截；用户选择彻底移除，静默忽略是可接受的代价。

## 后果

- **旧前端/旧脚本**：继续发 `spaceId` 时请求成功但不启用检索（静默降级），刷新到新前端后恢复。
- **部署顺序**：新后端代码（只写 `space_ids`）必须先于或同时于「删除 `space_id` 列」的迁移上线；旧后端在新库上插入会因缺列失败。回滚时需重新加列并从 `space_ids` 首段回填。
- **存量历史**：迁移先回填再删列，旧记录的知识库名称不会丢。
- **接口契约**：`/doc-template/generate` 的请求只认 `spaceIds`；文档与测试同步删除单值说明。
