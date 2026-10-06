-- =============================================================
-- TrueBuddy - 工作流执行实例表 workflow_run
-- 对应工作流模块 tb-module-workflow（执行侧落库）
-- 说明：
--   1. 一次执行（execute_id）落一行，记录「属于哪个会话、跑的哪个流程、什么状态、耗时多久」；
--   2. 层级：workflow_conversation（会话 / 聊天窗口）1 : N workflow_run（执行实例）；
--      execute_id 为本次执行编号（UUID，对外），conversation_id 为所属会话编号；
--      「续跑」复用同一条执行记录（status 4 → 1），不新增行；终态后再次发起才新增行；
--   3. 主键 id 为雪花 ID（Hutool IdUtil.getSnowflakeNextId），由应用生成，不使用数据库自增；
--   4. conversation_id / workflow_id 均为逻辑外键，与其它业务表一致不建物理外键约束；
--      workflow_name 为冗余字段，便于列表直接展示、避免联表；
--   5. 不存画布定义、入参、产出与错误详情（schema_json / input_params /
--      output_result / error_message 均未落库）：续跑时按 workflow_id 重新加载定义，
--      执行详情由实时事件流（SSE）承载；
--   6. 软删，与其它业务表保持一致。
-- =============================================================

CREATE TABLE IF NOT EXISTS workflow_run (
    id              BIGINT       NOT NULL,               -- 主键（雪花 ID）
    execute_id      VARCHAR(36)  NOT NULL,               -- 本次执行编号（UUID，对外）
    conversation_id VARCHAR(36)  NOT NULL,               -- 所属会话编号（逻辑外键 workflow_conversation.conversation_id）
    workflow_id     BIGINT,                              -- 逻辑外键 workflow.id
    workflow_name   VARCHAR(100),                        -- 流程名称（冗余，便于展示）
    status          SMALLINT     NOT NULL DEFAULT 0,     -- 状态：0=待运行，1=执行中，2=成功，3=失败，4=中断，5=已取消
    start_time      TIMESTAMP,                           -- 执行开始时间
    end_time        TIMESTAMP,                           -- 执行结束时间
    duration        BIGINT,                              -- 执行耗时（毫秒）
    create_time     TIMESTAMP    NOT NULL DEFAULT now(), -- 创建时间
    update_time     TIMESTAMP    NOT NULL DEFAULT now(), -- 更新时间
    creator         BIGINT,                              -- 创建人 id（system_user.id）
    updater         BIGINT,                              -- 更新人 id（system_user.id）
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE, -- 软删：false=否，true=是
    PRIMARY KEY (id)
);

COMMENT ON TABLE  workflow_run IS '工作流执行实例表（会话下一次执行一行）';
COMMENT ON COLUMN workflow_run.execute_id IS '本次执行编号（UUID，对外，与 SSE 事件 executeId 一致）';
COMMENT ON COLUMN workflow_run.conversation_id IS '所属会话编号（逻辑外键 workflow_conversation.conversation_id）';
COMMENT ON COLUMN workflow_run.workflow_id IS '逻辑外键 workflow.id';
COMMENT ON COLUMN workflow_run.workflow_name IS '流程名称（冗余，便于展示）';
COMMENT ON COLUMN workflow_run.status IS '状态：0=待运行，1=执行中，2=成功，3=失败，4=中断，5=已取消';
COMMENT ON COLUMN workflow_run.duration IS '执行耗时（毫秒）';
COMMENT ON COLUMN workflow_run.creator IS '创建人 id（system_user.id，逻辑外键）';

-- 执行编号全局唯一（/chat 续跑、轮询兜底按 executeId 命中）
CREATE UNIQUE INDEX IF NOT EXISTS uk_workflow_run_execute_id
    ON workflow_run (execute_id);

-- 按会话查执行历史（仅约束未删除记录）
CREATE INDEX IF NOT EXISTS idx_workflow_run_conversation_id
    ON workflow_run (conversation_id, create_time DESC) WHERE deleted = FALSE;

-- 按状态轮询（执行中 / 中断等）
CREATE INDEX IF NOT EXISTS idx_workflow_run_status
    ON workflow_run (status) WHERE deleted = FALSE;

-- 可选增强：一个会话同时只能有一条「活动」执行（1 执行中 / 4 中断），
-- 用于防止同一会话并发提交 /chat 产生两条并行执行。
-- CREATE UNIQUE INDEX IF NOT EXISTS uk_workflow_run_active
--     ON workflow_run (conversation_id) WHERE deleted = FALSE AND status IN (1, 4);
