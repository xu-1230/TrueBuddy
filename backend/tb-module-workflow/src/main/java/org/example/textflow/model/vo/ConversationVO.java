package org.example.textflow.model.vo;

import lombok.Data;

/**
 * 会话出参（建会话与会话列表共用）。
 */
@Data
public class ConversationVO {

    /** 会话编号（UUID，对外，后续 /chat 全程回传它） */
    private String conversationId;

    /** 会话绑定的工作流 id */
    private String workflowId;

    /** 流程名称（冗余，列表直接展示，避免联表） */
    private String workflowName;

    /** 会话标题 */
    private String title;

    private String createTime;

    /** 最近活跃时间（每次新建执行时刷新，列表按它倒序） */
    private String updateTime;

}
