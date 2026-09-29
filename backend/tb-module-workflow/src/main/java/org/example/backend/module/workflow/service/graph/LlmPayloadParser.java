package org.example.backend.module.workflow.service.graph;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.module.workflow.model.vo.GradingResultVO;
import org.example.backend.module.workflow.model.vo.PlanItemVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 大模型输出解析器：从文本中截取 JSON 并解析为结构化对象。
 *
 * <p>模型输出常带 markdown 代码块或解释性文字，故先截取首个 <code>{</code> 到末个 <code>}</code>；
 * 解析失败时降级返回空结果并记录 WARN，不打断整条工作流（与项目既有 AI 解析策略一致）。
 */
@Slf4j
@Component
public class LlmPayloadParser {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** 解析批改清单：{@code {"items": [{"filePath": ..., "needGrading": ...}]}} */
    public List<PlanItemVO> parsePlan(String raw) {
        List<PlanItemVO> items = new ArrayList<>();
        String json = extractJson(raw);
        if (json == null) {
            log.warn("[parsePlan][模型输出为空，按空清单处理] raw={}", abbreviate(raw));
            return items;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode itemsNode = root.isArray() ? root : root.path("items");
            if (itemsNode.isArray()) {
                for (JsonNode item : itemsNode) {
                    items.add(objectMapper.treeToValue(item, PlanItemVO.class));
                }
            }
        } catch (Exception e) {
            log.warn("[parsePlan][批改清单解析失败，按空清单处理] error={}, raw={}", e.getMessage(), abbreviate(raw));
        }
        return items;
    }

    /** 解析单份批改结果：{@code {"totalScore": ..., "dimensions": {...}, "comment": ...}} */
    public GradingResultVO parseGrading(String raw) {
        String json = extractJson(raw);
        if (json == null) {
            log.warn("[parseGrading][模型输出为空] raw={}", abbreviate(raw));
            return fallbackResult("批改结果为空，请人工复核");
        }
        try {
            GradingResultVO result = objectMapper.readValue(json, GradingResultVO.class);
            if (result.getTotalScore() == null && result.getComment() == null) {
                return fallbackResult("批改结果缺少总分与评语，请人工复核");
            }
            return result;
        } catch (Exception e) {
            log.warn("[parseGrading][批改结果解析失败] error={}, raw={}", e.getMessage(), abbreviate(raw));
            return fallbackResult("批改结果解析失败，请人工复核");
        }
    }

    private GradingResultVO fallbackResult(String comment) {
        GradingResultVO result = new GradingResultVO();
        result.setComment(comment);
        return result;
    }

    /** 截取首个 { 到末个 } 之间的内容；无法截取时返回 null */
    private String extractJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return raw.substring(start, end + 1);
    }

    private String abbreviate(String raw) {
        if (raw == null) {
            return "null";
        }
        return raw.length() <= 200 ? raw : raw.substring(0, 200) + "...";
    }

}
