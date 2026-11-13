# doc-database · 数据库设计

> 项目：TrueBuddy ｜ 规范入口：[doc.md](./doc.md) ｜ 最近核对代码现状：2026-11-13
> 事实源：`backend/sql/`（每表一个建表文件 + 日期命名的增量补丁）；本文与 SQL 文件冲突时以 SQL 为准并同步本文。

---

## 1. 总则

| 项 | 现状 |
| --- | --- |
| 数据库 | PostgreSQL（`127.0.0.1:5432/tb`）+ pgvector 扩展 |
| 迁移方式 | **无迁移工具**：建表 SQL 手工放在 `backend/sql/`，增量变更用日期命名的补丁脚本（如 `index_and_column_fixes_20261023.sql`） |
| 外键 | 全部为**逻辑外键**（无物理约束），下文「→」表示跨表引用 |
| 通用审计列 | 业务表统一：`create_time/update_time TIMESTAMP 默认now`、`creator/updater BIGINT`、软删 `deleted BOOLEAN 默认false`（`kb_document` 例外用 `deleted_at`，`homework_file` 物理删除） |
| `newsql/public.sql` | pg_dump 导出，**只含 7 张 system 表 + pgvector 扩展对象**；且内含 `DROP TABLE`，只作参考、禁止当迁移脚本执行 |
| 表规模 | 单表 DDL 共 21 张（含 doc_template.sql 内 2 张）；恢复脚本额外 3 张（旧版批改两表 + vector_store）→ 全库 24 张 |

## 2. system 模块（RBAC 与认证）

**system_user** — 用户表：`id` 自增 PK；`username VARCHAR(30)`；`password VARCHAR(100)`；`nickname`；`remark`；`email`；`avatar`；`status INTEGER` 0=正常/1=禁用；`login_ip`；`login_date`。索引：UNIQUE(username)、UNIQUE(email)。被 system_user_role、OAuth2 令牌与各业务表 creator/updater 引用。

**system_role** — 角色表：`id`；`name`；`code VARCHAR(100)`；`status`；`remark`。UNIQUE(code)。

**system_menu** — 菜单权限表（目录/菜单/按钮三级）：`id`；`name`；`parent_id` 默认 0=根（自关联树）；`type SMALLINT` 1=目录/2=菜单/3=按钮；`path`；`component`；`permission` 权限标识；`icon`；`sort`；`status`；`visible`。idx(parent_id)。

**system_role_menu**：`role_id` → system_role，`menu_id` → system_menu；UNIQUE(role_id, menu_id)。

**system_user_role**：`user_id` → system_user，`role_id` → system_role；UNIQUE(user_id, role_id)。

**system_oauth2_access_token** — 访问令牌：`access_token`（UNIQUE）；`refresh_token`；`user_id`；`user_type`（已废弃）；`user_info JSONB`；`scopes JSONB`；`expires_time`。补丁加 idx(user_id)、idx(expires_time)。

**system_oauth2_refresh_token** — 刷新令牌：`refresh_token`（UNIQUE，一次性消费轮换）；`user_id`；`scopes JSONB`；`expires_time`。

## 3. infra 模块

**infra_file** — 文件表（MinIO 对象元数据）：`bucket VARCHAR(100)`；`path VARCHAR(512)` 对象键；`name` 原始文件名；`url`；`size`；`type` MIME。索引：UNIQUE(bucket, path) **WHERE deleted=FALSE**。被 kb_document.file_id、homework_file.file_id、doc_template_* 等逻辑引用。

## 4. doctemplate 模块

**doc_template_generation** — 生成任务表：`template_file_id` → infra_file；`space_id` → kb_space（空=不启用检索）；`description VARCHAR(500)`；`quality` 480p/720p/1080p；`doc_count`；**`status SMALLINT` 0=生成中/1=成功/2=部分成功/3=失败**；`error_msg`；`duration_ms`。idx(template_file_id)、idx(space_id)。

**doc_template_file** — 生成结果文件表：`generation_id` → doc_template_generation；`file_id` → infra_file；`name`；`type` WORD/PDF/MARKDOWN；`size`；`tag` 份数定位标签；`summary`；`sort`。idx(generation_id)、idx(file_id)。

## 5. homework 模块

**homework_task** — 批改任务表（一次文件夹上传 = 一行，软删）：`folder_name`；`file_count`；`total_size`；**`status SMALLINT` 0=待批改/1=批改中/2=成功/3=部分成功/4=失败**；`description`（批改要求，补丁列）；`space_ids VARCHAR(512)`（逗号分隔，补丁列）。idx(create_time DESC) WHERE deleted=FALSE。1:N homework_file。

**homework_file** — 作业文件表（物理删除）：`task_id` → homework_task；`file_id` → infra_file（可空）；**`relative_path VARCHAR(512)`**（形如 `课程/学号-姓名/文件.docx`）；`name`；`size`。UNIQUE(task_id, relative_path)。

**homework_grading_task / homework_grading_record** — **旧版表**，仅存于 `restore_dropped_tables_20261022.sql` 恢复脚本，现行代码不使用（批改执行已移交工作流链路，见 doc-homework.md）。

## 6. knowledgebase 模块

**kb_space** — 知识库空间：`name VARCHAR(100)`；`description`。UNIQUE(name) WHERE deleted=FALSE（删除后可复用同名）。

**kb_document** — 文档表（原文不落库，由 parse_block 聚合）：`space_id` → kb_space；`file_id` → infra_file（可空）；`name`；`type` PDF/WORD/MARKDOWN；`chunk_count`；`token_count`；**`status SMALLINT` 0=待处理/1=解析中/2=已向量化/3=失败/4=分片待确认/5=待分片/6=AI分片中/7=向量化中**；`docmind_job_id`；`parse_progress` 0-100；`parse_error`；`parse_start_at/parse_end_at/vectorize_at`；**软删用 `deleted_at TIMESTAMP`（NULL=未删除），无 deleted 布尔列**。idx(space_id, status) WHERE deleted_at IS NULL。

**kb_document_parse_block** — 解析版面块表（DocMind 产物、AI 分片输入；物理删除重建）：`document_id`；`space_id`（冗余）；`block_index` 全局阅读顺序；`page_num`；`page_block_seq`；`block_type`；`content TEXT` Markdown 正文；`text` 纯文本降级；`external_id`；`token_count`。UNIQUE(document_id, block_index)。

**kb_document_chunk** — 分片表（AI 语义分片产物；物理删除重建）：`document_id`；`space_id`（冗余）；`chunk_index` 从 0 起；`content TEXT`；`token_count`；`enabled BOOLEAN` 参与检索开关；`hit_count` 召回次数（**表结构已预留，代码尚无更新/按 enabled 过滤的逻辑**）。UNIQUE(document_id, chunk_index)。

**vector_store** — pgvector 向量存储（Spring AI PgVectorStore 默认结构，建表语句仅存在于恢复脚本；`vector_store.sql` 只建扩展）：`id UUID` 默认 `uuid_generate_v4()`；`content TEXT`；`metadata JSONB`（含 spaceId/documentId/chunkId/title，JSON 内逻辑关联）；**`embedding vector(1024)`**。索引：**`spring_ai_vector_index` HNSW（`vector_cosine_ops` 余弦距离）** + GIN(metadata jsonb_path_ops)。配置约定：dimensions=1024、hnsw、cosine_distance。

## 7. lecture 模块（模拟会议）

**lecture_meeting** — 会议表（一次开启 = 一行）：`meeting_no VARCHAR(6)`（UNIQUE WHERE deleted=FALSE）；`title`；`host_token` 主持人凭据；**`status SMALLINT` 1=进行中/2=已结束**；`start_time`；`end_time`。结束会议时后端同步删除 LiveKit 房间。

**lecture_meeting_attendee** — 参会人表（**累计入会台账，不是在线名单**；在线真相源为 LiveKit 房间事件，adr/0002；原 `online` 列已删）：`meeting_id`；`nickname`（会议内自动去重）；`attendee_token`（UNIQUE WHERE deleted=FALSE）；`role SMALLINT` 1=老师/2=学生；`join_time`；`leave_time`（NULL=未显式离开；刷新页面不补写）。idx(meeting_id, nickname) WHERE deleted=FALSE。

**lecture_meeting_message** — 会议消息表：`meeting_id`；`attendee_id`（可空）；`nickname`（冗余）；`role`；`content TEXT`；**`kind SMALLINT` 1=文字（2/3 预留语音转写 / 数字人）**。idx(meeting_id, id) WHERE deleted=FALSE。

## 8. workflow 模块（三表主键均为应用生成雪花 ID，非自增）

**workflow** — 定义表（`{nodes,edges}` 整体存 JSON，**不做版本管理**）：`name`（非纯数字 workflowId 时按此匹配）；`description`；`schema_json TEXT`；`status` 0=草稿/1=已发布。idx(name) WHERE deleted=FALSE。内置模板「标准批改作业」由启动初始化器在表为空时写入（固定 id，幂等）。

**workflow_conversation** — 会话表（前端一个聊天窗口 = 一行）：`conversation_id VARCHAR(36)` UUID（UNIQUE，/chat 续跑定位）；`workflow_id`；`workflow_name`（冗余）；`title`。idx(update_time DESC)（最近活跃排序）。

**workflow_run** — 执行实例表（会话下一次执行 = 一行；续跑复用同一行 status 4→1）：`execute_id VARCHAR(36)`（UNIQUE）；`conversation_id`；`workflow_id`；`status` 0=待运行/1=执行中/2=成功/3=失败/4=中断/5=已取消；`start_time/end_time`；`duration`。idx(conversation_id, create_time DESC)、idx(status)。**不落 schema_json / 入参 / 产出 / 错误详情**——由 SSE 事件流承载。
