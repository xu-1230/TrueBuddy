package org.example.textflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.example.textflow.entity.WorkflowNode;

import java.util.Map;

/**
 * 结束节点（type = output）：输出最终渲染内容。
 */
public class EndNode implements NodeAction {

    private final WorkflowNode definition;

    public EndNode(WorkflowNode definition) {
        this.definition = definition;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {

        // TODO 组装最终返回内容
//        System.out.println(state);
        return Map.of();
    }

}
