package org.example.backend.module.doctemplate.model.vo;

import lombok.Data;

/**
 * 前端在文档中选中的元素（仅作为 LLM 上下文提示，不是硬性修改范围）。
 */
@Data
public class ChatSelection {

    /** docx-mcp 定位：p:N / t:N / t:N/r:R/c:C / 单元格段落路径 */
    private String locator;
    /** 元素标签：段落 / 表格 / 单元格 / 图片… */
    private String tag;
    /** 展示文案（类型 + 文本摘要） */
    private String label;
    /** 选中元素的文本摘要（前端可见文本，后端以文档正文为准） */
    private String text;

}
