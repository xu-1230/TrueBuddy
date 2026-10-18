package org.example.textflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.example.textflow.entity.NodeData;
import org.example.textflow.entity.NodeField;
import org.example.textflow.entity.WorkflowNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 输出节点（type = 6）：把上游的产出**按变量名原样透传**成自己的产出，作为"要展示的结果"的显式声明。
 *
 * <p>与其它节点的口径差异：它**只声明 {@code data.inputs}**，不声明 {@code data.outputs}——
 * 产出名就是这些 `inputs` 的变量名（与 {@link HumanNode#apply} 把提交值按变量名回写自己命名空间
 * 是同一范式）。理由：再引一层 inputs→outputs 的映射就要定义"哪个 input 喂哪个 output"的对应关系
 * 与校验，而这里没有任何新信息可加。
 *
 * <p>三个容易踩的点：
 * <ul>
 *   <li><b>它是"声明"而不是"唯一结果出口"</b>：输出节点可以出现多个。迭代体内的那个把
 *       "单份（一个学生）要展示什么"声明出来，它的产出被 {@link IterateNode#aggregate}
 *       按同名聚合到迭代节点上——**迭代体内的节点产出不会出现在父状态的
 *       {@code state[节点id]} 里**（子图状态独立），所以页面读到的是"迭代节点的输出列表"；</li>
 *   <li><b>取不到值就是 {@code null}，不抛异常</b>：与其它节点一致（上游没产出时静默为 null），
 *       由运行前校验负责在编辑期发现"引用取不到值"；</li>
 *   <li><b>日志不含用户内容</b>：只记变量名与个数。</li>
 * </ul>
 */
public class OutputNode implements NodeAction {

    private static final Logger log = LoggerFactory.getLogger(OutputNode.class);

    private final WorkflowNode definition;

    public OutputNode(WorkflowNode definition) {
        this.definition = definition;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        NodeData nodeData = definition.getData();
        Map<String, Object> outputs = new LinkedHashMap<>();
        if (nodeData != null && nodeData.getInputs() != null) {
            for (NodeField field : nodeData.getInputs()) {
                if (field == null || field.getName() == null || field.getInput() == null) {
                    continue;
                }
                outputs.put(field.getName(), NodeStateReader.read(state, field.getInput()));
            }
        }
        log.debug("输出节点 {} 组装产出 {} 个变量: {}", definition.getId(), outputs.size(), outputs.keySet());
        return Map.of(definition.getId(), outputs);
    }

}
