package org.example.backend.module.doctemplate.model.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * docx_validate 的文档体检结果
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DocxValidateResult {

    /** 总体是否通过（无占位符残留 / 无旧词残留 / 无缺失关键词 / 页面设置完整） */
    private Boolean ok;
    /** 失败信息（成功时为空） */
    private String error;
    /** 结构统计：paragraphs / paragraphs_all / tables / cells */
    private Map<String, Integer> structure;
    /** 占位符残留数量 */
    private Integer placeholderCount;
    /** 空单元格数量 */
    private Integer emptyCellCount;
    /** 关键词出现次数（required_keywords） */
    private Map<String, Integer> keywordCounts;
    /** 计数为 0 的必备关键词（新主题词缺失） */
    private List<String> missingKeywords;
    /** 仍存在的禁用关键词及其次数（旧主题词残留） */
    private Map<String, Integer> forbiddenKeywordCounts;
    /** 页面设置是否完整 */
    private Boolean pageSetupOk;
    /** 样式数量 */
    private Integer styleCount;

}
