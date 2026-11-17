package org.example.backend.module.doctemplate.model.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 聊天记录条目：用户指令 / 助手回复 / 系统提示。
 */
@Data
public class ChatMessageRespVO {

    /** 消息 id（服务端自增） */
    private Long id;
    /** user / assistant / system */
    private String role;
    /** 扁平文本（assistant 可优先使用 blocks） */
    private String content;
    /** 助手结构化回复块 */
    private List<DocEditBlock> blocks;
    /** 用户消息的选区上下文 */
    private List<ChatSelection> selections;
    /** 本次编辑产生的版本 id；无编辑为 null */
    private Long versionId;
    /** 撤回后是否失效 */
    private Boolean superseded;
    /** 是否失败回复 */
    private Boolean error;
    /** 创建时间 */
    private LocalDateTime createTime;

}
