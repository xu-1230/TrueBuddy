package org.example.backend.module.doctemplate.service.Impl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.framework.common.exception.ServiceException;
import org.example.backend.module.doctemplate.config.DocTemplateProperties;
import org.example.backend.module.doctemplate.model.vo.DocPlanResult;
import org.example.backend.module.doctemplate.model.vo.ReplacementRule;
import org.example.backend.module.doctemplate.prompt.AiPromptConstants;
import org.example.backend.module.doctemplate.service.AiContentService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.example.backend.framework.common.exception.util.ServiceExceptionUtil.exception;
import static org.example.backend.module.doctemplate.enums.ErrorCodeConstants.AI_RESPONSE_INVALID;

/**
 * LLM 内容服务实现：ChatClient 调用 + JSON 解析（剥离 markdown 代码块与解释性文字）
 */
@Slf4j
@Service
public class AiContentServiceImpl implements AiContentService {

    /** 提示词中模板骨架的最大字符数 */
    private static final int MAX_OUTLINE_CHARS = 4000;
    /** 提示词中检索内容的最大字符数 */
    private static final int MAX_RAG_CHARS = 4000;

    /** docx_replace 支持的 scope 键白名单（非法键仅丢弃 scope，不丢弃整条规则） */
    private static final Set<String> ALLOWED_SCOPE_KEYS = Set.of(
            "location", "paragraph", "table", "row", "col", "cell", "part",
            "after", "before", "occurrence");

    /** 段落路径形态：t:x/r:y/c:z/p:n / p:n */
    private static final String CELL_PATH_PATTERN = "t:\\d+/r:\\d+/c:\\d+";
    private static final String PARAGRAPH_PATH_PATTERN = "t:\\d+/r:\\d+/c:\\d+/p:\\d+";
    private static final String BODY_PATH_PATTERN = "p:\\d+";
    private static final String TABLE_PATH_PATTERN = "t:\\d+";

    private final ChatClient docTemplateChatClient;
    private final DocTemplateProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public AiContentServiceImpl(ChatClient docTemplateChatClient, DocTemplateProperties properties) {
        this.docTemplateChatClient = docTemplateChatClient;
        this.properties = properties;
    }

    @Override
    public DocPlanResult plan(String description, String templateOutline, String tablesDigest,
                              String textDigest, String ragContext, int maxDocCount) {
        String user = AiPromptConstants.PLANNER_USER.formatted(
                maxDocCount,
                nullToEmpty(description),
                truncate(templateOutline, MAX_OUTLINE_CHARS, "模板骨架"),
                truncate(tablesDigest, properties.getMaxDigestChars(), "表格段落清单"),
                truncate(textDigest, properties.getMaxTextChars(), "正文段落清单"),
                truncate(ragContext, MAX_RAG_CHARS, "检索内容"));
        String response = chat(AiPromptConstants.PLANNER_SYSTEM, user);

        DocPlanResult result;
        try {
            result = objectMapper.readValue(extractJson(response, '{'), DocPlanResult.class);
        } catch (Exception e) {
            log.warn("[plan] 规划结果解析失败，降级为单份生成: {}", e.getMessage());
            return fallbackPlan(description);
        }
        return normalizePlan(result, description, maxDocCount);
    }

    @Override
    public List<ReplacementRule> buildReplacements(String textDigest, String tablesDigest,
                                                   DocPlanResult.DocItem item, String ragContext) {
        String user = AiPromptConstants.REWRITE_USER.formatted(
                itemField(item.getTheme(), item.getTag()),
                itemField(item.getCourseName(), ""),
                itemField(item.getChapterName(), ""),
                itemField(item.getMajor(), ""),
                nullToEmpty(item.getFocus()),
                joinKeywords(item.getRequiredKeywords()),
                joinKeywords(item.getForbiddenKeywords()),
                truncate(tablesDigest, properties.getMaxDigestChars(), "表格段落清单"),
                truncate(textDigest, properties.getMaxTextChars(), "正文段落清单"),
                truncate(ragContext, MAX_RAG_CHARS, "检索内容"));
        String response = chat(AiPromptConstants.REWRITE_SYSTEM, user);
        return parseRules(response, item, "buildReplacements");
    }

    @Override
    public List<ReplacementRule> buildFixups(String textDigest, String tablesDigest, DocPlanResult.DocItem item,
                                             List<String> missingKeywords, Map<String, Integer> forbiddenCounts) {
        String user = AiPromptConstants.FIXUP_USER.formatted(
                itemField(item.getTheme(), item.getTag()),
                itemField(item.getCourseName(), ""),
                itemField(item.getChapterName(), ""),
                itemField(item.getMajor(), ""),
                joinKeywords(missingKeywords),
                joinCounts(forbiddenCounts),
                truncate(tablesDigest, properties.getMaxDigestChars(), "表格段落清单"),
                truncate(textDigest, properties.getMaxTextChars(), "正文段落清单"));
        String response = chat(AiPromptConstants.FIXUP_SYSTEM, user);
        return parseRules(response, item, "buildFixups");
    }

    // ========== 私有方法 ==========

    /** 解析模型返回的规则数组，并做清洗与 scope 规范化 */
    private List<ReplacementRule> parseRules(String response, DocPlanResult.DocItem item, String stage) {
        try {
            List<ReplacementRule> rules = objectMapper.readValue(
                    extractJson(response, '['),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, ReplacementRule.class));
            List<ReplacementRule> cleaned = new ArrayList<>();
            int droppedByNewline = 0;
            for (ReplacementRule rule : rules) {
                if (rule == null || rule.getOld() == null || rule.getOld().isBlank()) {
                    continue;
                }
                // 跨行 old 无法被 docx_replace 的段落级匹配命中，直接丢弃
                if (rule.getOld().contains("\n") || rule.getOld().contains("\r")) {
                    droppedByNewline++;
                    continue;
                }
                if (rule.getNewText() != null && rule.getNewText().equals(rule.getOld())) {
                    continue;
                }
                rule.setScope(sanitizeScope(rule.getScope(), rule.getOld()));
                cleaned.add(rule);
            }
            if (droppedByNewline > 0) {
                log.warn("[{}] 丢弃跨段落 old 规则 {} 条（old 必须同段落内连续）", stage, droppedByNewline);
            }
            if (cleaned.isEmpty()) {
                log.warn("[{}] 未获得有效改写规则（tag={}），该份将与模板原文一致", stage, item.getTag());
            } else {
                long scoped = cleaned.stream().filter(rule -> rule.getScope() != null).count();
                log.info("[{}] 规则就绪: tag={}, 规则数={}, 带 scope={}", stage, item.getTag(), cleaned.size(), scoped);
            }
            return cleaned;
        } catch (Exception e) {
            log.warn("[{}] 规则解析失败，该份保持现有内容: {}", stage, e.getMessage());
            return List.of();
        }
    }

    /**
     * scope 白名单校验与规范化：
     * <ul>
     *   <li>只保留 docx_replace 支持的键；</li>
     *   <li>{@code location} 为单元格路径（{@code t:0/r:0/c:1}）时补 {@code /p:0}
     *       —— 实测单元格级 location 命中 0 处，必须落到段落；</li>
     *   <li>{@code location} 为纯表格路径（{@code t:0}）时转换为 {@code table} 维度；</li>
     *   <li>非法形态丢弃 scope（整条规则保留，落盘前会由 dryRun 按命中位置自动补 scope）。</li>
     * </ul>
     */
    private Map<String, Object> sanitizeScope(Map<String, Object> scope, String old) {
        if (scope == null || scope.isEmpty()) {
            return null;
        }
        Map<String, Object> cleaned = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : scope.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (key == null || value == null) {
                continue;
            }
            if ("location".equals(key) && value instanceof String rawLocation) {
                putNormalizedLocation(cleaned, rawLocation.trim(), old);
                continue;
            }
            if (ALLOWED_SCOPE_KEYS.contains(key)) {
                cleaned.put(key, value);
            }
        }
        if (cleaned.size() != scope.size()) {
            log.debug("[sanitizeScope] old={} 的 scope 已规范化: {} → {}", abbreviate(old), scope, cleaned);
        }
        return cleaned.isEmpty() ? null : cleaned;
    }

    private void putNormalizedLocation(Map<String, Object> cleaned, String location, String old) {
        if (location.isEmpty()) {
            return;
        }
        if (location.matches(TABLE_PATH_PATTERN)) {
            cleaned.put("table", Integer.parseInt(location.substring(2)));
            return;
        }
        if (location.matches(CELL_PATH_PATTERN)) {
            cleaned.put("location", location + "/p:0");
            log.warn("[sanitizeScope] old={} 的 location 为单元格路径，已补全为段落路径 {}", abbreviate(old), location + "/p:0");
            return;
        }
        if (location.matches(PARAGRAPH_PATH_PATTERN) || location.matches(BODY_PATH_PATTERN)) {
            cleaned.put("location", location);
            return;
        }
        if (location.matches("\\d+")) {
            cleaned.put("location", "p:" + location);
            return;
        }
        log.warn("[sanitizeScope] old={} 的 location 无法识别，已丢弃该 scope: {}", abbreviate(old), location);
    }

    /** 调用对话模型并校验返回非空 */
    private String chat(String systemPrompt, String userPrompt) {
        String content;
        try {
            content = docTemplateChatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("[chat] 调用对话模型失败: {}", e.getMessage(), e);
            throw exception(AI_RESPONSE_INVALID, e.getMessage());
        }
        if (content == null || content.isBlank()) {
            throw exception(AI_RESPONSE_INVALID, "模型返回为空");
        }
        return content;
    }

    /** 规划结果归一化：补空值、修正 index、按上限截断、至少保留 1 份 */
    private DocPlanResult normalizePlan(DocPlanResult result, String description, int maxDocCount) {
        List<DocPlanResult.DocItem> docs = result == null ? null : result.getDocs();
        if (docs == null || docs.isEmpty()) {
            return fallbackPlan(description);
        }
        List<DocPlanResult.DocItem> normalized = new ArrayList<>();
        int limit = Math.max(1, maxDocCount);
        int planned = 0;
        for (DocPlanResult.DocItem item : docs) {
            if (item == null) {
                continue;
            }
            planned++;
            if (normalized.size() >= limit) {
                // 继续计数但不产出，便于提示被截断的份数（静默截断会让用户以为「漏做了」）
                continue;
            }
            item.setIndex(normalized.size() + 1);
            if (item.getTag() == null || item.getTag().isBlank()) {
                item.setTag(item.getTheme() == null || item.getTheme().isBlank() ? "复刻" : item.getTheme());
            }
            if (item.getTitle() == null || item.getTitle().isBlank()) {
                item.setTitle("文档" + item.getIndex());
            }
            if (item.getRequiredKeywords() == null) {
                item.setRequiredKeywords(List.of());
            }
            if (item.getForbiddenKeywords() == null) {
                item.setForbiddenKeywords(List.of());
            }
            normalized.add(item);
        }
        if (normalized.isEmpty()) {
            return fallbackPlan(description);
        }
        if (planned > normalized.size()) {
            log.warn("[plan] 描述中识别出 {} 个并列项，超过 tb.doctemplate.max-doc-count={}，已截断为 {} 份；"
                            + "如需全部产出请调大该配置",
                    planned, limit, normalized.size());
        }
        DocPlanResult normalizedResult = new DocPlanResult();
        normalizedResult.setDocs(normalized);
        normalizedResult.setTotalCount(normalized.size());
        return normalizedResult;
    }

    /** 解析失败时的兜底：单份「复刻」 */
    private DocPlanResult fallbackPlan(String description) {
        DocPlanResult.DocItem item = new DocPlanResult.DocItem();
        item.setIndex(1);
        item.setTag("复刻");
        item.setTheme("复刻");
        item.setTitle(truncate(nullToEmpty(description).replaceAll("\\s+", " "), 20, "标题"));
        if (item.getTitle().isBlank()) {
            item.setTitle("复刻文档");
        }
        item.setSummary(truncate(nullToEmpty(description).replaceAll("\\s+", " "), 50, "摘要"));
        item.setFocus("按用户描述调整内容，保持模板结构与版式不变");
        item.setCourseName("");
        item.setChapterName("");
        item.setMajor("");
        item.setRequiredKeywords(List.of());
        item.setForbiddenKeywords(List.of());

        DocPlanResult result = new DocPlanResult();
        result.setDocs(List.of(item));
        result.setTotalCount(1);
        return result;
    }

    /**
     * 从模型返回文本中提取 JSON 内容：剥离 ```json 代码块与前后解释性文字。
     */
    private String extractJson(String text, char open) {
        if (text == null || text.isBlank()) {
            throw exception(AI_RESPONSE_INVALID, "模型返回为空");
        }
        String trimmed = text.trim();
        char close = open == '{' ? '}' : ']';
        int start = trimmed.indexOf(open);
        int end = trimmed.lastIndexOf(close);
        if (start < 0 || end <= start) {
            throw exception(AI_RESPONSE_INVALID, "未找到 JSON 内容");
        }
        return trimmed.substring(start, end + 1);
    }

    private String joinKeywords(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return "（无）";
        }
        List<String> cleaned = new ArrayList<>();
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank()) {
                cleaned.add(keyword.trim());
            }
        }
        return cleaned.isEmpty() ? "（无）" : String.join("、", cleaned);
    }

    private String joinCounts(Map<String, Integer> counts) {
        if (counts == null || counts.isEmpty()) {
            return "（无）";
        }
        List<String> parts = new ArrayList<>();
        counts.forEach((keyword, count) -> parts.add(keyword + "（" + count + " 处）"));
        return String.join("、", parts);
    }

    private String itemField(String value, String fallback) {
        if (value != null && !value.isBlank()) {
            return value;
        }
        return fallback == null ? "" : fallback;
    }

    private String truncate(String text, Integer maxChars, String label) {
        if (text == null) {
            return "";
        }
        int limit = maxChars == null || maxChars <= 0 ? Integer.MAX_VALUE : maxChars;
        if (text.length() <= limit) {
            return text;
        }
        log.warn("[truncate] {} 长度 {} 超过上限 {}，已截断；静默截断会导致漏改，请调大 tb.doctemplate 对应上限",
                label, text.length(), limit);
        return text.substring(0, limit) + "\n…（内容已截断，请调大配置上限）";
    }

    private String nullToEmpty(String text) {
        return text == null ? "" : text;
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 40 ? text : text.substring(0, 40) + "…";
    }

}
