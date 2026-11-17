package org.example.textflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import org.example.textflow.entity.NodeData;
import org.example.textflow.entity.NodeField;
import org.example.textflow.entity.WorkflowNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 开始节点直读口径（{@link StartNode}）的行为用例。
 *
 * <p>改造后开始节点不再回查数据库、也没有演示兜底：按 {@code data.outputs} 的声明直读
 * 状态顶层同名键（{@code POST /chat} 的 inputs 并入顶层状态），required 变量缺失直接报错。
 * 用例钉住四类行为：三变量齐全原样产出、required 缺失抛错、required 文本空白抛错、
 * 非 required 变量缺省给空值（保证产出变量在状态里存在）。
 */
class StartNodeTest {

    private static final String NODE_ID = "100001";

    /** 内置定义的开始节点声明：file_tree / description 必填，know 可选 */
    private static WorkflowNode definition() {
        NodeField fileTree = new NodeField();
        fileTree.setName("file_tree");
        fileTree.setType("list");
        fileTree.setRequired(true);
        fileTree.setDescription("本次作业提交的文件树");

        NodeField description = new NodeField();
        description.setName("description");
        description.setType("string");
        description.setRequired(true);

        NodeField know = new NodeField();
        know.setName("know");
        know.setType("list");

        NodeData data = new NodeData();
        data.setLabel("开始");
        data.setOutputs(new ArrayList<>(List.of(fileTree, description, know)));

        WorkflowNode node = new WorkflowNode();
        node.setId(NODE_ID);
        node.setType("1");
        node.setData(data);
        return node;
    }

    /** 业务输入直传的状态形态：inputs 经 initialState 并入顶层状态 */
    private static OverAllState stateOf(Map<String, Object> values) {
        return new OverAllState(new HashMap<>(values));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> outputsOf(Map<String, Object> result) {
        Map<String, Object> output = (Map<String, Object>) result.get(NODE_ID);
        assertThat(output).as("开始节点必须按自身 id 写出命名空间").isNotNull();
        return output;
    }

    /** 三变量齐全：原样透传进自己的命名空间（不做任何加工） */
    @Test
    void apply_passesThroughProvidedInputs() {
        List<Map<String, Object>> fileTree = List.of(Map.of("fileId", 122, "path", "test/教案.docx"));
        Map<String, Object> values = new HashMap<>();
        values.put("file_tree", fileTree);
        values.put("description", "以满分 100 分评分，60 分及格");
        values.put("know", List.of("1", "2"));

        Map<String, Object> result = new StartNode(definition()).apply(stateOf(values));

        Map<String, Object> outputs = outputsOf(result);
        assertThat(outputs.get("file_tree")).isEqualTo(fileTree);
        assertThat(outputs.get("description")).isEqualTo("以满分 100 分评分，60 分及格");
        assertThat(outputs.get("know")).isEqualTo(List.of("1", "2"));
    }

    /** required 变量缺失：整轮失败，消息点名变量与两条提供途径（不再静默兜底） */
    @Test
    void apply_throwsWhenRequiredVariableMissing() {
        Map<String, Object> values = new HashMap<>();
        values.put("description", "以满分 100 分评分，60 分及格");

        assertThatThrownBy(() -> new StartNode(definition()).apply(stateOf(values)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("file_tree")
                .hasMessageContaining("inputs");
    }

    /** required 文本只有空白：等同缺失，直接报错 */
    @Test
    void apply_throwsWhenDescriptionBlank() {
        Map<String, Object> values = new HashMap<>();
        values.put("file_tree", List.of(Map.of("fileId", 122)));
        values.put("description", "   ");

        assertThatThrownBy(() -> new StartNode(definition()).apply(stateOf(values)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("description");
    }

    /** required 列表为空列表：等同缺失，直接报错 */
    @Test
    void apply_throwsWhenFileTreeEmpty() {
        Map<String, Object> values = new HashMap<>();
        values.put("file_tree", List.of());
        values.put("description", "以满分 100 分评分");

        assertThatThrownBy(() -> new StartNode(definition()).apply(stateOf(values)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("file_tree");
    }

    /** 非 required 变量（know）缺省：给空列表而非缺失键，下游取键不会拿到 null */
    @Test
    void apply_defaultsOptionalKnowToEmptyList() {
        Map<String, Object> values = new HashMap<>();
        values.put("file_tree", List.of(Map.of("fileId", 122)));
        values.put("description", "以满分 100 分评分");

        Map<String, Object> result = new StartNode(definition()).apply(stateOf(values));

        Map<String, Object> outputs = outputsOf(result);
        assertThat(outputs.get("know")).isEqualTo(List.of());
    }

    /** 无声明输出：返回空命名空间（不抛错），未声明输出的开始节点仍可执行 */
    @Test
    void apply_returnsEmptyOutputsWhenNothingDeclared() {
        NodeData data = new NodeData();
        data.setLabel("开始");
        WorkflowNode node = new WorkflowNode();
        node.setId(NODE_ID);
        node.setType("1");
        node.setData(data);

        Map<String, Object> result = new StartNode(node).apply(stateOf(Map.of()));

        assertThat(outputsOf(result)).isEmpty();
    }

    // ========== 评分区间 / 自定义列：可选 + default ==========

    private static WorkflowNode definitionWithDefaults() {
        WorkflowNode node = definition();
        NodeField scoreMin = new NodeField();
        scoreMin.setName("score_min");
        scoreMin.setType("number");
        scoreMin.setDefaultValue(0);

        NodeField scoreMax = new NodeField();
        scoreMax.setName("score_max");
        scoreMax.setType("number");
        scoreMax.setDefaultValue(100);

        NodeField passScore = new NodeField();
        passScore.setName("pass_score");
        passScore.setType("number");
        passScore.setDefaultValue(60);

        NodeField customFields = new NodeField();
        customFields.setName("custom_fields");
        customFields.setType("list");
        customFields.setDefaultValue(List.of(Map.of("key", "deductions", "label", "扣分明细")));

        node.getData().getOutputs().addAll(List.of(scoreMin, scoreMax, passScore, customFields));
        return node;
    }

    /** 没提供 score_* / custom_fields：按定义里的 default 回填 */
    @Test
    void apply_usesDeclaredDefaultsWhenOptionalInputsAbsent() {
        Map<String, Object> values = new HashMap<>();
        values.put("file_tree", List.of(Map.of("fileId", 122)));
        values.put("description", "按维度评分");

        Map<String, Object> outputs = outputsOf(
                new StartNode(definitionWithDefaults()).apply(stateOf(values)));

        assertThat(outputs.get("score_min")).isEqualTo(0);
        assertThat(outputs.get("score_max")).isEqualTo(100);
        assertThat(outputs.get("pass_score")).isEqualTo(60);
        assertThat(outputs.get("custom_fields")).isEqualTo(List.of(Map.of("key", "deductions", "label", "扣分明细")));
    }

    /** 显式 0 / 空数组不能被默认值覆盖：0 是合法最低分，[] 是「本次没有自定义列」 */
    @Test
    void apply_keepsExplicitZeroAndEmptyList() {
        Map<String, Object> values = new HashMap<>();
        values.put("file_tree", List.of(Map.of("fileId", 122)));
        values.put("description", "按维度评分");
        values.put("score_min", 0);
        values.put("score_max", 20);
        values.put("pass_score", 12);
        values.put("custom_fields", List.of());

        Map<String, Object> outputs = outputsOf(
                new StartNode(definitionWithDefaults()).apply(stateOf(values)));

        assertThat(outputs.get("score_min")).isEqualTo(0);
        assertThat(outputs.get("score_max")).isEqualTo(20);
        assertThat(outputs.get("pass_score")).isEqualTo(12);
        assertThat(outputs.get("custom_fields")).isEqualTo(List.of());
    }
}
