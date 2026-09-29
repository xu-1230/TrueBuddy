-- =============================================================
-- TrueBuddy - 工作流定义表 workflow
-- 对应前端 api/workflow.js 的 WorkflowVO（画布结构整存为 schema_json 字符串）
-- 说明：
--   1. 画布结构整存，不拆分节点 / 连线子表：迭代嵌套 blocks 与后端专有 inputs 配置原样保真；
--   2. 主键为雪花 ID（Hutool IdUtil.getSnowflakeNextId），由应用生成，不使用数据库自增；
--   3. 不做版本管理：无版本表、无历史快照，保存即覆盖当前记录；
--   4. creator / updater 为逻辑外键（system_user.id），与其它业务表一致不建物理外键约束。
-- =============================================================

CREATE TABLE IF NOT EXISTS workflow (
    id          BIGINT       NOT NULL,               -- 主键（雪花 ID）
    name        VARCHAR(100) NOT NULL,               -- 工作流名称
    description VARCHAR(500),                        -- 工作流说明（VO 中字段名为 desc）
    schema_json TEXT         NOT NULL,               -- 画布结构（JSON 字符串）
    status      SMALLINT     NOT NULL DEFAULT 0,     -- 状态：0=草稿，1=已发布
    create_time TIMESTAMP    NOT NULL DEFAULT now(), -- 创建时间
    update_time TIMESTAMP    NOT NULL DEFAULT now(), -- 更新时间
    creator     BIGINT,                              -- 创建人 id（system_user.id）
    updater     BIGINT,                              -- 更新人 id（system_user.id）
    deleted     BOOLEAN      NOT NULL DEFAULT FALSE, -- 软删：false=否，true=是
    PRIMARY KEY (id)
);

COMMENT ON TABLE  workflow IS '工作流定义表';
COMMENT ON COLUMN workflow.schema_json IS '画布结构（JSON 字符串）：{nodes, edges}，含迭代 blocks 与后端 inputs 配置';
COMMENT ON COLUMN workflow.status IS '状态：0=草稿，1=已发布';
COMMENT ON COLUMN workflow.creator IS '创建人 id（system_user.id，逻辑外键）';

-- 按创建人列出（仅约束未删除记录）
CREATE INDEX IF NOT EXISTS idx_workflow_creator
    ON workflow (creator) WHERE deleted = FALSE;
