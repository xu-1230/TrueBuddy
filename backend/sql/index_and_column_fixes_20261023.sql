-- 2026-10-23 索引与列补齐
--
-- 每条都可重复执行（IF NOT EXISTS / DROP IF EXISTS），跑第二遍不会报错。
-- 依据：backend/sql 的 DDL 与线上 tb 库（PostgreSQL 18.4）实测对照。
-- 注意：本文件只改 DDL，不动任何业务数据。

BEGIN;

-- ---------------------------------------------------------------------------
-- 1. homework_task 补列
--    代码里 HomeworkTaskMapper.updateGradeConfig 已经在写 description / space_ids，
--    而线上表没有这两列 —— POST /homework/grade 一进来就会报错。
-- ---------------------------------------------------------------------------
ALTER TABLE homework_task ADD COLUMN IF NOT EXISTS description VARCHAR(500);
ALTER TABLE homework_task ADD COLUMN IF NOT EXISTS space_ids   VARCHAR(512);

COMMENT ON COLUMN homework_task.description IS 'AI 描述说明（批改要求），工作流开始节点按 taskId 读取';
COMMENT ON COLUMN homework_task.space_ids   IS '知识库 id 列表（逗号分隔），工作流开始节点按 taskId 读取为 know';

-- ---------------------------------------------------------------------------
-- 2. 删掉纯左前缀的冗余索引
--    idx_parse_block_doc(document_id) 是 uk_parse_block_doc_index(document_id, block_index)
--    的严格左前缀，唯一键已经能满足按 document_id 的查找与删除，留着只是写放大。
-- ---------------------------------------------------------------------------
DROP INDEX IF EXISTS idx_parse_block_doc;

-- ---------------------------------------------------------------------------
-- 3. token 表按 user_id 的清理路径建索引
--    deleteByUserId 只有 pkey 和 uk(token) 可用，走的是顺序扫描。
--    另外这两张表没有任何过期清理：实测 16 条 access token 已全部过期仍留在表里。
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_system_oauth2_access_token_user_id
    ON system_oauth2_access_token (user_id);
CREATE INDEX IF NOT EXISTS idx_system_oauth2_refresh_token_user_id
    ON system_oauth2_refresh_token (user_id);
-- 将来加过期清理时的扫描列
CREATE INDEX IF NOT EXISTS idx_system_oauth2_access_token_expires_time
    ON system_oauth2_access_token (expires_time);

-- ---------------------------------------------------------------------------
-- 4. 参会人重名检查索引（并替换被它覆盖的单列索引）
--    selectByMeetingIdAndNickname 在入会的重试循环里，属于 /join 热路径。
--    新建的 (meeting_id, nickname) 以 meeting_id 打头，仍能服务 selectListByMeetingId /
--    countByMeetingId / markAllLeft，故原来的 (meeting_id) 局部索引可以删掉。
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_lecture_meeting_attendee_meeting_nickname
    ON lecture_meeting_attendee (meeting_id, nickname) WHERE deleted = FALSE;
DROP INDEX IF EXISTS idx_lecture_meeting_attendee_meeting;

-- ---------------------------------------------------------------------------
-- 5. vector_store.metadata 由 json 改为 jsonb，并加 GIN
--    Spring AI 2.0.0-M3 的 PgVectorStore 建表模板写的是 `metadata json`，
--    但它的 INSERT 用 `?::jsonb`、过滤与删除用 `metadata::jsonb @@ '…'::jsonpath`。
--    列本来就是 jsonb 才与写入侧一致，也省掉每次查询对每一行做 json->jsonb 转换；
--    pg_amop 实测 jsonb_path_ops 里有 @@ 算子（PG17 起支持），所以 GIN 能用上。
--    表当前 0 行，ALTER 不涉及重写。
-- ---------------------------------------------------------------------------
ALTER TABLE vector_store ALTER COLUMN metadata TYPE jsonb USING metadata::jsonb;
CREATE INDEX IF NOT EXISTS idx_vector_store_metadata_gin
    ON vector_store USING gin (metadata jsonb_path_ops);

COMMIT;

-- ---------------------------------------------------------------------------
-- 执行后自查：status=2（已完成向量化）但向量数与分片数不一致的文档。
-- 这类文档在 searchBySpace 里会返回空串并被静默忽略，需要老师重新点一次向量化。
-- ---------------------------------------------------------------------------
-- SELECT d.id, d.name, d.chunk_count,
--        (SELECT count(*) FROM vector_store v
--          WHERE v.metadata ->> 'documentId' = d.id::text) AS vectors
--   FROM kb_document d
--  WHERE d.deleted_at IS NULL AND d.status = 2
--    AND (SELECT count(*) FROM vector_store v
--          WHERE v.metadata ->> 'documentId' = d.id::text) <> d.chunk_count;
