package org.example.backend.module.doctemplate.model.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

/**
 * 文本改写规则：docx-mcp 的 old → new 替换项（可带作用域）
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReplacementRule {

    /** 模板中的原文（必须与模板段落文本一致） */
    private String old;

    /** 改写后的新文本 */
    @JsonProperty("new")
    private String newText;

    /**
     * 作用域（可空）：把替换限定到某一位置，避免同一原文在多处被全局替换。
     * <p>
     * 取值为 docx_replace 支持的 scope，例如：
     * <ul>
     *   <li>正文段落：{@code {"location":"p:12"}}</li>
     *   <li>表格单元格：{@code {"table":0,"row":8,"cell":0}}</li>
     *   <li>仅表格 / 仅正文：{@code {"part":"table"}} / {@code {"part":"body"}}</li>
     * </ul>
     */
    private Map<String, Object> scope;

}
