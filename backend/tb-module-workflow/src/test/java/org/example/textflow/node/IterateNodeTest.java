package org.example.textflow.node;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.example.textflow.entity.NodeData;
import org.example.textflow.entity.NodeField;
import org.example.textflow.entity.NodeFieldRef;
import org.example.textflow.entity.WorkflowNode;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 迭代节点（{@link IterateNode}）的行为用例。
 *
 * <p>覆盖修复前的问题与必须保住的约定：
 * <ul>
 *   <li><b>出口边漏声明</b>：内部边只写了「迭代节点 → 子块」时，每轮结果收集必须与子图的兜底口径一致（不再恒为空）；</li>
 *   <li><b>单轮失败</b>：不再静默吞掉——每一轮都失败时整节点抛异常（流程标记失败），而不是「成功但没有结果」；</li>
 *   <li><b>轮数上限</b>：单次执行最多 {@code tb.workflow.iterate.max-rounds}（默认 50）轮；</li>
 *   <li><b>日志脱敏</b>：用户填写的子块定义不进日志；</li>
 *   <li><b>转发契约</b>：每轮把每个子块的 {@link NodeOutput} 转发给上层，且事件携带的轮次下标
 *       {@code state[迭代节点id].index} 正确——前端「第 n/N 轮」进度就靠它（见 {@code frontend/src/views/workflow2/utils/run-state.js}）。</li>
 * </ul>
 *
 * <p>夹具约定：迭代体子块统一用**开始节点**（type = 1）——开始节点按 {@code data.outputs} 声明直读
 * 状态、非 required 变量缺省给空值，因此声明一个非 required 的 {@code description} 即可保证
 * {@code apply} 必定成功，并在 {@code state[子块 id]} 下产出 {@code {description: ""}}；迭代源取
 * {@code state[100001].description}（声明为 list）；迭代节点声明的输出变量名与子块产出同名（{@code description}）。
 */
class IterateNodeTest {

    /** 迭代节点自身 id（与默认画布一致） */
    private static final String ITERATE_ID = "132996";

    /** 上游节点 id：产出迭代源 */
    private static final String SOURCE_ID = "100001";

    /** 迭代体子块 id */
    private static final String BLOCK_A = "200001";
    private static final String BLOCK_B = "200002";

    /** 单次执行的最大轮数（与 IterateNode 的默认值一致） */
    private static final int MAX_ROUNDS = 50;

    // ==================== 聚合口径 ====================

    @Test
    void control_singleBlock_aggregatesEveryRound() throws Exception {
        WorkflowNode definition = iterateDefinition(
                List.of(block(BLOCK_A)),
                List.of(edge(ITERATE_ID, BLOCK_A), edge(BLOCK_A, ITERATE_ID)));
        RecordingListener listener = new RecordingListener();

        Map<String, Object> result = apply(definition, List.of("甲", "乙", "丙"), listener);

        assertThat(valuesOf(result)).as("3 项应聚合出 3 条产出").hasSize(3);
        assertThat(listener.blockEvents()).as("子块应执行 3 次").isEqualTo(3);
    }

    /**
     * 出口边漏声明（内部边只有「迭代节点 → 子块」）时：子图靠兜底照常执行，
     * 结果收集也必须照常——出口节点取「没有出边的子块」，与 {@code buildSubGraph} 的兜底同一套口径。
     */
    @Test
    void fixed_missingExitEdge_stillAggregates() throws Exception {
        WorkflowNode definition = iterateDefinition(
                List.of(block(BLOCK_A)),
                // 故意不声明 BLOCK_A → ITERATE_ID
                List.of(edge(ITERATE_ID, BLOCK_A)));
        RecordingListener listener = new RecordingListener();

        Map<String, Object> result = apply(definition, List.of("甲", "乙", "丙"), listener);

        assertThat(listener.blockEvents()).isEqualTo(3);
        assertThat(valuesOf(result))
                .as("缺出口边时也要能收到每轮产出（旧实现这里是恒空的）")
                .hasSize(3);
    }

    // ==================== 单轮失败不再静默 ====================

    /**
     * 迭代体子块必定失败（开始节点声明了 required 输出、但子图状态里没有值——
     * {@code apply} 每轮抛 {@link IllegalStateException}）时：
     * 每一轮都失败 ⇒ 整节点抛异常，由上层把流程标记为失败，而不是「流程成功、结果为空」。
     */
    @Test
    void fixed_allRoundsFail_throwsInsteadOfSilentEmpty() {
        WorkflowNode definition = iterateDefinition(
                List.of(requiredMissingBlock(BLOCK_A)),
                List.of(edge(ITERATE_ID, BLOCK_A), edge(BLOCK_A, ITERATE_ID)));

        assertThatThrownBy(() -> apply(definition, List.of("甲", "乙", "丙"), new RecordingListener()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("3 轮全部执行失败");
    }

    // ==================== 轮数上限 ====================

    /** 可迭代项超过上限时只执行前 {@value #MAX_ROUNDS} 轮（默认上限，可用系统属性调整） */
    @Test
    void fixed_roundCapStopsAtMaxRounds() throws Exception {
        int itemCount = MAX_ROUNDS + 10;
        List<String> items = new ArrayList<>();
        for (int i = 0; i < itemCount; i++) {
            items.add("第 " + (i + 1) + " 项");
        }
        WorkflowNode definition = iterateDefinition(
                List.of(block(BLOCK_A)),
                List.of(edge(ITERATE_ID, BLOCK_A), edge(BLOCK_A, ITERATE_ID)));
        RecordingListener listener = new RecordingListener();

        Map<String, Object> result = apply(definition, items, listener);

        assertThat(valuesOf(result)).as("超出上限的轮次不再执行").hasSize(MAX_ROUNDS);
        assertThat(listener.blockEvents()).isEqualTo(MAX_ROUNDS);
    }

    // ==================== 转发契约（前端进度依赖） ====================

    /** 每轮把**每个**子块的事件都转发一次（前端据此收到 2 个子块 × 2 轮 = 4 个事件） */
    @Test
    void forwardsEveryChildEventOfEveryRound() throws Exception {
        RecordingListener single = new RecordingListener();
        apply(iterateDefinition(
                List.of(block(BLOCK_A)),
                List.of(edge(ITERATE_ID, BLOCK_A), edge(BLOCK_A, ITERATE_ID))),
                List.of("甲", "乙"), single);

        RecordingListener chained = new RecordingListener();
        apply(iterateDefinition(
                List.of(block(BLOCK_A), block(BLOCK_B)),
                List.of(edge(ITERATE_ID, BLOCK_A), edge(BLOCK_A, BLOCK_B), edge(BLOCK_B, ITERATE_ID))),
                List.of("甲", "乙"), chained);

        assertThat(single.blockEvents()).as("1 个子块 × 2 轮").isEqualTo(2);
        assertThat(chained.blockEvents()).as("2 个子块 × 2 轮").isEqualTo(4);
    }

    /**
     * 转发出去的子块事件必须带**权威轮次下标**：{@code state[迭代节点id].index}。
     * 前端「第 n/N 轮」进度按它取值（不能用「收到多少个子块事件」累加，否则含 N 个子块时会翻 N 倍）。
     */
    @Test
    void forwardedEventsCarryRoundIndex() throws Exception {
        WorkflowNode definition = iterateDefinition(
                List.of(block(BLOCK_A), block(BLOCK_B)),
                List.of(edge(ITERATE_ID, BLOCK_A), edge(BLOCK_A, BLOCK_B), edge(BLOCK_B, ITERATE_ID)));
        RecordingListener listener = new RecordingListener();

        apply(definition, List.of("甲", "乙"), listener);

        List<Object> indexes = listener.roundIndexes();
        assertThat(indexes)
                .as("4 个事件（2 个子块 × 2 轮）依次携带轮次下标 0、0、1、1")
                .containsExactly(0, 0, 1, 1);
    }

    // ==================== 日志脱敏 ====================

    /** 用户填写的子块定义（名称 / 输入 / 提示词）不进服务端日志 */
    @Test
    void noUserContentInLogs() throws Exception {
        WorkflowNode definition = iterateDefinition(
                List.of(block(BLOCK_A)),
                List.of(edge(ITERATE_ID, BLOCK_A), edge(BLOCK_A, ITERATE_ID)));

        String log = captureStdout(() -> apply(definition, List.of("张三/第三章习题.docx"),
                NodeEventListener.NO_OP));

        assertThat(log).as("子块名称（用户内容）不进日志").doesNotContain("子块 " + BLOCK_A);
        assertThat(log).as("整条子块定义（Lombok toString 转储）不进日志").doesNotContain("WorkflowNode(id=");
        assertThat(log).as("迭代源取值（用户内容）不进日志").doesNotContain("张三/第三章习题.docx");
    }

    // ==================== 夹具 ====================

    private static Map<String, Object> apply(WorkflowNode definition, List<String> items,
                                             NodeEventListener listener) throws Exception {
        return new IterateNode(definition, listener).apply(stateWithItems(items));
    }

    /** 捕获标准输出（日志与 println 都走 stdout） */
    private static String captureStdout(ThrowingRunnable action) throws Exception {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
            action.run();
        } finally {
            System.setOut(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    /** 迭代源状态：{@code state[SOURCE_ID].description = items} */
    private static OverAllState stateWithItems(List<String> items) {
        Map<String, Object> sourceOutput = new HashMap<>();
        sourceOutput.put("description", items);
        Map<String, Object> state = new HashMap<>();
        state.put(SOURCE_ID, sourceOutput);
        return new OverAllState(state);
    }

    /** 迭代节点定义：inputs 声明 list 型迭代源，outputs 声明 description */
    private static WorkflowNode iterateDefinition(List<Map<String, Object>> blocks,
                                                  List<Map<String, Object>> edges) {
        NodeField input = new NodeField();
        input.setName("items");
        input.setType("list");
        NodeFieldRef ref = new NodeFieldRef();
        ref.setBlockID(SOURCE_ID);
        ref.setName("description");
        ref.setType("list");
        input.setInput(ref);

        NodeField output = new NodeField();
        output.setName("description");

        NodeData data = new NodeData();
        data.setLabel("迭代");
        data.setInputs(new ArrayList<>(List.of(input)));
        data.setOutputs(new ArrayList<>(List.of(output)));

        WorkflowNode node = new WorkflowNode();
        node.setId(ITERATE_ID);
        node.setType("4");
        node.setData(data);
        node.setBlocks(blocks);
        node.setEdges(edges);
        return node;
    }

    /**
     * 子块：开始节点（type = 1），声明一个非 required 的输出变量 {@code description}——
     * 开始节点按声明直读状态、缺省给空串，因此 apply 必定成功且每轮产出 {@code {description: ""}}。
     */
    private static Map<String, Object> block(String id) {
        Map<String, Object> output = new HashMap<>();
        output.put("name", "description");
        output.put("type", "string");
        Map<String, Object> data = new HashMap<>();
        data.put("label", "子块 " + id);
        data.put("outputs", List.of(output));
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("type", "1");
        map.put("data", data);
        return map;
    }

    /**
     * 必定失败的子块：开始节点声明了 required 输出变量，但子图状态里没有同名键——
     * {@code apply} 每轮都抛 {@link IllegalStateException}（不再依赖人工确认节点的空指针）。
     */
    private static Map<String, Object> requiredMissingBlock(String id) {
        Map<String, Object> output = new HashMap<>();
        output.put("name", "plan");
        output.put("type", "string");
        output.put("required", true);
        Map<String, Object> data = new HashMap<>();
        data.put("label", "子块 " + id);
        data.put("outputs", List.of(output));
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("type", "1");
        map.put("data", data);
        return map;
    }

    /** 迭代体内部边（画布结构：sourceNodeID / targetNodeID） */
    private static Map<String, Object> edge(String source, String target) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", "e-" + source + "-" + target);
        map.put("sourceNodeID", source);
        map.put("targetNodeID", target);
        return map;
    }

    /** 迭代节点的输出变量（description）聚合结果 */
    @SuppressWarnings("unchecked")
    private static List<Object> valuesOf(Map<String, Object> result) {
        Map<String, Object> output = (Map<String, Object>) result.get(ITERATE_ID);
        assertThat(output).as("迭代节点必须写出自己的输出变量").isNotNull();
        return (List<Object>) output.get("description");
    }

    /** 允许抛异常的动作 */
    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    /** 记录转发事件：统计子块执行次数与事件携带的轮次下标 */
    private static final class RecordingListener implements NodeEventListener {

        private final List<NodeOutput> outputs = new ArrayList<>();

        @Override
        public void onEvent(NodeOutput output) {
            outputs.add(output);
        }

        /** 子块执行事件数（不含 START / END） */
        long blockEvents() {
            return outputs.stream()
                    .filter(output -> BLOCK_A.equals(output.node()) || BLOCK_B.equals(output.node()))
                    .count();
        }

        /** 每个事件携带的轮次下标 {@code state[ITERATE_ID].index} */
        @SuppressWarnings("unchecked")
        List<Object> roundIndexes() {
            List<Object> indexes = new ArrayList<>();
            for (NodeOutput output : outputs) {
                if (!BLOCK_A.equals(output.node()) && !BLOCK_B.equals(output.node())) {
                    continue;
                }
                OverAllState state = output.state();
                Object iterateState = state == null ? null : state.value(ITERATE_ID).orElse(null);
                Object index = iterateState instanceof Map<?, ?> map ? ((Map<String, Object>) map).get("index") : null;
                indexes.add(index);
            }
            return indexes;
        }
    }
}
