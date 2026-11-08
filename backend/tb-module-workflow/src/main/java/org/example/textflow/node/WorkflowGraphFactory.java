package org.example.textflow.node;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import org.example.textflow.entity.WorkflowEdge;
import org.example.textflow.entity.WorkflowNode;
import org.example.textflow.entity.WorkflowProcessRespVO;
import org.example.textflow.model.ChatProtocol;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 画布定义 → 可执行图。原本内联在 {@code WorkflowController} 里，{@code /chat} 也要建图，
 * 故抽成工厂供「执行入口」与联调接口共用。
 */
public final class WorkflowGraphFactory {

    private WorkflowGraphFactory() {
    }

    /**
     * 按定义装配节点、边、入口出口并编译。
     *
     * @param process    画布定义（{@code {nodes, edges}}）
     * @param listener   节点事件回调：迭代体子图内部子块的 {@link com.alibaba.cloud.ai.graph.NodeOutput}
     *                   也经它转发到上层
     * @param chatClient 大模型节点的对话模型客户端，可为 null（单测场景）
     * @param saver      检查点存储（中断后 resume 依赖它）
     */
    public static CompiledGraph createGraph(WorkflowProcessRespVO process, NodeEventListener listener,
                                           ChatClient chatClient, MemorySaver saver) throws GraphStateException {
        return createGraph(process, listener, chatClient, saver, List.of());
    }

    /**
     * 同 {@link #createGraph(WorkflowProcessRespVO, NodeEventListener, ChatClient, MemorySaver)}，
     * 并给大模型节点（含迭代体里的大模型节点）挂上可自主调用的工具。
     *
     * @param toolCallbacks 工具回调；为空时节点不具备工具能力
     */
    public static CompiledGraph createGraph(WorkflowProcessRespVO process, NodeEventListener listener,
                                           ChatClient chatClient, MemorySaver saver,
                                           List<ToolCallback> toolCallbacks) throws GraphStateException {
        StateGraph stateGraph = new StateGraph("text-flow", keyStrategyFactory());

        for (WorkflowNode node : process.getNodes()) {
            stateGraph.addNode(node.getId(),
                    NodeActionFactory.of(node, listener, chatClient, toolCallbacks));
        }
        for (WorkflowEdge edge : process.getEdges()) {
            stateGraph.addEdge(edge.getSource(), edge.getTarget());
        }

        // 无入边的节点接 START、无出边的节点接 END，定义里不必显式声明这两种节点
        Set<String> sources = process.getEdges().stream().map(WorkflowEdge::getSource).collect(Collectors.toSet());
        Set<String> targets = process.getEdges().stream().map(WorkflowEdge::getTarget).collect(Collectors.toSet());
        for (WorkflowNode node : process.getNodes()) {
            if (!targets.contains(node.getId())) {
                stateGraph.addEdge(StateGraph.START, node.getId());
            }
            if (!sources.contains(node.getId())) {
                stateGraph.addEdge(node.getId(), StateGraph.END);
            }
        }

        return stateGraph.compile(CompileConfig.builder()
                .saverConfig(SaverConfig.builder().register(saver).build())
                .releaseThread(false)
                .build());
    }

    /**
     * 状态键策略表。
     *
     * <p>空串键沿用既有口径（变量类型为 list 时视为可累积的「记忆」变量）。
     * {@link ChatProtocol#HUMAN_FEEDBACK_KEY} 必须**显式注册**：检查点合并时
     * {@code OverAllState.input(checkpointState)} 会按策略表过滤，未注册的键会被静默丢弃，
     * 那样续跑注入的人工反馈就到不了人工确认节点。
     */
    public static KeyStrategyFactory keyStrategyFactory() {
        return () -> {
            Map<String, KeyStrategy> keyStrategyMap = new HashMap<>();
            keyStrategyMap.put("", new AppendStrategy());
            keyStrategyMap.put(ChatProtocol.HUMAN_FEEDBACK_KEY, new ReplaceStrategy());
            return keyStrategyMap;
        };
    }

}
