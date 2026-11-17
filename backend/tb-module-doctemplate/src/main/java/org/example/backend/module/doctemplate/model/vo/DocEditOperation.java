package org.example.backend.module.doctemplate.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * LLM 产出的单条结构化编辑指令。
 *
 * <p>op 取值：replace / setCell / insertParagraph / deleteParagraph。
 * 定位统一使用 docx-mcp 的 p:N / t:N / t:N/r:R/c:C（及单元格段落路径）。
 */
@Data
public class DocEditOperation {

    /** replace / setCell / insertParagraph / deleteParagraph */
    private String op;

    /** 目标定位：p:N / t:N / t:N/r:R/c:C / t:N/r:R/c:C/p:P */
    private String locator;

    /** replace 的原文（必须逐字取自文档清单） */
    @JsonProperty("old")
    private String oldText;

    /** replace 的新文 */
    @JsonProperty("new")
    private String newText;

    /** setCell 的写入文本（支持 \n 多段） */
    private String text;

    /** insertParagraph 的后置锚点段落 */
    private String after;

    /** insertParagraph 的前置锚点段落 */
    private String before;

    /** insertParagraph 的格式来源段落 */
    private String styleFrom;

}
