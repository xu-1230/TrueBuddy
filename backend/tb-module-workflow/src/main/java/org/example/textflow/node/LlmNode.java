package org.example.textflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.backend.framework.common.exception.ErrorCode;
import org.example.backend.framework.common.exception.ServiceException;
import org.example.textflow.entity.NodeData;
import org.example.textflow.entity.NodeField;
import org.example.textflow.entity.NodeFieldRef;
import org.example.textflow.entity.WorkflowNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.example.backend.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 大模型节点（type = 3）：按输入变量渲染提示词，调用对话模型产出输出变量。
 * <p>{@link #apply} 把 {@code outputs} 声明的 JSON Schema 并入系统提示词后调用模型，
 * 再把模型返回的 JSON 按变量名拆回状态；模型不可用或返回无法解析时抛业务异常，由上层把流程标记为失败。
 */
public class LlmNode implements NodeAction {

    private static final Logger log = LoggerFactory.getLogger(LlmNode.class);

    /** 变量类型为 list 时，结构化输出里用 array 表达 */
    private static final String LIST_TYPE = "list";

    /** 提示词模板里的占位符，如 {{output}}（允许花括号内有空格） */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([\\w.\\-]+)\\s*}}");

    /** 大模型调用失败（工作流模块错误码段 1_005_000_xxx） */
    private static final ErrorCode AI_RESPONSE_INVALID = new ErrorCode(1_005_000_000, "工作流大模型调用失败：{}");

    /** 追加到系统提示词末尾的输出结构约束，其后紧跟 JSON Schema */
    private static final String OUTPUT_SCHEMA_HEADER = "请严格按以下 JSON Schema 输出，只返回 JSON，不要添加解释：";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WorkflowNode definition;

    /** 对话模型客户端；为 null（无 Spring 容器的单测场景）时无法执行节点，调用时抛业务异常 */
    private final ChatClient nodeChatClient;

    /**
     * 随每次调用一起提交给模型的工具（如作业文件读取）。
     * <p>工具入参（如 fileId）需要模型自己提供，因此提示词里必须能拿到文件清单；
     * 工具的执行与结果回填由 Spring AI 的 ToolCallingManager 完成，模型可多轮调用后再产出最终 JSON。
     */
    private final List<ToolCallback> toolCallbacks;

    public LlmNode(WorkflowNode definition, ChatClient nodeChatClient) {
        this(definition, nodeChatClient, List.of());
    }

    public LlmNode(WorkflowNode definition, ChatClient nodeChatClient, List<ToolCallback> toolCallbacks) {
        this.definition = definition;
        this.nodeChatClient = nodeChatClient;
        this.toolCallbacks = toolCallbacks == null ? List.of() : List.copyOf(toolCallbacks);
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws InterruptedException {
        NodeData nodeData = definition.getData();
        Map<String, Object> inputValues = new LinkedHashMap<>();
        if (nodeData != null && nodeData.getInputs() != null) {
            for (NodeField datum : nodeData.getInputs()) {
                NodeFieldRef nodeFieldRef = datum.getInput();
                if (nodeFieldRef == null) {
                    continue;
                }
                inputValues.put(datum.getName(), NodeStateReader.read(state, nodeFieldRef));
            }
        }
        // 输出结构按 outputs 声明渲染：单变量时模型直接返回该值，多变量时返回 {变量名: 值}
        Map<String, Object> outputSchema = buildOutputSchema(nodeData == null ? null : nodeData.getOutputs());
        // 输出结构并入系统提示词，让模型在生成时就知道要按声明的变量返回
        String systemPrompt = appendOutputSchema(renderPrompt(nodeData == null ? null : nodeData.getSystem(), inputValues), outputSchema);
        String userPrompt = renderPrompt(nodeData == null ? null : nodeData.getUser(), inputValues);

        System.out.println(systemPrompt);
        System.out.println(userPrompt);

        // 调用真实模型，再把返回的 JSON 按 outputs 声明拆成 {变量名: 值} 写回状态
        Map<String, Object> output = chat(systemPrompt, userPrompt, nodeData == null ? null : nodeData.getOutputs());
        System.out.println(output);

        return Map.of(definition.getId(), output);
    }

    /**
     * 调用对话模型，并把返回的 JSON 按 outputs 声明拆成 {@code {变量名: 值}}。
     * <p>输出结构见 {@link #buildOutputSchema(List)}：只声明一个输出变量时模型直接返回该值，
     * 声明多个时返回 {@code {变量名: 值}}；多变量时缺失的变量按 {@code null} 补齐，保证下游取键不会拿不到。
     */
    private Map<String, Object> chat(String systemPrompt, String userPrompt, List<NodeField> fields) {
        if (nodeChatClient == null) {
            throw exception(AI_RESPONSE_INVALID, "未配置对话模型客户端");
        }
        String content;
        try {
            ChatClient.ChatClientRequestSpec request = nodeChatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt);
            if (!toolCallbacks.isEmpty()) {
                request = request.toolCallbacks(toolCallbacks);
            }
            content = request.call().content();
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            // 只记节点 id 与异常信息：渲染后的提示词是用户数据，不进日志
            log.error("[chat] 大模型节点 {} 调用对话模型失败: {}", definition.getId(), e.getMessage(), e);
            throw exception(AI_RESPONSE_INVALID, e.getMessage());
        }
        if (content == null || content.isBlank()) {
            throw exception(AI_RESPONSE_INVALID, "模型返回为空");
        }
        return parseOutputs(content, declaredOutputs(fields));
    }

    /** 模型返回文本 → 输出变量：单变量取该值本身，多变量按变量名取 */
    private Map<String, Object> parseOutputs(String content, List<NodeField> declared) {
        if (declared.isEmpty()) {
            log.debug("大模型节点 {} 未声明输出变量，模型返回不写回状态", definition.getId());
            return Map.of();
        }
        JsonNode json = readJson(content);
        Map<String, Object> output = new LinkedHashMap<>();
        if (declared.size() == 1) {
            NodeField field = declared.get(0);
            // 解析不出 JSON（模型直接返回了裸文本）时按原文取值，避免整个节点因格式问题失败
            output.put(field.getName(), json == null ? content.trim() : toJavaValue(json));
            return output;
        }
        if (json == null || !json.isObject()) {
            throw exception(AI_RESPONSE_INVALID, "模型返回的结构不符合声明的输出变量");
        }
        for (NodeField field : declared) {
            JsonNode value = json.path(field.getName());
            output.put(field.getName(), value.isMissingNode() || value.isNull() ? null : toJavaValue(value));
        }
        return output;
    }

    /**
     * 读取模型返回的 JSON：先按原文解析，失败再截取最外层 JSON 片段重试；
     * 仍解析不出时返回 {@code null}，由调用方决定兜底（单变量按裸文本取值，多变量报错）。
     */
    private JsonNode readJson(String content) {
        String trimmed = content.trim();
        try {
            return MAPPER.readTree(trimmed);
        } catch (JsonProcessingException ignored) {
            // 模型常在 JSON 前后带 ``` 代码块或解释性文字，退化为按括号截取最外层片段
        }
        String excerpt = extractJson(trimmed);
        if (excerpt == null) {
            return null;
        }
        try {
            return MAPPER.readTree(excerpt);
        } catch (JsonProcessingException e) {
            // 只记异常信息：模型返回属用户数据，不进日志
            log.warn("大模型节点 {} 的模型返回无法解析为 JSON：{}", definition.getId(), e.getOriginalMessage());
            return null;
        }
    }

    /** 截取最外层 JSON 片段：对象与数组取先出现的那个；都找不到时返回 null */
    private String extractJson(String text) {
        int objectStart = text.indexOf('{');
        int arrayStart = text.indexOf('[');
        boolean useObject = objectStart >= 0 && (arrayStart < 0 || objectStart < arrayStart);
        char open = useObject ? '{' : '[';
        int start = text.indexOf(open);
        int end = text.lastIndexOf(useObject ? '}' : ']');
        return start < 0 || end <= start ? null : text.substring(start, end + 1);
    }

    /** JSON → 状态里的 Java 值（数组 / 对象转 List / Map），与下游节点取值口径一致 */
    private Object toJavaValue(JsonNode json) {
        return MAPPER.convertValue(json, Object.class);
    }

    /** 已声明变量名的输出变量（没有 name 的声明无法写回状态，直接忽略） */
    private List<NodeField> declaredOutputs(List<NodeField> fields) {
        return fields == null ? List.of()
                : fields.stream().filter(field -> field.getName() != null).toList();
    }

    /**
     * 编写提示词：把模板里的 {{变量名}} 换成输入变量的取值（变量名取 inputs 的本地别名）。
     * 取不到值（含模板里写了未声明的变量）时替换为空串，避免把占位符原样发给模型。
     */
    private String renderPrompt(String template, Map<String, Object> values) {
        if (template == null) {
            return null;
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder prompt = new StringBuilder();
        while (matcher.find()) {
            Object value = values.get(matcher.group(1));
            matcher.appendReplacement(prompt, Matcher.quoteReplacement(value == null ? "" : String.valueOf(value)));
        }
        matcher.appendTail(prompt);
        return prompt.toString();
    }

    /**
     * 把输出结构（JSON Schema）追加到系统提示词末尾，约束模型按声明的输出变量返回。
     * <p>未声明系统提示词（模板缺失）时只返回该约束段；schema 序列化失败（正常不会发生）时退化为 toString。
     */
    private String appendOutputSchema(String systemPrompt, Map<String, Object> outputSchema) {
        String schemaJson;
        try {
            schemaJson = MAPPER.writeValueAsString(outputSchema);
        } catch (JsonProcessingException e) {
            // 只记节点 id：输出结构由用户定义，内容不进日志
            log.warn("大模型节点 {} 的输出结构序列化失败，按 toString 追加：{}", definition.getId(), e.getMessage());
            schemaJson = String.valueOf(outputSchema);
        }
        String section = OUTPUT_SCHEMA_HEADER + "\n" + schemaJson;
        return systemPrompt == null || systemPrompt.isBlank() ? section : systemPrompt + "\n\n" + section;
    }

    /**
     * 按节点声明的 outputs 生成大模型结构化输出结构（JSON Schema），随系统提示词一起交给模型：
     * <ul>
     *   <li>只声明一个输出变量：直接返回该变量的 schema，模型输出的 JSON 就是这个参数本身。
     *       例如 list 输出时模型直接返回 [{"student":"赵六","paths":[...]}]，不带变量名包裹。</li>
     *   <li>声明多个输出变量：返回 {"type":"object","properties":{变量名:schema}}，按变量名区分。</li>
     * </ul>
     */
    private Map<String, Object> buildOutputSchema(List<NodeField> fields) {
        List<NodeField> declared = declaredOutputs(fields);
        if (declared.size() == 1) {
            return fieldSchema(declared.get(0));
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (NodeField field : declared) {
            properties.put(field.getName(), fieldSchema(field));
            if (Boolean.TRUE.equals(field.getRequired())) {
                required.add(field.getName());
            }
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    /** 单个输出变量的 JSON Schema：list -> array + items（元素结构取 schema），其余按其 type */
    private Map<String, Object> fieldSchema(NodeField field) {
        Map<String, Object> schema = new LinkedHashMap<>();
        if (LIST_TYPE.equalsIgnoreCase(field.getType())) {
            schema.put("type", "array");
            schema.put("items", field.getSchema() == null || field.getSchema().isEmpty()
                    ? Map.of("type", "string")
                    : field.getSchema());
        } else {
            schema.put("type", field.getType() == null ? "string" : field.getType());
        }
        if (field.getDescription() != null) {
            schema.put("description", field.getDescription());
        }
        return schema;
    }

}
