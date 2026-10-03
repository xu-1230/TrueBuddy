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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

import java.time.Duration;
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
 *
 * <p>健壮性约定：
 * <ul>
 *   <li><b>出口节点与出口边同一套口径</b>：出口节点取「没有出边的子块」，与 {@link #buildSubGraph} 把这类子块接到
 *       {@code END} 的兜底完全一致——迭代体内部边漏声明出口边时，子图仍会执行且结果照常收集；</li>
 *   <li><b>单轮失败不再静默</b>：某一轮抛异常时记 ERROR 日志（含堆栈）、该轮的产出变量按 {@code null} 补齐
 *       （结果列表长度始终等于执行的轮次数，界面上「空值」即这一轮没产出）；**每一轮都失败**时整节点抛异常，
 *       由上层把流程标记为失败，不再出现「流程成功但没有结果」；</li>
 *   <li><b>轮数上限与单轮超时</b>：默认最多 {@value #DEFAULT_MAX_ROUNDS} 轮、单轮 {@value #DEFAULT_ROUND_TIMEOUT_MS} ms，
 *       可用系统属性 {@value #MAX_ROUNDS_PROPERTY} / {@value #ROUND_TIMEOUT_PROPERTY} 覆盖；</li>
 *   <li><b>日志不含用户内容</b>：只记节点 / 子块 id 与变量名，用户填写的节点定义与渲染后的提示词不进日志。</li>
 * </ul>
 */
public class IterateNode implements NodeAction {

    private static final Logger log = LoggerFactory.getLogger(IterateNode.class);

    /** 变量类型为 list 时视为可迭代的输入来源 */
    private static final String LIST_TYPE = "list";

    /** 每轮注入的轮次下标变量名 */
    private static final String INDEX_VAR = "index";

    /** 每轮注入的当前项变量名 */
    private static final String ITEM_VAR = "item";

    /** 轮数上限的系统属性名 */
    private static final String MAX_ROUNDS_PROPERTY = "tb.workflow.iterate.max-rounds";

    /** 单轮超时的系统属性名 */
    private static final String ROUND_TIMEOUT_PROPERTY = "tb.workflow.iterate.round-timeout-ms";

    /** 单次执行的最大轮数（默认值），超出部分不再执行 */
    private static final int DEFAULT_MAX_ROUNDS = 50;

    /** 单轮执行的超时（默认值，毫秒） */
    private static final long DEFAULT_ROUND_TIMEOUT_MS = 120_000L;

    /** 单次执行的最大轮数 */
    private static final int MAX_ROUNDS = Integer.getInteger(MAX_ROUNDS_PROPERTY, DEFAULT_MAX_ROUNDS);

    /** 单轮执行的超时（毫秒） */
    private static final long ROUND_TIMEOUT_MS = Long.getLong(ROUND_TIMEOUT_PROPERTY, DEFAULT_ROUND_TIMEOUT_MS);

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

    /** 迭代体子块里的大模型节点使用的对话模型客户端，为 null 时子块只产出模拟结果 */
    private final ChatClient chatClient;

    public IterateNode(WorkflowNode definition, NodeEventListener listener) {
        this(definition, listener, null);
    }

    public IterateNode(WorkflowNode definition, NodeEventListener listener, ChatClient chatClient) {
        this.definition = definition;
        this.listener = listener == null ? NodeEventListener.NO_OP : listener;
        this.chatClient = chatClient;
        this.blocks = convertBlocks(definition.getBlocks());
        this.itemVarNames = collectItemVarNames(this.blocks, definition.getId());
        this.terminalBlockIds = collectTerminalBlockIds(definition.getEdges(), definition.getId(), this.blocks);
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        List<Object> items = readItems(state);
        if (items.isEmpty()) {
            // 没有可迭代项也要按 outputs 声明写空列表，否则下游取变量会拿到 null
            log.debug("迭代节点 {} 没有可迭代的列表输入，返回空汇总", definition.getId());
            return Map.of(definition.getId(), aggregate(List.of(), Set.of()));
        }

        CompiledGraph graph = subGraph();
        if (graph == null) {
            // 没有迭代体子块：同样返回空汇总，保证输出变量在状态里存在
            return Map.of(definition.getId(), aggregate(List.of(), Set.of()));
        }

        int rounds = Math.min(items.size(), MAX_ROUNDS);
        if (items.size() > MAX_ROUNDS) {
            log.warn("迭代节点 {} 的可迭代项有 {} 个，超过上限 {}，只执行前 {} 轮（可用 -D{}= 调整）",
                    definition.getId(), items.size(), MAX_ROUNDS, rounds, MAX_ROUNDS_PROPERTY);
        }

        List<Map<String, Object>> roundResults = new ArrayList<>(rounds);
        Set<Integer> failedRounds = new LinkedHashSet<>();
        for (int index = 0; index < rounds; index++) {
            Map<String, Object> roundInput = buildRoundInput(state, items.get(index), index);
            try {
                List<NodeOutput> outputs = graph.stream(roundInput)
                        .collectList()
                        .block(Duration.ofMillis(ROUND_TIMEOUT_MS));
                if (outputs == null || outputs.isEmpty()) {
                    failedRounds.add(index);
                    roundResults.add(Map.of());
                    log.error("迭代节点 {} 第 {} 轮没有产生任何节点输出（超时 {} ms），该轮产出按空值补齐",
                            definition.getId(), index, ROUND_TIMEOUT_MS);
                    continue;
                }
                // 迭代体内部的节点事件转发给上层（SSE），否则前端只能看到迭代节点整体执行一次
                for (NodeOutput output : outputs) {
                    listener.onEvent(output);
                }
                Map<String, Object> roundResult = collectRoundResult(outputs.get(outputs.size() - 1));
                roundResults.add(roundResult);
                log.debug("迭代节点 {} 第 {} 轮完成，产出变量: {}", definition.getId(), index, roundResult.keySet());
            } catch (Exception e) {
                // 单轮失败不再静默：记 ERROR 日志（含堆栈），并把该轮标记为失败，让汇总长度与轮次对齐
                failedRounds.add(index);
                roundResults.add(Map.of());
                log.error("迭代节点 {} 第 {} 轮执行失败，该轮产出按空值补齐", definition.getId(), index, e);
            }
        }

        if (rounds > 0 && failedRounds.size() == rounds) {
            // 每一轮都失败：抛异常让上层把流程标记为失败，避免出现「流程成功但没有结果」
            throw new IllegalStateException("迭代节点 " + definition.getId() + " 的 " + rounds + " 轮全部执行失败");
        }

        return Map.of(definition.getId(), aggregate(roundResults, failedRounds));
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
                log.debug("迭代节点 {} 没有迭代体子块，跳过", definition.getId());
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
            // 只记子块 id：用户填写的子块定义（名称 / 输入 / 提示词等）不进日志
            log.debug("迭代节点 {} 编译子块 {}", definition.getId(), block.getId());
            graph.addNode(block.getId(), NodeActionFactory.of(block, listener, chatClient));
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
     * <p>**失败轮次按 {@code null} 补齐**（结果列表长度始终等于执行的轮次数，界面上该轮显示「空值」，
     * 不会像旧实现那样让列表悄悄变短）；成功轮次里本来就没有该变量时仍跳过该轮；
     * 没有轮次或没有声明输出时返回空 Map。
     *
     * @param rounds       每轮的产出（失败轮次是空 Map）
     * @param failedRounds 失败轮次的下标集合
     */
    private Map<String, Object> aggregate(List<Map<String, Object>> rounds, Set<Integer> failedRounds) {
        Map<String, Object> output = new LinkedHashMap<>();
        NodeData data = definition.getData();
        List<NodeField> declared = data == null || data.getOutputs() == null ? List.of() : data.getOutputs();
        for (NodeField field : declared) {
            if (field.getName() == null) {
                continue;
            }
            List<Object> values = new ArrayList<>(rounds.size());
            for (int index = 0; index < rounds.size(); index++) {
                if (failedRounds.contains(index)) {
                    values.add(null);
                    continue;
                }
                Map<String, Object> round = rounds.get(index);
                if (round != null && round.containsKey(field.getName())) {
                    values.add(round.get(field.getName()));
                }
            }
            output.put(field.getName(), values);
        }
        // 只记变量名与轮次数，产出内容（用户数据）不进日志
        log.debug("迭代节点 {} 共执行 {} 轮（失败 {} 轮），输出变量: {}",
                definition.getId(), rounds.size(), failedRounds.size(), output.keySet());
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

    /**
     * 迭代体出口节点：**没有出边的子块**（出边指「指向另一个子块」的边；指向迭代节点自身的边表示接 {@code END}）。
     * <p>必须与 {@link #buildSubGraph} 的兜底同一套口径——那里会给没有出边的子块补一条到 {@code END} 的边。
     * 旧实现只认「指向迭代节点自身的边」，漏声明出口边时子图照常执行、但每轮结果收集恒为空
     * （表现为「流程成功但没有结果」），因此这里改为直接从子图结构推导。
     */
    private static Set<String> collectTerminalBlockIds(List<Map<String, Object>> rawEdges, String iterateId,
                                                       List<WorkflowNode> blocks) {
        Set<String> blockIds = new LinkedHashSet<>();
        for (WorkflowNode block : blocks) {
            blockIds.add(block.getId());
        }
        Set<String> withOutbound = new LinkedHashSet<>();
        for (WorkflowEdge edge : convertEdges(rawEdges)) {
            String source = edge.getSource();
            String target = edge.getTarget();
            if (source == null || iterateId.equals(source) || !blockIds.contains(source)) {
                continue;
            }
            if (target != null && !iterateId.equals(target) && blockIds.contains(target)) {
                withOutbound.add(source);
            }
        }
        Set<String> ids = new LinkedHashSet<>();
        for (String blockId : blockIds) {
            if (!withOutbound.contains(blockId)) {
                ids.add(blockId);
            }
        }
        return ids;
    }
}
