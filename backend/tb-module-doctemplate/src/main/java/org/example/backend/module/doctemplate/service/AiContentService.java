package org.example.backend.module.doctemplate.service;

import org.example.backend.module.doctemplate.model.vo.DocPlanResult;
import org.example.backend.module.doctemplate.model.vo.ReplacementRule;

import java.util.List;
import java.util.Map;

/**
 * LLM 内容服务：规划产出份数与主题定位 + 产出「按位置区分」的改写规则 + 自检缺口补规则
 */
public interface AiContentService {

    /**
     * 规划阶段：依据描述、模板骨架、段落清单与知识库检索内容，决定产出几份及每份的主题定位与自检关键词。
     *
     * @param description       用户 AI 描述说明
     * @param templateOutline   模板骨架 JSON
     * @param tablesDigest      表格与嵌套表格段落清单（段落路径 → 原文）
     * @param textDigest        正文段落清单（位置 → 原文）
     * @param ragContext        知识库检索内容（可为空）
     * @param maxDocCount       份数上限
     * @return 规划结果（份数已按上限截断，且至少 1 份）
     */
    DocPlanResult plan(String description, String templateOutline, String tablesDigest,
                       String textDigest, String ragContext, int maxDocCount);

    /**
     * 改写阶段：针对某一份文档计划，产出 old → new 文本改写规则
     * （old 必须逐字取自清单；重复文本须带 scope 按位置区分，且各位置的 new 可不同）。
     *
     * @param textDigest   正文段落清单
     * @param tablesDigest 表格与嵌套表格段落清单
     * @param item         该份文档计划（含主题定位与自检关键词）
     * @param ragContext   知识库检索内容（可为空）
     * @return 改写规则列表（可能为空，表示保持原文）
     */
    List<ReplacementRule> buildReplacements(String textDigest, String tablesDigest,
                                            DocPlanResult.DocItem item, String ragContext);

    /**
     * 自检修复阶段：针对自检暴露的关键词缺口，补充一批替换规则。
     *
     * @param textDigest        正文段落清单
     * @param tablesDigest      表格与嵌套表格段落清单
     * @param item              该份文档计划
     * @param missingKeywords   生成结果中缺失的新主题关键词
     * @param forbiddenCounts   仍残留的旧主题关键词及次数
     * @return 补充规则列表（无缺口时为空）
     */
    List<ReplacementRule> buildFixups(String textDigest, String tablesDigest, DocPlanResult.DocItem item,
                                      List<String> missingKeywords, Map<String, Integer> forbiddenCounts);

}
