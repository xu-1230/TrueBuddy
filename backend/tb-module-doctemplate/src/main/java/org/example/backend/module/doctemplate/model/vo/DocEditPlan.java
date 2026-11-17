package org.example.backend.module.doctemplate.model.vo;

import lombok.Data;

import java.util.List;

/**
 * LLM 的文档编辑规划结果：助手回复内容 + 结构化编辑指令。
 * operations 为空表示本次只回答、不改文档。
 */
@Data
public class DocEditPlan {

    /** 助手回复：p / ul 内容块 */
    private List<DocEditBlock> reply;

    /** 结构化编辑指令 */
    private List<DocEditOperation> operations;

}
