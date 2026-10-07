package org.example.textflow.model.vo;

import lombok.Data;

/**
 * {@code POST /chat/conversation} 入参：新建一个会话（= 前端一个聊天窗口）。
 *
 * <p>会话在建的那一刻就绑定一个**已落库**的工作流：{@code /chat} 只接受 {@code workflow_id}，
 * 不接受内联画布定义（未保存的画布请先在画布页保存）。因此这里顺带完成「工作流是否存在」的校验。
 */
@Data
public class ConversationCreateReqVO {

    /** 工作流 id（必填，雪花 ID 字符串） */
    private String workflowId;

    /** 会话标题（可选，缺省取工作流名称） */
    private String title;

}
