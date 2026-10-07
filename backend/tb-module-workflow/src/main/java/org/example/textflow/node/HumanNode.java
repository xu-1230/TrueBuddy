package org.example.textflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.InterruptableAction;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.example.textflow.entity.NodeData;
import org.example.textflow.entity.NodeField;
import org.example.textflow.entity.WorkflowNode;
import org.example.textflow.model.ChatProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 人工确认节点（type = 5）：执行前中断流程，等待人工确认后再继续。
 *
 * <p>中断时把「弹窗要展示什么」一并算好塞进 {@link InterruptionMetadata}：
 * <ul>
 *   <li>{@code label} → 弹窗标题（节点 {@code data.label}）；</li>
 *   <li>{@code data} → 只读回显的**待确认数据**：按 {@code data.inputs} 的引用
 *       （{@code {blockID, name}}）逐个从状态里取值，每项 {@code {name, type, label, value}}；</li>
 *   <li>{@code fields} → 待用户填写的**表单字段**：按 {@code data.outputs} 声明生成，
 *       每项 {@code {name, type, required, label, schema}}。</li>
 * </ul>
 * 「卡片长什么样」由节点定义决定，执行侧只负责搬运与推送，两者互不耦合。
 */
public class HumanNode implements NodeAction, InterruptableAction {

    private static final Logger log = LoggerFactory.getLogger(HumanNode.class);

    private final WorkflowNode definition;

    public HumanNode(WorkflowNode definition) {
        this.definition = definition;
    }

    /**
     * 中断点：进入节点之前先中断，把弹窗数据抛给调用方；人工确认后 resume 才会真正执行 {@link #apply}。
     */
    @Override
    public Optional<InterruptionMetadata> interrupt(String nodeId, OverAllState state, RunnableConfig config) {
        // resume 时通过 RunnableConfig.Builder.addHumanFeedback(...) 带上人工反馈，此时不再中断
        if (config.metadata(RunnableConfig.HUMAN_FEEDBACK_METADATA_KEY).isPresent()) {
            return Optional.empty();
        }
        log.info("人工确认节点 {} 中断，等待人工确认", nodeId);
        return Optional.of(InterruptionMetadata.builder(nodeId, state)
                .addMetadata("nodeId", nodeId)
                .addMetadata("label", label())
                .addMetadata("data", confirmDataOf(state))
                .addMetadata("fields", formFields())
                .build());
    }

    /**
     * 人工确认后继续：把续跑注入的反馈（{@link ChatProtocol#HUMAN_FEEDBACK_KEY}）按
     * {@code state[节点id][变量名]} 的口径回写到本节点的命名空间，供下游引用。
     */
    @Override
    public Map<String, Object> apply(OverAllState state) {
        // 只记节点 id：人工填写的内容不进日志
        log.info("人工确认节点 {} 已确认，继续执行", definition.getId());
        Object feedback = state == null ? null : state.value(ChatProtocol.HUMAN_FEEDBACK_KEY).orElse(null);
        if (!(feedback instanceof Map<?, ?> values) || values.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> outputs = new LinkedHashMap<>();
        values.forEach((name, value) -> outputs.put(String.valueOf(name), value));
        return Map.of(definition.getId(), outputs);
    }

    private String label() {
        return data() == null || data().getLabel() == null ? "" : data().getLabel();
    }

    /** 只读回显的待确认数据：按 {@code data.inputs} 的引用逐个取值（取不到为 null，不抛异常） */
    private List<Map<String, Object>> confirmDataOf(OverAllState state) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (data() == null || data().getInputs() == null) {
            return list;
        }
        for (NodeField field : data().getInputs()) {
            if (field == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", field.getName());
            item.put("type", typeOf(field));
            item.put("label", labelOf(field));
            item.put("value", NodeStateReader.read(state, field.getInput()));
            list.add(item);
        }
        return list;
    }

    /** 待用户填写的表单字段：按 {@code data.outputs} 声明生成 */
    private List<Map<String, Object>> formFields() {
        List<Map<String, Object>> list = new ArrayList<>();
        if (data() == null || data().getOutputs() == null) {
            return list;
        }
        for (NodeField field : data().getOutputs()) {
            if (field == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", field.getName());
            item.put("type", typeOf(field));
            item.put("required", Boolean.TRUE.equals(field.getRequired()));
            item.put("label", labelOf(field));
            item.put("schema", field.getSchema() == null ? Map.of() : field.getSchema());
            list.add(item);
        }
        return list;
    }

    private NodeData data() {
        return definition.getData();
    }

    /** 字段说明优先作展示标签，缺省回落变量名 */
    private static String labelOf(NodeField field) {
        String description = field.getDescription();
        return description == null || description.isBlank() ? field.getName() : description;
    }

    /** 变量类型：inputs 元素常常只写 {@code name + input}，类型要退到 {@code input.type} 上取 */
    private static String typeOf(NodeField field) {
        if (field.getType() != null && !field.getType().isBlank()) {
            return field.getType();
        }
        return field.getInput() == null ? null : field.getInput().getType();
    }

}
