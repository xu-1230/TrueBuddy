package org.example.textflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.example.textflow.entity.NodeData;
import org.example.textflow.entity.NodeField;
import org.example.textflow.entity.NodeFieldRef;
import org.example.textflow.entity.WorkflowNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 大模型节点（type = 3）：按输入变量渲染提示词，产出输出变量。
 * <p>当前尚未接入真实模型：{@link #apply} 按 {@code outputs} 声明生成模拟产出（见 {@link #mockOutputs}），
 * 待接入模型后把模拟产出替换为模型返回即可，提示词与 JSON Schema 的组装逻辑无需改动。
 */
public class LlmNode implements NodeAction {

    private static final Logger log = LoggerFactory.getLogger(LlmNode.class);

    /** 变量类型为 list 时，结构化输出里用 array 表达 */
    private static final String LIST_TYPE = "list";

    /** 模拟数组的元素个数：list 类型输出的固定长度 */
    private static final int MOCK_LIST_SIZE = 4;

    /** 提示词模板里的占位符，如 {{output}}（允许花括号内有空格） */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([\\w.\\-]+)\\s*}}");

    private final WorkflowNode definition;

    public LlmNode(WorkflowNode definition) {
        this.definition = definition;
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
        // 提示词与输出结构仍按声明渲染，便于联调时核对；此前这两个变量算完即丢，见下方 TODO
        Map<String, Object> outputSchema = buildOutputSchema(nodeData == null ? null : nodeData.getOutputs());
        String systemPrompt = renderPrompt(nodeData == null ? null : nodeData.getSystem(), inputValues);
        String userPrompt = renderPrompt(nodeData == null ? null : nodeData.getUser(), inputValues);

        // TODO 接入真实模型后替换：当前不调用模型，按 outputs 声明生成模拟产出，保证下游（迭代 / 结束）拿得到数据
        Map<String, Object> output = mockOutputs(nodeData == null ? null : nodeData.getOutputs());
        // 只记节点 id 与变量名：输入取值、输出结构与渲染后的提示词都是用户数据，不进日志
        // （联调要看渲染结果时把本类日志级别调到 DEBUG，并自行承担日志里出现用户内容的风险）
        log.debug("大模型节点 {} 模拟执行，输入变量: {}，产出变量: {}，提示词长度: system={} user={}",
                definition.getId(), inputValues.keySet(), output.keySet(),
                systemPrompt.length(), userPrompt.length());

        return Map.of(definition.getId(), output);
    }

    /**
     * 按 outputs 声明生成模拟产出：list → 模拟数组（元素按 schema 构造），string → 模拟文本，其余 → 占位值。
     * <p>变量名取声明的 {@code name}，由 {@link #apply} 包一层 {@code {节点id: {变量名: 值}}} 写回状态。
     */
    private Map<String, Object> mockOutputs(List<NodeField> fields) {
        List<NodeField> declared = fields == null ? List.of()
                : fields.stream().filter(field -> field.getName() != null).toList();
        Map<String, Object> output = new LinkedHashMap<>();
        for (NodeField field : declared) {
            output.put(field.getName(), mockValueOf(field));
        }
        return output;
    }

    /** 单个输出变量的模拟值 */
    private Object mockValueOf(NodeField field) {
        if (LIST_TYPE.equalsIgnoreCase(field.getType())) {
            List<Object> items = new ArrayList<>(MOCK_LIST_SIZE);
            for (int i = 0; i < MOCK_LIST_SIZE; i++) {
                items.add(mockElementOf(field.getSchema(), i));
            }
            return items;
        }
        return mockScalarOf(field.getType(), 0);
    }

    /** 数组元素：object 按 properties 逐字段构造，array 取 items 构造一个元素，其余按标量 */
    private Object mockElementOf(Map<String, Object> schema, int index) {
        String type = schema == null ? "string" : String.valueOf(schema.getOrDefault("type", "string"));
        if ("object".equalsIgnoreCase(type)) {
            Map<String, Object> element = new LinkedHashMap<>();
            if (schema.get("properties") instanceof Map<?, ?> propertyMap) {
                propertyMap.forEach((name, propertySchema) -> element.put(
                        String.valueOf(name), mockElementOf(castSchema(propertySchema), index)));
            }
            return element;
        }
        if ("array".equalsIgnoreCase(type)) {
            return List.of(mockElementOf(castSchema(schema.get("items")), index));
        }
        return mockScalarOf(type, index);
    }

    /** schema 的宽松取用：不是对象（缺失 / 字符串）时返回 null，交由上层按字符串处理 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> castSchema(Object schema) {
        return schema instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    /** 标量模拟值：string → 中文文本，integer / number / boolean → 可读占位值 */
    private Object mockScalarOf(String type, int index) {
        if (type == null) {
            return "模拟文本" + (index + 1);
        }
        return switch (type.toLowerCase()) {
            case "integer" -> index + 1;
            case "number", "float" -> index + 0.5;
            case "boolean" -> true;
            case "object" -> Map.of();
            default -> "模拟文本" + (index + 1);
        };
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
     * 按节点声明的 outputs 生成大模型结构化输出结构（JSON Schema），后续作为 responseFormat / 提示词约束交给模型：
     * <ul>
     *   <li>只声明一个输出变量：直接返回该变量的 schema，模型输出的 JSON 就是这个参数本身。
     *       例如 list 输出时模型直接返回 [{"student":"赵六","paths":[...]}]，不带变量名包裹。</li>
     *   <li>声明多个输出变量：返回 {"type":"object","properties":{变量名:schema}}，按变量名区分。</li>
     * </ul>
     */
    private Map<String, Object> buildOutputSchema(List<NodeField> fields) {
        List<NodeField> declared = fields == null ? List.of()
                : fields.stream().filter(field -> field.getName() != null).toList();
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
