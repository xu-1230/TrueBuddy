package org.example.backend.module.doctemplate.model.vo;

import lombok.Data;

/**
 * 前端会话的历史消息（仅作 LLM 上下文回退：持久化历史为空时使用）。
 */
@Data
public class ChatHistoryMessage {

    /** user / assistant */
    private String role;
    /** 已展平的文本内容 */
    private String content;

}
