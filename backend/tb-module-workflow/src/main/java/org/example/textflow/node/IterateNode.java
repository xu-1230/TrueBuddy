package org.example.textflow.node;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.textflow.entity.NodeData;
import org.example.textflow.entity.NodeField;
import org.example.textflow.entity.WorkflowEdge;
import org.example.textflow.entity.WorkflowNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 迭代节点（type = 4）：取一个列表输入，对其中每个元素依次执行一遍迭代体（节点内部的 blocks / edges 组成的子图），
 * 再把每次的结果汇总成输出。
 * <p>实现方式参考 Spring AI Alibaba 子图文档的「模式 2：在节点操作中调用子图」：
 * 迭代要求对每一项各跑一遍，而把子图直接挂到父图上（{@code addNode(id, CompiledGraph)}）只会执行一次，
 * 因此这里在节点内部编译子图，每轮把父状态转换成子图状态后调用，再把子图结果转换回父状态。
 *
 * <p>取值约定与其它节点一致：{@code state[节点 id][变量名]}。每轮子图状态里，迭代节点自身 id 下会放入
 * {@code index}（轮次下标）、{@code item}（当前项）、当前项的别名（子块引用迭代节点时的变量名，如 {@code input}）
 * 以及 {@code variableParameters} 声明的中间变量。
 */
public class IterateNode implements NodeAction {

    /** 变量类型为 list 时视为可迭代的输入来源 */
    private static final String LIST_TYPE = "list";

    /** 每轮注入的轮次下标变量名 */
    private static final String INDEX_VAR = "index";

    /** 每轮注入的当前项变量名 */
    private static final String ITEM_VAR = "item";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final WorkflowNode definition;

    private final NodeEventListener listener;

    /** 迭代体子图：编译一次，多轮复用（避免每轮重复编译） */
    private volatile CompiledGraph subGraph;

    /** 迭代体子块的节点定义（blocks 反序列化结果） */
    private final List<WorkflowNode> blocks;

    /** 迭代体出口节点 id（指向迭代节点自身的那些边的起点） */
    private final Set<String> terminalBlockIds;

    /** 当前项在子图状态里的变量名集合（子块引用迭代节点 id 时用的名字，如 input） */
    private final Set<String> itemVarNames;

    public IterateNode(WorkflowNode definition, NodeEventListener listener) {
        this.definition = definition;
        this.listener = listener == null ? NodeEventListener.NO_OP : listener;
        this.blocks = convertBlocks(definition.getBlocks());
        this.itemVarNames = collectItemVarNames(this.blocks, definition.getId());
        this.terminalBlockIds = collectTerminalBlockIds(definition.getEdges(), definition.getId());
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        List<Object> items = readItems(state);
        if (items.isEmpty()) {
            // 没有可迭代项也要按 outputs 声明写空列表，否则下游取变量会拿到 null
            System.out.println("迭代节点 " + definition.getId() + " 没有可迭代的列表输入，返回空汇总");
            return Map.of(definition.getId(), aggregate(List.of()));
        }

        CompiledGraph graph = subGraph();
        if (graph == null) {
            // 没有迭代体子块：同样返回空汇总，保证输出变量在状态里存在
            return Map.of(definition.getId(), aggregate(List.of()));
        }

        List<Map<String, Object>> rounds = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            Map<String, Object> roundInput = buildRoundInput(state, items.get(index), index);
            try {
                List<NodeOutput> outputs = graph.stream(roundInput).collectList().block();
                if (outputs == null || outputs.isEmpty()) {
                    continue;
                }
                // 迭代体内部的节点事件转发给上层（SSE），否则前端只能看到迭代节点整体执行一次
                for (NodeOutput output : outputs) {
                    listener.onEvent(output);
                }
                Map<String, Object> roundResult = collectRoundResult(outputs.get(outputs.size() - 1));
                rounds.add(roundResult);
                System.out.println("迭代节点 " + definition.getId() + " 第 " + index + " 轮完成，产出变量: " + (roundResult.isEmpty() ? "无" : roundResult.keySet()));
            } catch (Exception e) {
                System.out.println("迭代节点 " + definition.getId() + " 第 " + index + " 轮执行失败: " + e.getMessage());
            }
        }

        return Map.of(definition.getId(), aggregate(rounds));
    }

    /** 惰性编译迭代体子图（没有子块时返回 null，表示不执行迭代体） */
    private CompiledGraph subGraph() throws GraphStateException {
        if (subGraph != null) {
            return subGraph;
        }
        synchronized (this) {
            if (subGraph != null) {
                return subGraph;
            }
            if (blocks.isEmpty()) {
                System.out.println("迭代节点 " + definition.getId() + " 没有迭代体子块，跳过");
                return null;
            }
            subGraph = buildSubGraph();
            return subGraph;
        }
    }

    /**
     * 编译迭代体子图：blocks 作为节点、edges 作为边，迭代节点自身 id 在边里充当虚拟的入口 / 出口。
     * <p>子图独立编译，不挂 checkpoint，KeyStrategy 用 Replace（每轮状态独立，避免同名键被累积成列表）。
     */
    private CompiledGraph buildSubGraph() throws GraphStateException {
        String iterateId = definition.getId();
        StateGraph graph = new StateGraph("iterate-" + iterateId, subKeyStrategyFactory());

        Set<String> blockIds = new HashSet<>();
        for (WorkflowNode block : blocks) {
            System.out.println(block);
            graph.addNode(block.getId(), NodeActionFactory.of(block, listener));
            blockIds.add(block.getId());
        }

        // 子图里的边：指向迭代节点 = 迭代体出口（接 END），来自迭代节点 = 迭代体入口（接 START）
        Set<String> withInbound = new HashSet<>();
        Set<String> withOutbound = new HashSet<>();
        List<WorkflowEdge> edges = convertEdges(definition.getEdges());
        for (WorkflowEdge edge : edges) {
            String source = edge.getSource();
            String target = edge.getTarget();
            boolean fromIterate = iterateId.equals(source);
            boolean toIterate = iterateId.equals(target);
            if (fromIterate) {
                graph.addEdge(StateGraph.START, target);
                withInbound.add(target);
            } else if (toIterate) {
                graph.addEdge(source, StateGraph.END);
                withOutbound.add(source);
            } else if (blockIds.contains(source) && blockIds.contains(target)) {
                graph.addEdge(source, target);
                withInbound.add(target);
                withOutbound.add(source);
            }
        }

        // 兜底：边没声明入口 / 出口时，按入度出度补齐（只有一个子块时就是 START -> 块 -> END）
        for (WorkflowNode block : blocks) {
            if (!withInbound.contains(block.getId())) {
                graph.addEdge(StateGraph.START, block.getId());
            }
            if (!withOutbound.contains(block.getId())) {
                graph.addEdge(block.getId(), StateGraph.END);
            }
        }

        return graph.compile();
    }

    /**
     * 迭代源：取 inputs 里声明为 list 的那一项（取不到类型就退回第一个带来源引用的输入）。
     */
    private List<Object> readItems(OverAllState state) {
        NodeData data = definition.getData();
        if (data == null || data.getInputs() == null) {
            return List.of();
        }
        NodeField fallback = null;
        for (NodeField field : data.getInputs()) {
            if (field.getInput() == null) {
                continue;
            }
            if (fallback == null) {
                fallback = field;
            }
            if (isList(field)) {
                return NodeStateReader.toList(NodeStateReader.read(state, field.getInput()));
            }
        }
        return fallback == null ? List.of() : NodeStateReader.toList(NodeStateReader.read(state, fallback.getInput()));
    }

    private static boolean isList(NodeField field) {
        return LIST_TYPE.equalsIgnoreCase(field.getType())
                || (field.getInput() != null && LIST_TYPE.equalsIgnoreCase(field.getInput().getType()));
    }

    /**
     * 每轮子图的初始状态：以迭代节点 id 为命名空间，放入 index / item / 当前项别名 / variableParameters。
     */
    private Map<String, Object> buildRoundInput(OverAllState parentState, Object item, int index) {
        Map<String, Object> variables = new HashMap<>();
        variables.put(INDEX_VAR, index);
        variables.put(ITEM_VAR, item);
        for (String name : itemVarNames) {
            variables.put(name, item);
        }
        NodeData data = definition.getData();
        if (data != null && data.getVariableParameters() != null) {
            for (NodeField parameter : data.getVariableParameters()) {
                if (parameter.getName() == null || parameter.getInput() == null) {
                    continue;
                }
                variables.put(parameter.getName(), NodeStateReader.read(parentState, parameter.getInput()));
            }
        }
        Map<String, Object> roundInput = new HashMap<>();
        roundInput.put(definition.getId(), variables);
        return roundInput;
    }

    /** 收集单轮结果：取子图终态里出口节点产出的变量 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> collectRoundResult(NodeOutput lastOutput) {
        Map<String, Object> result = new LinkedHashMap<>();
        OverAllState state = lastOutput == null ? null : lastOutput.state();
        if (state == null) {
            return result;
        }
        for (String blockId : terminalBlockIds) {
            Object value = state.value(blockId).orElse(null);
            if (value instanceof Map<?, ?> outputMap) {
                result.putAll((Map<String, Object>) outputMap);
            }
        }
        return result;
    }

    /**
     * 汇总各轮结果，产出迭代节点的输出：按 {@code outputs} 声明的变量名，把每轮子块的同名产出聚合成列表。
     * <p>某一轮没有产出该变量时跳过该轮（不补 null），因此结果列表长度可能小于实际轮次；
     * 没有轮次或没有声明输出时返回空 Map。
     */
    private Map<String, Object> aggregate(List<Map<String, Object>> rounds) {
        Map<String, Object> output = new LinkedHashMap<>();
        NodeData data = definition.getData();
        List<NodeField> declared = data == null || data.getOutputs() == null ? List.of() : data.getOutputs();
        for (NodeField field : declared) {
            if (field.getName() == null) {
                continue;
            }
            List<Object> values = new ArrayList<>(rounds.size());
            for (Map<String, Object> round : rounds) {
                if (round != null && round.containsKey(field.getName())) {
                    values.add(round.get(field.getName()));
                }
            }
            output.put(field.getName(), values);
        }
        System.out.println("迭代节点 " + definition.getId() + " 共执行 " + rounds.size() + " 轮，输出: " + output);
        return output;
    }

    /** 子图的状态策略：默认键覆盖写（每轮状态独立） */
    private static KeyStrategyFactory subKeyStrategyFactory() {
        return () -> {
            Map<String, KeyStrategy> strategies = new HashMap<>();
            strategies.put("input", new ReplaceStrategy());
            strategies.put("output", new ReplaceStrategy());
            return strategies;
        };
    }

    /** blocks（原始 Map）反序列化成节点定义 */
    private static List<WorkflowNode> convertBlocks(List<Map<String, Object>> rawBlocks) {
        if (rawBlocks == null || rawBlocks.isEmpty()) {
            return List.of();
        }
        List<WorkflowNode> nodes = new ArrayList<>();
        for (Map<String, Object> raw : rawBlocks) {
            if (raw == null || raw.get("id") == null) {
                continue;
            }
            nodes.add(MAPPER.convertValue(raw, WorkflowNode.class));
        }
        return nodes;
    }

    /** edges（原始 Map，画布用 sourceNodeID / targetNodeID）反序列化成连线 */
    private static List<WorkflowEdge> convertEdges(List<Map<String, Object>> rawEdges) {
        if (rawEdges == null || rawEdges.isEmpty()) {
            return List.of();
        }
        List<WorkflowEdge> edges = new ArrayList<>();
        for (Map<String, Object> raw : rawEdges) {
            if (raw == null) {
                continue;
            }
            edges.add(MAPPER.convertValue(raw, WorkflowEdge.class));
        }
        return edges;
    }

    /** 当前项的变量名：扫描所有子块输入里引用了迭代节点自身的名字（如 input） */
    private static Set<String> collectItemVarNames(List<WorkflowNode> blocks, String iterateId) {
        Set<String> names = new LinkedHashSet<>();
        for (WorkflowNode block : blocks) {
            NodeData data = block.getData();
            if (data == null || data.getInputs() == null) {
                continue;
            }
            for (NodeField field : data.getInputs()) {
                if (field.getInput() != null && iterateId.equals(field.getInput().getBlockID())
                        && field.getInput().getName() != null) {
                    names.add(field.getInput().getName());
                }
            }
        }
        return names;
    }

    /** 迭代体出口节点：边中指向迭代节点自身的起点；没有这类边时取所有子块 */
    private static Set<String> collectTerminalBlockIds(List<Map<String, Object>> rawEdges, String iterateId) {
        Set<String> ids = new LinkedHashSet<>();
        for (WorkflowEdge edge : convertEdges(rawEdges)) {
            if (iterateId.equals(edge.getTarget()) && edge.getSource() != null && !iterateId.equals(edge.getSource())) {
                ids.add(edge.getSource());
            }
        }
        return ids;
    }
}
