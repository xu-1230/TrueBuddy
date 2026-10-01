package org.example.textflow.node;

import com.alibaba.cloud.ai.graph.NodeOutput;

/**
 * 节点执行事件的转发回调。
 * <p>迭代节点（{@link IterateNode}）会在节点内部执行迭代体子图，子图里每个子块产生的 {@link NodeOutput}
 * 无法通过父图的流直接冒泡出去，因此由迭代节点主动回调本接口，把事件交给上层（如 SSE 推送）。
 */
@FunctionalInterface
public interface NodeEventListener {

    /** 空实现：没有推送目标（如直接 invoke 的 /text 接口）时使用，避免到处判空。 */
    NodeEventListener NO_OP = output -> {
    };

    /**
     * 收到一个节点输出事件。
     *
     * @param output 节点输出（可能是 {@link com.alibaba.cloud.ai.graph.action.InterruptionMetadata}）
     */
    void onEvent(NodeOutput output);
}
