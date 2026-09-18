package org.example.backend.module.doctemplate.model.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * docx_replace 的结构化变更报告（dryRun 与落盘共用）
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DocxReplaceReport {

    /** 是否预演（true 表示未写盘） */
    private Boolean dryRun;
    /** 命中规则数 */
    private Integer appliedRules;
    /** 模板来源 */
    private String source;
    /** 落盘路径（dryRun 时为 null） */
    private String outputPath;
    /** 失败信息（成功时为空） */
    private String error;
    /** 每条规则的命中明细 */
    private List<RuleResult> results;
    /** 未命中的规则 */
    private List<MissedRule> missedRules;
    /** 顺序 / 包含关系等提示 */
    private List<String> warnings;

    /** 单条规则的命中结果 */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RuleResult {

        private String old;
        @JsonProperty("new")
        private String newText;
        private Map<String, Object> scope;
        /** 命中次数 */
        private Integer matched;
        /** 是否未命中 */
        private Boolean missed;
        /** 命中位置明细（location 可用于按位置展开） */
        private List<Occurrence> occurrences;

        public int matchedCount() {
            return matched == null ? 0 : matched;
        }

    }

    /** 单处命中位置 */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Occurrence {

        /** 段落路径，如 p:12 或 t:3/r:2/c:1/p:0（可直接作为 scope.location） */
        private String location;
        /** body / table */
        private String part;
        private Integer paragraphIndex;
        private Integer tableIndex;
        private Integer row;
        private Integer col;
        private Integer charOffset;
        /** 命中的原文 */
        private String hit;

    }

    /** 未命中的规则 */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MissedRule {

        private String old;

    }

}
