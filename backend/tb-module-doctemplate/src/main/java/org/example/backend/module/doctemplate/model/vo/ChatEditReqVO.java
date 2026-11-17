package org.example.backend.module.doctemplate.model.vo;

import lombok.Data;

import java.util.List;

/**
 * 文档 AI 编辑请求。
 */
@Data
public class ChatEditReqVO {

    /** 根文件 id（doc_template_file.id，即工作区） */
    private Long rootFileId;
    /** 用户修改要求 */
    private String instruction;
    /** 选中的文档元素（上下文提示；为空表示全文） */
    private List<ChatSelection> selections;
    /** 最近的有效消息（后端优先读取持久化历史，仅在无记录时作为回退） */
    private List<ChatHistoryMessage> history;

}
