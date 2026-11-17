-- =============================================================
-- TrueBuddy - 向量存储表 vector_store（pgvector）
-- 结构对齐 Spring AI PgVectorStore 默认表，维度 1024
--
-- application.yml 当前使用 spring.ai.vectorstore.pgvector.initialize-schema=true：
--   Spring AI 首次启动会自动建表，但默认 metadata 是 json，而代码写入按 ?::jsonb、
--   查询按 metadata->>/jsonpath 使用。先执行本脚本可直接得到最终的 jsonb 结构，
--   避免之后再跑 index_and_column_fixes_20261023.sql 的 ALTER。
-- 若改为 initialize-schema=false，本脚本即为唯一的建表入口。
-- =============================================================

-- 1. 安装插件扩展（须以超级用户执行，幂等）
CREATE EXTENSION IF NOT EXISTS vector;        -- pgvector：向量类型与相似度检索
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";   -- UUID 生成函数（id 默认值 uuid_generate_v4 依赖）

-- 2. 建表（metadata 用 jsonb：与 Spring AI 的写入 / 过滤算子一致）
CREATE TABLE IF NOT EXISTS vector_store (
    id        UUID DEFAULT uuid_generate_v4() NOT NULL,
    content   TEXT,
    metadata  JSONB,
    embedding vector(1024),
    PRIMARY KEY (id)
);

COMMENT ON TABLE vector_store IS '向量存储表（Spring AI PgVectorStore，1024 维，cosine）';

-- 已建表的环境把 metadata 从旧版 json 收敛为 jsonb（已是 jsonb 时跳过，避免无谓重写）
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = 'public'
           AND table_name = 'vector_store'
           AND column_name = 'metadata'
           AND data_type = 'json'
    ) THEN
        ALTER TABLE vector_store ALTER COLUMN metadata TYPE jsonb USING metadata::jsonb;
    END IF;
END
$$;

-- 3. 索引：metadata 过滤走 GIN；向量召回走 HNSW（cosine）
CREATE INDEX IF NOT EXISTS idx_vector_store_metadata_gin
    ON vector_store USING gin (metadata jsonb_path_ops);

CREATE INDEX IF NOT EXISTS spring_ai_vector_index
    ON vector_store USING hnsw (embedding vector_cosine_ops);
