package org.example.backend.module.workflow.service.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.backend.module.workflow.model.schema.InputParamDef;
import org.example.backend.module.workflow.model.schema.InputSlotDef;
import org.example.backend.module.workflow.model.schema.SlotValueDef;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.example.backend.framework.common.exception.util.ServiceExceptionUtil.exception;
import static org.example.backend.module.workflow.enums.ErrorCodeConstants.WORKFLOW_INPUT_REQUIRED;

/**
 * 变量解析器：负责
 * <ul>
 *   <li>状态键约定：{@code {nodeId}.{outputName}}（如 {@code 100002.output}）；</li>
 *   <li>解析槽位取值（{@code ref} 引用 / {@code literal} 字面量 / 默认值 / 必填校验）；</li>
 *   <li>渲染提示词模板中的 {@code {{变量名}}}。</li>
 * </ul>
 */
@Component
public class RefResolver {

    /** 模板变量：{{name}}，允许两侧空格 */
    private static final Pattern TEMPLATE_VAR = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.\\-]+)\\s*}}");

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 状态键：节点输出统一存为 {nodeId}.{outputName} */
    public static String stateKey(String nodeId, String outputName) {
        return nodeId + "." + outputName;
    }

    /**
     * 解析节点的一组命名输入（{@code inputParameters} / {@code llmParam}），得到「变量名 → 值」。
     */
    public Map<String, Object> resolveParams(List<InputParamDef> params, WorkflowStateView state) {
        Map<String, Object> resolved = new LinkedHashMap<>();
        if (params == null) {
            return resolved;
        }
        for (InputParamDef param : params) {
            if (param == null || param.getName() == null) {
                continue;
            }
            resolved.put(param.getName(), resolveSlot(param.getInput(), state, param.getName()));
        }
        return resolved;
    }

    /**
     * 解析单个槽位取值。
     *
     * <p>顺序：ref 引用（{@code blockID.name} → 裸变量名）→ literal 字面量 → defaultValue →
     * required 校验失败抛 {@code WORKFLOW_INPUT_REQUIRED} → null。
     */
    public Object resolveSlot(InputSlotDef slot, WorkflowStateView state, String label) {
        if (slot == null) {
            return null;
        }
        SlotValueDef value = slot.getValue();
        if (value != null) {
            if ("ref".equalsIgnoreCase(value.getType())) {
                String blockId = value.refBlockId();
                String name = value.refName();
                if (blockId != null && name != null) {
                    Object referenced = state.find(stateKey(blockId, name)).orElse(null);
                    if (referenced != null) {
                        return coerce(referenced, slot.getType());
                    }
                }
                if (name != null) {
                    Object referenced = state.find(name).orElse(null);
                    if (referenced != null) {
                        return coerce(referenced, slot.getType());
                    }
                }
            } else if (value.getContent() != null) {
                return coerce(value.getContent(), slot.getType());
            } else if (value.getRawMeta() != null) {
                Object literal = value.getRawMeta().get("content");
                if (literal != null) {
                    return coerce(literal, slot.getType());
                }
            }
        }
        if (slot.getDefaultValue() != null) {
            return coerce(slot.getDefaultValue(), slot.getType());
        }
        if (Boolean.TRUE.equals(slot.getRequired())) {
            throw exception(WORKFLOW_INPUT_REQUIRED, String.valueOf(label));
        }
        return null;
    }

    /** 渲染模板：{@code {{fileTree}}} 等变量取自 locals，缺失时回退到图状态 */
    public String renderTemplate(String template, Map<String, Object> locals, WorkflowStateView state) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        Matcher matcher = TEMPLATE_VAR.matcher(template);
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            Object value = locals != null && locals.containsKey(name) ? locals.get(name) : null;
            if (value == null && state != null) {
                value = state.find(name).orElse(null);
            }
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(stringify(value)));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    /** 把值渲染为提示词 / 展示文本：列表按行拼接，Map 输出 JSON，null 输出空串 */
    public String stringify(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof List<?> list) {
            List<String> parts = new ArrayList<>(list.size());
            for (Object item : list) {
                parts.add(item instanceof Map || item instanceof List ? toJson(item) : String.valueOf(item));
            }
            return String.join("\n", parts);
        }
        if (value instanceof Map || value instanceof List) {
            return toJson(value);
        }
        return String.valueOf(value);
    }

    /** 按声明类型转换字面量（画布中的数字 / 布尔在 JSON 里可能是字符串） */
    public Object coerce(Object content, String type) {
        if (content == null || type == null) {
            return content;
        }
        String normalized = type.trim().toLowerCase();
        try {
            return switch (normalized) {
                case "integer", "int", "long" -> content instanceof Number number
                        ? number.longValue() : Long.parseLong(String.valueOf(content).trim());
                case "float", "number", "double" -> content instanceof Number number
                        ? number.doubleValue() : Double.parseDouble(String.valueOf(content).trim());
                case "boolean", "bool" -> content instanceof Boolean bool
                        ? bool : Boolean.parseBoolean(String.valueOf(content).trim());
                case "list" -> content instanceof List<?> ? content : List.of(content);
                default -> content;
            };
        } catch (NumberFormatException | NullPointerException ignored) {
            // 字面量与该类型不匹配时按原值返回，避免因画布配置笔误直接失败
            return content;
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

}
