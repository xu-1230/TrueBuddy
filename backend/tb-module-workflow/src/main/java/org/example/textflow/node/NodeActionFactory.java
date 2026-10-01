package org.example.textflow.node;

import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeActionWithConfig;
import com.alibaba.cloud.ai.graph.action.InterruptableAction;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.example.textflow.entity.WorkflowNode;

/**
 * 按节点定义（type）创建对应的 graph 动作。
 */
public final class NodeActionFactory {

    private NodeActionFactory() {
    }

    /**
     * 创建可加入 {@code StateGraph} 的节点动作（不转发内部事件）。
     */
    public static AsyncNodeActionWithConfig of(WorkflowNode node) {
        return of(node, NodeEventListener.NO_OP);
    }

    /**
     * 创建可加入 {@code StateGraph} 的节点动作。
     * <p>{@code listener} 会传给迭代节点，用于把迭代体子图内部的节点事件转发到上层；
     * 迭代体里再嵌套迭代节点时，回调会继续向下传递。
     * <p>实现了 {@link InterruptableAction} 的节点（如人工确认）会额外包一层中断检测。
     */
    public static AsyncNodeActionWithConfig of(WorkflowNode node, NodeEventListener listener) {
        NodeAction nodeAction = create(node, listener);
        AsyncNodeAction asyncNodeAction = AsyncNodeAction.node_async(nodeAction);
        if (nodeAction instanceof InterruptableAction interruptable) {
            return new AsyncNodeActionWithConfig.InterruptableAsyncNodeActionWrapper(asyncNodeAction, interruptable);
        }
        return AsyncNodeActionWithConfig.of(asyncNodeAction);
    }

    /**
     * 节点类型 → 动作实现。
     * <p>画布导出的 type 是数字编码：1=开始、2=结束、3=大模型、4=迭代、5=人工确认；
     * 旧的语义化字符串写法（input / llm / human / output / iterate）已不再兼容。
     */
    private static NodeAction create(WorkflowNode node, NodeEventListener listener) {
        String type = node.getType() == null ? "" : node.getType().trim();
        return switch (type) {
            case "1" -> new StartNode(node);
            case "2" -> new EndNode(node);
            case "3" -> new LlmNode(node);
            case "4" -> new IterateNode(node, listener);
            case "5" -> new HumanNode(node);
            default -> throw new IllegalArgumentException("不支持的节点类型: " + node.getType());
        };
    }

}
