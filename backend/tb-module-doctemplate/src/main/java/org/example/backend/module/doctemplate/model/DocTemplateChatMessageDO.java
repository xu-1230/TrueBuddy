package org.example.backend.module.doctemplate.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档 AI 聊天消息 DO（表 doc_template_chat_message）
 * <p>
 * 聊天记录按根文件（工作区）归属；助手消息通过 versionId 关联编辑版本，
 * 撤回后目标消息及其后消息置 superseded。
 */
@Data
public class DocTemplateChatMessageDO {

    /** 主键（自增） */
    private Long id;
    /** 工作区（doc_template_file.id） */
    private Long rootFileId;
    /** 消息角色：user / assistant / system */
    private String role;
    /** 扁平文本（LLM 上下文 / 兜底展示） */
    private String content;
    /** 助手结构化回复块 JSON（p / ul） */
    private String blocks;
    /** 用户消息的选区上下文 JSON */
    private String selections;
    /** 本次编辑产生的版本 id；无编辑为 null */
    private Long versionId;
    /** 撤回后是否失效 */
    private Boolean superseded;
    /** 是否失败回复 */
    private Boolean error;
    /** 发送人 id */
    private Long creator;
    /** 创建时间 */
    private LocalDateTime createTime;

}
