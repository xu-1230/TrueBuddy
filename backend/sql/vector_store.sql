-- =============================================================
-- TrueBuddy - 向量存储表 vector_store（pgvector）
-- 结构对齐 Spring AI PgVectorStore 默认表，维度 1024
-- 使用本表需在 application.yml 配置：
--   spring.ai.vectorstore.pgvector.initialize-schema=false
--   spring.ai.vectorstore.pgvector.dimensions=1024
--   spring.ai.vectorstore.pgvector.index-type=hnsw
--   spring.ai.vectorstore.pgvector.distance-type=cosine_distance
-- =============================================================

-- 1. 安装插件扩展（须以超级用户执行，幂等）
CREATE EXTENSION IF NOT EXISTS vector;        -- pgvector：向量类型与相似度检索
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";   -- UUID 生成函数（id 默认值 uuid_generate_v4 依赖）
