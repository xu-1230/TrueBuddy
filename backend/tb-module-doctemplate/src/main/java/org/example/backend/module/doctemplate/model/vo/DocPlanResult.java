package org.example.backend.module.doctemplate.model.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * LLM 文档规划结果：份数 + 每份的主题定位、改写要点与自检关键词
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DocPlanResult {

    /** 计划产出份数（与 docs 大小一致，作为自校验字段） */
    private Integer totalCount;
    /** 计划列表 */
    private List<DocItem> docs;

    /**
     * 单份文档计划
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DocItem {

        /** 序号（1 起） */
        private Integer index;
        /** 定位标签：用户给出的并列项原话，或风格名（如 系统分析 / 复刻 / 精简） */
        private String tag;
        /** 该份文档标题（用于文件命名与卡片标题） */
        private String title;
        /** 内容摘要（卡片副标题） */
        private String summary;
        /** 改写要点：告知后续改写步骤应如何调整内容 */
        private String focus;
        /** 本次目标名称（沿用用户原话口径：可以是主题 / 章节 / 课程 / 模块 / 方向 / 场景…），多份场景下每份不同 */
        private String theme;
        /** 该份对应课程名称（封面字段；用户未给出则为空字符串） */
        private String courseName;
        /** 该份对应章节名称（封面字段；用户未给出则为空字符串） */
        private String chapterName;
        /** 该份面向专业（封面字段；用户未给出则为空字符串） */
        private String major;
        /** 新主题关键词（自检用：生成结果中应齐全） */
        private List<String> requiredKeywords;
        /** 旧主题关键词（自检用：生成结果中应归零） */
        private List<String> forbiddenKeywords;

    }

}
