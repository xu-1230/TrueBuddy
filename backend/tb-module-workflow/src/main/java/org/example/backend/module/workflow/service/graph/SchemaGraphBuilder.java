package org.example.backend.module.workflow.service.graph;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.KeyStrategyFactoryBuilder;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncNodeActionWithConfig;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.framework.common.exception.ServiceException;
import org.example.backend.module.workflow.config.WorkflowProperties;
import org.example.backend.module.workflow.mock.MockLlmEngine;
import org.example.backend.module.workflow.model.schema.GraphNodeEdge;
import org.example.backend.module.workflow.model.schema.OutputParamDef;
import org.example.backend.module.workflow.model.schema.SchemaNodeDef;
import org.example.backend.module.workflow.model.schema.WorkflowGraphModel;
import org.example.backend.module.workflow.service.node.HumanConfirmNodeAction;
import org.example.backend.module.workflow.service.node.LoopNodeAction;
import org.example.backend.module.workflow.service.node.MockLlmNodeAction;
import org.example.backend.module.workflow.service.node.WorkflowNodeAction;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import static org.example.backend.framework.common.exception.util.ServiceExceptionUtil.exception;
import static org.example.backend.module.workflow.enums.ErrorCodeConstants.WORKFLOW_GRAPH_BUILD_FAILED;
import static org.example.backend.module.workflow.enums.ErrorCodeConstants.WORKFLOW_NODE_TYPE_UNSUPPORTED;

/**
 * 图编译器：把归一化后的画布模型编译为 {@link CompiledGraph}。
 *
 * <ul>
 *   <li>开始 / 结束节点不注册为图节点：分别映射为 {@link StateGraph#START} / {@link StateGraph#END}；</li>
 *   <li>节点输出统一通过 {@code {nodeId}.{outputName}} 键写入状态，键策略默认 REPLACE；</li>
 *   <li>注册 {@link MemorySaver} 检查点，保留各次执行的中间状态；</li>
 *   <li>按 schema 指纹缓存已编译图，避免每次请求重复建图。</li>
 * </ul>
 */
@Slf4j
@Component
public class SchemaGraphBuilder {

    /** 循环并发上限，防止画布配置过大导致线程膨胀 */
    private static final int MAX_LOOP_CONCURRENCY = 8;

    private final RefResolver refResolver;

    private final MockLlmEngine mockLlmEngine;

    private final LlmPayloadParser payloadParser;

    private final WorkflowProperties properties;

    /** 循环节点并发执行器；串行模式为 null */
    private final ExecutorService loopExecutor;

    private final Map<String, CompiledGraph> compiledCache = new ConcurrentHashMap<>();

    public SchemaGraphBuilder(RefResolver refResolver, MockLlmEngine mockLlmEngine,
                              LlmPayloadParser payloadParser, WorkflowProperties properties) {
        this.refResolver = refResolver;
        this.mockLlmEngine = mockLlmEngine;
        this.payloadParser = payloadParser;
        this.properties = properties;
        int concurrency = Math.max(1, Math.min(properties.getLoopConcurrency(), MAX_LOOP_CONCURRENCY));
        this.loopExecutor = concurrency > 1 ? Executors.newFixedThreadPool(concurrency, newLoopThreadFactory()) : null;
        if (concurrency > 1) {
            log.info("[SchemaGraphBuilder][循环节点启用并发执行] concurrency={}", concurrency);
        }
    }

    /** 取（或构建）指定 schema 对应的已编译图 */
    public CompiledGraph getOrBuild(String fingerprint, WorkflowGraphModel model) {
        return compiledCache.computeIfAbsent(fingerprint, key -> build(model));
    }

    /** 编译图 */
    public CompiledGraph build(WorkflowGraphModel model) {
        if (model.getExecutableNodes().isEmpty()) {
            throw exception(WORKFLOW_GRAPH_BUILD_FAILED, "工作流中没有可执行节点");
        }
        StateGraph stateGraph = new StateGraph(model.getName(), buildKeyStrategyFactory(model));
        try {
            for (SchemaNodeDef node : model.getExecutableNodes()) {
                stateGraph.addNode(node.getId(), toGraphNode(node));
            }
            registerEdges(stateGraph, model);
            CompiledGraph compiled = stateGraph.compile(CompileConfig.builder()
                    .saverConfig(SaverConfig.builder().register(new MemorySaver()).build())
                    .build());
            log.info("[build][工作流图编译完成] name={}, nodes={}, edges={}",
                    model.getName(), model.getExecutableNodes().size(), model.getEdges().size());
            return compiled;
        } catch (ServiceException e) {
            throw e;
        } catch (GraphStateException e) {
            throw exception(WORKFLOW_GRAPH_BUILD_FAILED, e.getMessage());
        } catch (Exception e) {
            throw exception(WORKFLOW_GRAPH_BUILD_FAILED, e.getMessage());
        }
    }

    /**
     * 构建状态键策略：必须显式注册每一个用到的状态键。
     *
     * <p>注意：graph-core 的 {@code KeyStrategyFactoryBuilder.defaultStrategy(...)} 在 {@code build()}
     * 时被忽略（策略表为空），而断点恢复时 {@code OverAllState.input(checkpointState)} 会按
     * {@code keyStrategies} 过滤待合并的键——未注册的键会被静默丢弃，导致恢复后状态为空。
     * 因此这里按图模型枚举「{@code {nodeId}.{outputName}} + 开始节点裸变量名」并统一使用 REPLACE。
     */
    private KeyStrategyFactory buildKeyStrategyFactory(WorkflowGraphModel model) {
        Map<String, KeyStrategy> strategies = new HashMap<>();
        SchemaNodeDef startNode = model.getStartNode();
        if (startNode.getData() != null && startNode.getData().getOutputs() != null) {
            for (OutputParamDef output : startNode.getData().getOutputs()) {
                if (output == null || output.getName() == null || output.getName().isBlank()) {
                    continue;
                }
                strategies.put(RefResolver.stateKey(startNode.getId(), output.getName()), KeyStrategy.REPLACE);
                // 裸变量名：便于提示词模板直接引用开始节点输入
                strategies.put(output.getName(), KeyStrategy.REPLACE);
            }
        }
        for (SchemaNodeDef node : model.getNodeIndex().values()) {
            registerOutputStrategies(node, strategies);
        }
        log.debug("[buildKeyStrategyFactory][状态键策略注册完成] keys={}", strategies.size());
        return new KeyStrategyFactoryBuilder()
                .addStrategies(strategies)
                .defaultStrategy(KeyStrategy.REPLACE)
                .build();
    }

    /** 注册某个节点所有输出的状态键（含循环内部子节点） */
    private void registerOutputStrategies(SchemaNodeDef node, Map<String, KeyStrategy> strategies) {
        if (node.getData() != null && node.getData().getOutputs() != null) {
            for (OutputParamDef output : node.getData().getOutputs()) {
                if (output != null && output.getName() != null && !output.getName().isBlank()) {
                    strategies.put(RefResolver.stateKey(node.getId(), output.getName()), KeyStrategy.REPLACE);
                }
            }
        }
        if (node.getBlocks() != null) {
            for (SchemaNodeDef block : node.getBlocks()) {
                if (block != null) {
                    registerOutputStrategies(block, strategies);
                }
            }
        }
    }

    /** 注册连线：开始 / 结束节点 id 分别替换为图的 START / END，并补齐缺失的首尾连线 */
    private void registerEdges(StateGraph stateGraph, WorkflowGraphModel model) throws GraphStateException {
        String startId = model.getStartNode().getId();
        String endId = model.getEndNode().getId();
        boolean hasStartEdge = false;
        boolean hasEndEdge = false;
        for (GraphNodeEdge edge : model.getEdges()) {
            String source = startId.equals(edge.source()) ? StateGraph.START : edge.source();
            String target = endId.equals(edge.target()) ? StateGraph.END : edge.target();
            if (StateGraph.START.equals(source)) {
                hasStartEdge = true;
            }
            if (StateGraph.END.equals(target)) {
                hasEndEdge = true;
            }
            stateGraph.addEdge(source, target);
        }
        String firstNode = model.getExecutableNodes().get(0).getId();
        String lastNode = model.getExecutableNodes().get(model.getExecutableNodes().size() - 1).getId();
        if (!hasStartEdge) {
            log.warn("[registerEdges][缺少开始节点连线，自动连接到首个可执行节点] nodeId={}", firstNode);
            stateGraph.addEdge(StateGraph.START, firstNode);
        }
        if (!hasEndEdge) {
            log.warn("[registerEdges][缺少结束节点连线，自动从末个可执行节点连接到结束] nodeId={}", lastNode);
            stateGraph.addEdge(lastNode, StateGraph.END);
        }
    }

    /** 把画布节点转换为 graph 节点动作 */
    private AsyncNodeActionWithConfig toGraphNode(SchemaNodeDef node) {
        return switch (WorkflowNodeType.require(node.getType())) {
            case LLM -> asAsyncNode(new MockLlmNodeAction(node, refResolver, mockLlmEngine));
            case LOOP -> asAsyncNode(buildLoopAction(node));
            // 人工确认节点自带 InterruptableAction（执行前中断、等待确认后恢复），不经 asAsyncNode 包装
            case HUMAN_CONFIRM -> new HumanConfirmNodeAction(node, refResolver, payloadParser);
            default -> throw exception(WORKFLOW_NODE_TYPE_UNSUPPORTED, node.getType());
        };
    }

    /** 循环节点及其内部子节点（支持嵌套循环） */
    private LoopNodeAction buildLoopAction(SchemaNodeDef node) {
        List<WorkflowNodeAction> innerActions = new ArrayList<>();
        if (node.getBlocks() != null) {
            for (SchemaNodeDef block : node.getBlocks()) {
                innerActions.add(buildInnerAction(block));
            }
        }
        if (innerActions.isEmpty()) {
            throw exception(WORKFLOW_GRAPH_BUILD_FAILED, "循环节点没有内部子节点：" + node.getId());
        }
        return new LoopNodeAction(node, innerActions, refResolver, properties, loopExecutor);
    }

    private WorkflowNodeAction buildInnerAction(SchemaNodeDef node) {
        return switch (WorkflowNodeType.require(node.getType())) {
            case LLM -> new MockLlmNodeAction(node, refResolver, mockLlmEngine);
            case LOOP -> buildLoopAction(node);
            // 循环内部的人工确认需要在循环体内暂停并恢复（容器内中断语义），本阶段不支持
            case HUMAN_CONFIRM -> throw exception(WORKFLOW_NODE_TYPE_UNSUPPORTED,
                    "循环内部暂不支持人工确认节点：" + node.getId());
            default -> throw exception(WORKFLOW_NODE_TYPE_UNSUPPORTED, node.getType());
        };
    }

    private AsyncNodeActionWithConfig asAsyncNode(WorkflowNodeAction action) {
        return (OverAllState state, RunnableConfig config) ->
                CompletableFuture.completedFuture(action.execute(
                        WorkflowStateView.of(state.data()).withListener(resolveProgressListener(config))));
    }

    /**
     * 从运行配置的元数据里取进度监听器。
     *
     * <p>监听器属于「一次执行」的上下文，不能挂在节点实例上（图按 schema 指纹缓存、会被多次复用），
     * 因此由提交接口在构建 {@link RunnableConfig} 时注入；未注入时退回空实现。
     */
    private WorkflowProgressListener resolveProgressListener(RunnableConfig config) {
        if (config == null) {
            return WorkflowProgressListener.NO_OP;
        }
        Object listener = config.metadata(WorkflowProgressListener.METADATA_KEY).orElse(null);
        return listener instanceof WorkflowProgressListener progress ? progress : WorkflowProgressListener.NO_OP;
    }

    /** schema 指纹：用于已编译图缓存 */
    public static String fingerprint(String schemaJson) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((schemaJson == null ? "" : schemaJson).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return builder.toString();
        } catch (Exception e) {
            return String.valueOf(schemaJson == null ? "null" : schemaJson.hashCode());
        }
    }

    private ThreadFactory newLoopThreadFactory() {
        AtomicInteger counter = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, "tb-workflow-loop-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }

    @PreDestroy
    public void shutdown() {
        if (loopExecutor != null) {
            loopExecutor.shutdownNow();
        }
    }

}
