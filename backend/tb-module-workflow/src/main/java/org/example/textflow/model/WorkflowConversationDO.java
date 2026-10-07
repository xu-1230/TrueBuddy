package org.example.textflow.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作流会话（{@code workflow_conversation} 表一行）。
 *
 * <p>一个会话 = 前端一个「聊天窗口」，会话绑定一个工作流，窗口内的多次执行共用该绑定；
 * {@code updateTime} 每次新建执行时 touch，用于会话列表按活跃度倒序。
 */
@Data
public class WorkflowConversationDO {

    /** 主键（雪花 ID） */
    private String id;

    /** 会话编号（UUID，对外，= 聊天窗口编号） */
    private String conversationId;

    /** 逻辑外键 workflow.id（会话绑定的工作流） */
    private String workflowId;

    /** 流程名称（冗余，便于展示） */
    private String workflowName;

    /** 会话标题 */
    private String title;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 创建人 id（system_user.id） */
    private Long creator;

}
