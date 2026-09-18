-- =============================================================
-- TrueBuddy 数据库初始化脚本（只需要运行一次）
-- 数据库：PostgreSQL
-- 连接库：tb（对应 tb-server/src/main/resources/application.yml）
-- =============================================================

-- 1. 创建数据库（若尚未创建）
--    说明：PostgreSQL 不支持 CREATE DATABASE IF NOT EXISTS，且 CREATE DATABASE
--    不能与其它语句放在同一事务中，因此需在 psql 中以超级用户单独执行：
--        CREATE DATABASE tb;
--    若连接的目标库已经存在，则跳过此步骤，直接执行下面的扩展创建。

-- 2. 启用扩展（幂等，可重复执行）
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";   -- UUID 生成函数
CREATE EXTENSION IF NOT EXISTS vector;        -- pgvector：向量存储与相似度检索