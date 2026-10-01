-- =============================================================
-- TrueBuddy - 工作流定义表 workflow
-- 对应工作流模块 tb-module-workflow（实现移植自独立工程 text-flow）
-- 说明：
--   1. 流程定义（{nodes, edges}）整体存入 schema_json，不拆分节点 / 连线子表：
--      迭代节点内部的 blocks / edges 原样保真，不做结构改写；
--   2. 主键为雪花 ID（Hutool IdUtil.getSnowflakeNextId），由应用生成，不使用数据库自增；
--   3. 读取规则（WorkflowDefinitionRepository）：workflowId 为纯数字时按主键 id 命中，
--      其余按 name 命中；未命中或 JSON 解析失败时回落到模块内置的 resources/text.json；
--   4. 不做版本管理：无版本表、无历史快照，保存即覆盖当前记录；
--   5. creator / updater 为逻辑外键（system_user.id），与其它业务表一致不建物理外键约束。
-- 注意：若数据库中已存在旧结构的 workflow 表，本脚本不会重建（IF NOT EXISTS），
--       旧表数据为旧画布格式，与 text-flow 的结构不同构，需重导或依赖 classpath 回落。
-- =============================================================

CREATE TABLE IF NOT EXISTS workflow (
    id          BIGINT       NOT NULL,               -- 主键（雪花 ID）
    name        VARCHAR(100) NOT NULL,               -- 流程名称（workflowId 非纯数字时按该字段匹配）
    description VARCHAR(500),                        -- 流程说明
    schema_json TEXT         NOT NULL,               -- 流程定义 JSON：{nodes, edges}
    status      SMALLINT     NOT NULL DEFAULT 0,     -- 状态：0=草稿，1=已发布
    create_time TIMESTAMP    NOT NULL DEFAULT now(), -- 创建时间
    update_time TIMESTAMP    NOT NULL DEFAULT now(), -- 更新时间
    creator     BIGINT,                              -- 创建人 id（system_user.id）
    updater     BIGINT,                              -- 更新人 id（system_user.id）
    deleted     BOOLEAN      NOT NULL DEFAULT FALSE, -- 软删：false=否，true=是
    PRIMARY KEY (id)
);

COMMENT ON TABLE  workflow IS '工作流定义表';
COMMENT ON COLUMN workflow.name IS '流程名称：workflowId 非纯数字时按该字段匹配';
COMMENT ON COLUMN workflow.schema_json IS '流程定义 JSON：{nodes, edges}，含迭代节点内部 blocks / edges';
COMMENT ON COLUMN workflow.status IS '状态：0=草稿，1=已发布';
COMMENT ON COLUMN workflow.creator IS '创建人 id（system_user.id，逻辑外键）';

-- 按名称读取（仅约束未删除记录）
CREATE INDEX IF NOT EXISTS idx_workflow_name
    ON workflow (name) WHERE deleted = FALSE;
