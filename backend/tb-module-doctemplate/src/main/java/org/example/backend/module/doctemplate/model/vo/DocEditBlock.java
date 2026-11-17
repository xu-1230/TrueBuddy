package org.example.backend.module.doctemplate.model.vo;

import lombok.Data;

import java.util.List;

/**
 * 助手回复的内容块：段落 p / 项目列表 ul。
 * 与前端 AiChatPanel 的本地结构化渲染保持一致。
 */
@Data
public class DocEditBlock {

    /** p / ul */
    private String type;
    /** p 的正文 */
    private String text;
    /** ul 的列表项 */
    private List<String> items;

}
