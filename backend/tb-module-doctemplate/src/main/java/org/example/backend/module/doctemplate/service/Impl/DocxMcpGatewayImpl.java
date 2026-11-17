package org.example.backend.module.doctemplate.service.Impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.framework.common.exception.ServiceException;
import org.example.backend.module.doctemplate.model.vo.DocxReplaceReport;
import org.example.backend.module.doctemplate.model.vo.DocxValidateResult;
import org.example.backend.module.doctemplate.service.DocxMcpGateway;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.example.backend.framework.common.exception.util.ServiceExceptionUtil.exception;
import static org.example.backend.module.doctemplate.enums.ErrorCodeConstants.GENERATE_FAILED;
import static org.example.backend.module.doctemplate.enums.ErrorCodeConstants.MCP_TOOL_UNAVAILABLE;

/**
 * docx-mcp 网关实现：通过 Spring AI MCP Client 暴露的 {@link ToolCallbackProvider} 按名调用工具。
 * <p>
 * 两点关键约束：
 * <ul>
 *   <li>句柄（docId）与本地文件路径都依赖 MCP 进程，必须复用同一长连接会话；</li>
 *   <li>Spring AI 的 {@code SyncMcpToolCallback.call()} 返回的是 content 数组 JSON
 *       （{@code [{"type":"text","text":"<工具正文>"}]}），所有调用统一经 {@link #unwrapToolOutput} 解包。</li>
 * </ul>
 */
@Slf4j
@Service
public class DocxMcpGatewayImpl implements DocxMcpGateway {

    private static final String TOOL_OPEN = "docx_open";
    private static final String TOOL_GET_TEXT = "docx_get_text";
    private static final String TOOL_GET_TABLES = "docx_get_tables";
    private static final String TOOL_REPLACE = "docx_replace";
    private static final String TOOL_VALIDATE = "docx_validate";
    private static final String TOOL_FILL_FIELDS = "docx_fill_fields";
    private static final String TOOL_SET_CELL = "docx_set_cell";
    private static final String TOOL_INSERT_PARAGRAPH = "docx_insert_paragraph";
    private static final String TOOL_DELETE_PARAGRAPH = "docx_delete_paragraph";
    private static final String TOOL_DIFF = "docx_diff";

    private final ObjectProvider<ToolCallbackProvider> toolCallbackProviders;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public DocxMcpGatewayImpl(ObjectProvider<ToolCallbackProvider> toolCallbackProviders) {
        this.toolCallbackProviders = toolCallbackProviders;
    }

    @Override
    public String openOutline(String source) {
        Map<String, Object> args = new HashMap<>();
        args.put("source", source);
        return callTool(TOOL_OPEN, args);
    }

    @Override
    public String getText(String source, Integer page, Integer pageSize) {
        Map<String, Object> args = new HashMap<>();
        args.put("source", source);
        if (page != null) {
            args.put("page", page);
        }
        if (pageSize != null) {
            args.put("page_size", pageSize);
        }
        return callTool(TOOL_GET_TEXT, args);
    }

    @Override
    public String getTables(String source) {
        Map<String, Object> args = new HashMap<>();
        args.put("source", source);
        return callTool(TOOL_GET_TABLES, args);
    }

    @Override
    public DocxReplaceReport replace(String source, List<Map<String, Object>> rules,
                                    String outputPath, boolean dryRun) {
        Map<String, Object> args = new HashMap<>();
        args.put("source", source);
        args.put("rules", rules);
        args.put("dry_run", dryRun);
        if (!dryRun && outputPath != null) {
            args.put("output_path", outputPath);
        }
        String raw = callTool(TOOL_REPLACE, args);
        DocxReplaceReport report = readJson(raw, TOOL_REPLACE, DocxReplaceReport.class);
        if (report.getError() != null) {
            throw exception(GENERATE_FAILED, "文档改写失败：" + report.getError());
        }
        if (dryRun) {
            log.debug("[replace] dryRun 完成: appliedRules={}, missedRules={}",
                    report.getAppliedRules(), size(report.getMissedRules()));
            return report;
        }
        if (report.getOutputPath() == null) {
            throw exception(GENERATE_FAILED, "文档改写失败（未返回 outputPath）");
        }
        log.info("[replace] 落盘完成: appliedRules={}, missedRules={}, path={}",
                report.getAppliedRules(), size(report.getMissedRules()), report.getOutputPath());
        return report;
    }

    @Override
    public DocxValidateResult validate(String source, List<String> requiredKeywords, List<String> forbiddenKeywords) {
        Map<String, Object> args = new HashMap<>();
        args.put("source", source);
        if (requiredKeywords != null && !requiredKeywords.isEmpty()) {
            args.put("required_keywords", requiredKeywords);
        }
        if (forbiddenKeywords != null && !forbiddenKeywords.isEmpty()) {
            args.put("forbidden_keywords", forbiddenKeywords);
        }
        String raw = callTool(TOOL_VALIDATE, args);
        DocxValidateResult result = readJson(raw, TOOL_VALIDATE, DocxValidateResult.class);
        if (result.getError() != null) {
            throw exception(GENERATE_FAILED, "文档自检失败：" + result.getError());
        }
        return result;
    }

    @Override
    public String fillFields(String source, Map<String, String> fields, String outputPath, boolean overwrite) {
        if (fields == null || fields.isEmpty()) {
            return null;
        }
        Map<String, Object> args = new HashMap<>();
        args.put("source", source);
        args.put("fields", fields);
        args.put("overwrite", overwrite);
        if (outputPath != null) {
            args.put("output_path", outputPath);
        }
        String raw = callTool(TOOL_FILL_FIELDS, args);
        Map<String, Object> result = readJsonMap(raw, TOOL_FILL_FIELDS);
        if (result.get("error") != null) {
            throw exception(GENERATE_FAILED, "封面字段填充失败：" + result.get("error"));
        }
        log.info("[fillFields] 封面字段填充: filled={}, skipped={}, missed={}",
                sizeOf(result.get("filled")), sizeOf(result.get("skipped")), result.get("missed"));
        Object path = result.get("outputPath");
        return path == null ? source : path.toString();
    }

    @Override
    public Map<String, Object> setCell(String source, String text, String location, String outputPath) {
        Map<String, Object> args = new HashMap<>();
        args.put("source", source);
        args.put("text", text);
        if (location != null && !location.isBlank()) {
            args.put("location", location);
        }
        if (outputPath != null) {
            args.put("output_path", outputPath);
        }
        Map<String, Object> result = readJsonMap(callTool(TOOL_SET_CELL, args), TOOL_SET_CELL);
        if (result.get("error") != null) {
            throw exception(GENERATE_FAILED, "单元格写入失败：" + result.get("error"));
        }
        return result;
    }

    @Override
    public Map<String, Object> insertParagraph(String source, String text, String after,
                                               String before, String styleFrom, String outputPath) {
        Map<String, Object> args = new HashMap<>();
        args.put("source", source);
        args.put("text", text);
        if (after != null && !after.isBlank()) {
            args.put("after", after);
        }
        if (before != null && !before.isBlank()) {
            args.put("before", before);
        }
        if (styleFrom != null && !styleFrom.isBlank()) {
            args.put("style_from", styleFrom);
        }
        if (outputPath != null) {
            args.put("output_path", outputPath);
        }
        Map<String, Object> result = readJsonMap(callTool(TOOL_INSERT_PARAGRAPH, args), TOOL_INSERT_PARAGRAPH);
        if (result.get("error") != null) {
            throw exception(GENERATE_FAILED, "段落插入失败：" + result.get("error"));
        }
        return result;
    }

    @Override
    public Map<String, Object> deleteParagraph(String source, String location, String outputPath) {
        Map<String, Object> args = new HashMap<>();
        args.put("source", source);
        args.put("location", location);
        if (outputPath != null) {
            args.put("output_path", outputPath);
        }
        Map<String, Object> result = readJsonMap(callTool(TOOL_DELETE_PARAGRAPH, args), TOOL_DELETE_PARAGRAPH);
        if (result.get("error") != null) {
            throw exception(GENERATE_FAILED, "段落删除失败：" + result.get("error"));
        }
        return result;
    }

    @Override
    public Map<String, Object> diff(String pathA, String pathB, String level) {
        Map<String, Object> args = new HashMap<>();
        args.put("path_a", pathA);
        args.put("path_b", pathB);
        args.put("level", level == null ? "text" : level);
        Map<String, Object> result = readJsonMap(callTool(TOOL_DIFF, args), TOOL_DIFF);
        if (result.get("error") != null) {
            throw exception(GENERATE_FAILED, "文档比对失败：" + result.get("error"));
        }
        return result;
    }

    // ========== 私有方法 ==========

    /** 按名查找 MCP 工具回调并调用，工具入参序列化为 JSON */
    private String callTool(String toolName, Map<String, Object> args) {
        ToolCallback callback = findToolCallback(toolName);
        String payload;
        try {
            payload = objectMapper.writeValueAsString(args);
        } catch (Exception e) {
            throw exception(MCP_TOOL_UNAVAILABLE, toolName + "（参数序列化失败：" + e.getMessage() + "）");
        }
        try {
            String output = callback.call(payload);
            if (output == null) {
                throw exception(MCP_TOOL_UNAVAILABLE, toolName + "（返回为空）");
            }
            return unwrapToolOutput(output, toolName);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("[callTool] 调用 docx-mcp 工具失败: tool={}, error={}", toolName, e.getMessage(), e);
            throw exception(MCP_TOOL_UNAVAILABLE, toolName + " - " + e.getMessage());
        }
    }

    /**
     * 解包 Spring AI MCP 工具返回值。
     * <p>
     * Spring AI 的 {@code SyncMcpToolCallback.call()} 返回的是
     * {@code ModelOptionsUtils.toJsonString(CallToolResult.content())}，即形如
     * {@code [{"type":"text","text":"<工具正文>"}]} 的 content 数组 JSON，
     * 而非工具正文本身。这里统一抽取其中的 text 内容，使上层拿到工具原始 JSON。
     */
    private String unwrapToolOutput(String output, String toolName) {
        String text = output.trim();
        if (text.isEmpty()) {
            return text;
        }
        try {
            JsonNode node = objectMapper.readTree(text);
            String extracted = extractText(node);
            if (extracted != null && !extracted.isBlank()) {
                return extracted.trim();
            }
        } catch (Exception e) {
            log.debug("[unwrapToolOutput] 工具 {} 返回非 JSON，按原文处理: {}", toolName, e.getMessage());
        }
        return text;
    }

    /** 从 MCP content 结构（数组 / {content:[...]} / {text:...}）中抽取文本；非 content 结构返回 null */
    private String extractText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode item : node) {
                String part = extractText(item);
                if (part != null) {
                    builder.append(part);
                }
            }
            return builder.length() == 0 ? null : builder.toString();
        }
        if (node.isObject()) {
            JsonNode content = node.get("content");
            if (content != null && content.isArray()) {
                return extractText(content);
            }
            JsonNode textNode = node.get("text");
            if (textNode != null && textNode.isTextual()) {
                return textNode.asText();
            }
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        return null;
    }

    /** 跨所有 ToolCallbackProvider 按名查找工具回调 */
    private ToolCallback findToolCallback(String toolName) {
        for (ToolCallbackProvider provider : toolCallbackProviders) {
            ToolCallback[] callbacks = provider.getToolCallbacks();
            if (callbacks == null) {
                continue;
            }
            for (ToolCallback callback : callbacks) {
                if (callback != null && callback.getToolDefinition() != null
                        && toolName.equals(callback.getToolDefinition().name())) {
                    return callback;
                }
            }
        }
        throw exception(MCP_TOOL_UNAVAILABLE, toolName + "（未注册，请检查 mcp-servers-config.json 与 MCP Client 配置）");
    }

    /** 解析 MCP 工具返回的 JSON 为指定类型 */
    private <T> T readJson(String raw, String toolName, Class<T> type) {
        try {
            return objectMapper.readValue(raw, type);
        } catch (Exception e) {
            log.error("[readJson] {} 返回内容无法解析为 {}，前 300 字符: {}",
                    toolName, type.getSimpleName(), abbreviate(raw));
            throw exception(GENERATE_FAILED, toolName + " 返回内容解析失败：" + e.getMessage());
        }
    }

    /** 解析 MCP 工具返回的 JSON 对象（通用 Map） */
    private Map<String, Object> readJsonMap(String raw, String toolName) {
        try {
            return objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.error("[readJsonMap] {} 返回内容无法解析为 JSON 对象，前 300 字符: {}",
                    toolName, abbreviate(raw));
            throw exception(GENERATE_FAILED, toolName + " 返回内容解析失败：" + e.getMessage());
        }
    }

    private int size(List<?> list) {
        return list == null ? 0 : list.size();
    }

    private int sizeOf(Object value) {
        return value instanceof List<?> list ? list.size() : 0;
    }

    /** 截断长文本用于日志 */
    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 300 ? text : text.substring(0, 300) + "…";
    }

}
