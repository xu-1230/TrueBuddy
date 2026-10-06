-- =============================================================
-- TrueBuddy - 工作流会话表 workflow_conversation
-- 说明：
--   1. 一个会话 = 前端一个「聊天窗口」，conversation_id 为窗口编号（UUID，对外）；
--   2. 会话绑定一个工作流（workflow_id），窗口内的多次执行共用该绑定；
--   3. 一个会话下可有多次执行（workflow_run）：新建 → 中断 → 续跑 → … → 终态；
--   4. 主键 id 为雪花 ID（Hutool IdUtil.getSnowflakeNextId），由应用生成，不使用数据库自增；
--   5. workflow_id 为逻辑外键（workflow.id），与其它业务表一致不建物理外键约束；
--   6. 软删，与其它业务表保持一致。
-- =============================================================

CREATE TABLE IF NOT EXISTS workflow_conversation (
    id              BIGINT       NOT NULL,               -- 主键（雪花 ID）
    conversation_id VARCHAR(36)  NOT NULL,               -- 会话编号（UUID，对外，= 聊天窗口编号）
    workflow_id     BIGINT,                              -- 逻辑外键 workflow.id（会话绑定的工作流）
    workflow_name   VARCHAR(100),                        -- 流程名称（冗余，便于展示）
    title           VARCHAR(200),                        -- 会话标题（一般取首条用户输入截断）
    create_time     TIMESTAMP    NOT NULL DEFAULT now(), -- 创建时间
    update_time     TIMESTAMP    NOT NULL DEFAULT now(), -- 更新时间（每次新建执行时 touch，用于会话列表按活跃排序）
    creator         BIGINT,                              -- 创建人 id（system_user.id）
    updater         BIGINT,                              -- 更新人 id（system_user.id）
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE, -- 软删：false=否，true=是
    PRIMARY KEY (id)
);

COMMENT ON TABLE  workflow_conversation IS '工作流会话表（前端一个聊天窗口一行）';
COMMENT ON COLUMN workflow_conversation.conversation_id IS '会话编号（UUID，对外，= 聊天窗口编号）';
COMMENT ON COLUMN workflow_conversation.workflow_id IS '逻辑外键 workflow.id（会话绑定的工作流）';
COMMENT ON COLUMN workflow_conversation.workflow_name IS '流程名称（冗余，便于展示）';
COMMENT ON COLUMN workflow_conversation.title IS '会话标题';
COMMENT ON COLUMN workflow_conversation.creator IS '创建人 id（system_user.id，逻辑外键）';

-- 会话编号全局唯一（/chat 续跑按它定位会话）
CREATE UNIQUE INDEX IF NOT EXISTS uk_workflow_conversation_id
    ON workflow_conversation (conversation_id);

-- 会话列表按最近活跃排序（仅约束未删除记录）
CREATE INDEX IF NOT EXISTS idx_workflow_conversation_active
    ON workflow_conversation (update_time DESC) WHERE deleted = FALSE;
