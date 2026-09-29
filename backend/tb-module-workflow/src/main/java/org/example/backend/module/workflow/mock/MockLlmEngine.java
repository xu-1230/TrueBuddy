package org.example.backend.module.workflow.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.module.workflow.config.WorkflowProperties;
import org.example.backend.module.workflow.model.vo.GradingDimensionsVO;
import org.example.backend.module.workflow.model.vo.GradingResultVO;
import org.example.backend.module.workflow.model.vo.PlanItemVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 模拟大模型引擎（MVP 阶段不调用真实模型）。
 *
 * <p>按节点语义返回与真实模型同构的 JSON：规划节点返回批改清单，批改节点返回总分 / 三维度 /
 * 评语。分数由文件路径哈希派生，保证同一份作业多次执行结果稳定、不同作业之间有区分度。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MockLlmEngine {

    /** 可参与批改的文件扩展名 */
    private static final Set<String> GRADABLE_EXTENSIONS =
            Set.of("docx", "doc", "pdf", "txt", "md", "html", "htm");

    private static final String OUTPUT_ITEMS = "items";

    private final MockKnowledgeBaseService knowledgeBaseService;

    private final WorkflowProperties properties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 模拟节点语义 */
    public enum MockLlmSemantic {
        /** 规划批改清单 */
        PLANNING,
        /** 单份作业批改 */
        GRADING,
        /** 其他（兜底） */
        GENERIC
    }

    /**
     * 模拟调用入参。
     *
     * @param nodeId    节点 id
     * @param title     节点标题
     * @param prompt    渲染后的提示词
     * @param locals    节点输入变量（按变量名）
     * @param llmParams 节点大模型参数（按参数名）
     */
    public record MockLlmRequest(String nodeId, String title, String prompt,
                                Map<String, Object> locals, Map<String, Object> llmParams) {
    }

    /**
     * 模拟调用结果。
     *
     * @param output    主输出（JSON 字符串）
     * @param reasoning 推理过程（reasoning_content）
     */
    public record MockLlmResult(String output, String reasoning) {
    }

    /** 执行一次模拟调用 */
    public MockLlmResult invoke(MockLlmRequest request) {
        MockLlmSemantic semantic = detectSemantic(request.title(), request.prompt());
        sleepQuietly(properties.getMockLlmDelayMs());
        String output = switch (semantic) {
            case PLANNING -> buildPlanOutput(request);
            case GRADING -> buildGradingOutput(request);
            case GENERIC -> buildGenericOutput(request);
        };
        log.debug("[invoke][模拟大模型执行完成] nodeId={}, title={}, semantic={}, outputLength={}",
                request.nodeId(), request.title(), semantic, output.length());
        return new MockLlmResult(output, "模拟推理（" + semantic + "）：已按提示词组织上下文并生成结构化结果");
    }

    /** 按标题 / 提示词特征判定节点语义 */
    public MockLlmSemantic detectSemantic(String title, String prompt) {
        String text = (title == null ? "" : title) + "|" + (prompt == null ? "" : prompt);
        if (text.contains(OUTPUT_ITEMS) || text.contains("清单") || text.contains("规划")) {
            return MockLlmSemantic.PLANNING;
        }
        if (text.contains("totalScore") || text.contains("评语") || text.contains("批改")) {
            return MockLlmSemantic.GRADING;
        }
        return MockLlmSemantic.GENERIC;
    }

    /** 规划节点：根据文件树产出待批改清单 */
    private String buildPlanOutput(MockLlmRequest request) {
        List<String> fileTree = toStringList(request.locals().get("fileTree"));
        int maxItems = Math.max(1, properties.getMaxLoopItems());
        List<PlanItemVO> items = new ArrayList<>();
        boolean truncated = false;
        for (String path : fileTree) {
            if (isDirectory(path)) {
                continue;
            }
            if (items.size() >= maxItems) {
                truncated = true;
                break;
            }
            String fileName = fileNameOf(path);
            boolean gradable = GRADABLE_EXTENSIONS.contains(extensionOf(fileName));
            items.add(new PlanItemVO(path, fileName, gradable,
                    gradable ? "文本类作业，可直接解析并批改" : "非文本类文件，当前版本不参与批改"));
        }
        if (truncated) {
            log.warn("[buildPlanOutput][规划清单超过上限已截断] nodeId={}, maxItems={}", request.nodeId(), maxItems);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(OUTPUT_ITEMS, items);
        String output = toJson(payload);
        log.info("[buildPlanOutput][批改规划完成] nodeId={}, items={}, gradable={}/{}",
                request.nodeId(), items.size(), items.stream().filter(item -> Boolean.TRUE.equals(item.getNeedGrading())).count(),
                items.size());
        return output;
    }

    /** 批改节点：产出总分 / 三维度 / 评语 */
    private String buildGradingOutput(MockLlmRequest request) {
        String item = stringify(request.locals().get("item"));
        int fullScore = toInt(request.locals().get("fullScore"), 100);
        List<String> knowledgeList = toStringList(request.locals().get("knowledgeList"));
        List<String> snippets = knowledgeBaseService.retrieve(item, knowledgeList, 2);

        int seed = Math.floorMod(item.hashCode(), 10_000);
        double accuracy = 62 + seed % 34;
        double completeness = 58 + (seed / 3) % 38;
        double expression = 66 + (seed / 7) % 30;
        int totalScore = (int) Math.round((accuracy + completeness + expression) / 3.0 * fullScore / 100.0);

        GradingResultVO result = new GradingResultVO();
        result.setTotalScore(totalScore);
        result.setDimensions(new GradingDimensionsVO(accuracy, completeness, expression));
        result.setComment(buildComment(request, snippets));
        return toJson(result);
    }

    /** 兜底语义：返回与真实模型同构的简单结构 */
    private String buildGenericOutput(MockLlmRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("output", "模拟大模型输出：" + (request.title() == null ? request.nodeId() : request.title()));
        return toJson(payload);
    }

    private String buildComment(MockLlmRequest request, List<String> snippets) {
        String basis = abbreviate(stringify(request.locals().get("aiDescription")), 30);
        String citation = snippets.isEmpty() ? "课程讲义" : abbreviate(tailOf(snippets.get(0)), 24);
        String comment = "按「" + basis + "」批改：静态工作点计算过程完整、公式书写规范；"
                + "交流通路分析缺少相位说明，建议参考「" + citation + "」补充说明后重做该小题。";
        return abbreviate(comment, 150);
    }

    private boolean isDirectory(String path) {
        return path != null && (path.endsWith("/") || path.endsWith("\\"));
    }

    private String fileNameOf(String path) {
        if (path == null) {
            return "";
        }
        String normalized = path.replace('\\', '/');
        int index = normalized.lastIndexOf('/');
        return index < 0 ? normalized : normalized.substring(index + 1);
    }

    private String extensionOf(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index < 0 ? "" : fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String tailOf(String snippet) {
        int index = snippet.lastIndexOf('：');
        return index < 0 ? snippet : snippet.substring(index + 1);
    }

    private String stringify(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Map || value instanceof List) {
            return toJson(value);
        }
        return String.valueOf(value);
    }

    private List<String> toStringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                result.add(stringify(item));
            }
        } else if (value != null) {
            result.add(stringify(value));
        }
        return result;
    }

    private int toInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? defaultValue : Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String abbreviate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "…";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("[toJson][模拟结果序列化失败] error={}", e.getMessage());
            return String.valueOf(value);
        }
    }

    private void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
