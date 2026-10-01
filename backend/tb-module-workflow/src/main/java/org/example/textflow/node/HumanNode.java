package org.example.textflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.InterruptableAction;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.example.textflow.entity.WorkflowNode;

import java.util.Map;
import java.util.Optional;

/**
 * 人工确认节点（type = 5）：执行前中断流程，等待人工确认后再继续。
 */
public class HumanNode implements NodeAction, InterruptableAction {

    private final WorkflowNode definition;

    public HumanNode(WorkflowNode definition) {
        this.definition = definition;
    }

    /**
     * 中断点：进入节点之前先中断，把待确认内容抛给调用方，人工确认后 resume 才会真正执行 {@link #apply}。
     */
    @Override
    public Optional<InterruptionMetadata> interrupt(String nodeId, OverAllState state, RunnableConfig config) {
        // resume 时通过 RunnableConfig.Builder.addHumanFeedback(...) 带上人工反馈，此时不再中断
        if (config.metadata(RunnableConfig.HUMAN_FEEDBACK_METADATA_KEY).isPresent()) {
            return Optional.empty();
        }
        System.out.println("中断，等待人工确认: " + nodeId);
        return Optional.of(InterruptionMetadata.builder(nodeId, state)
                .addMetadata("nodeId", nodeId)
                .addMetadata("label", definition.getData().getLabel())
                .build());
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        System.out.println("执行节点: " + definition.getId() + " (" + definition.getData().getLabel() + ")");
        // TODO 读取人工确认结果，写入 definition 中声明的输出变量
        return Map.of();
    }

}
