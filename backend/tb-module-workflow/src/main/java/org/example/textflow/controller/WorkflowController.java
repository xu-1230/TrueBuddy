package org.example.textflow.controller;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.KeyStrategyFactoryBuilder;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import jakarta.annotation.Resource;
import org.example.backend.framework.common.exception.ServiceException;
import org.example.backend.framework.common.exception.enums.GlobalErrorCodeConstants;
import org.example.textflow.entity.NodeData;
import org.example.textflow.entity.NodeField;
import org.example.textflow.entity.NodeFieldRef;
import org.example.textflow.entity.WorkflowEdge;
import org.example.textflow.entity.WorkflowNode;
import org.example.textflow.entity.WorkflowProcessLoader;
import org.example.textflow.entity.WorkflowProcessRespVO;
import org.example.textflow.node.NodeActionFactory;
import org.example.textflow.node.NodeEventListener;
import org.example.textflow.service.WorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/workflow")
public class WorkflowController {

    private static final Logger log = LoggerFactory.getLogger(WorkflowController.class);

    /** 变量类型为 list 时视为可累积的“记忆”变量 */
    private static final String LIST_TYPE = "list";

    /** 请求体未提交画布定义时回落的定义标识（classpath text.json） */
    private static final String DEFAULT_WORKFLOW_ID = "text";

    @Resource
    private WorkflowService workflowService;

    /** 大模型节点使用的对话模型客户端（bean 定义见 {@code WorkflowChatClientConfiguration}，按名注入避开其它模块的同类型 bean） */
    @Resource(name = "nodeChatClient")
    private ChatClient nodeChatClient;

    private volatile WorkflowProcessRespVO processCache;

    /** 中断点存储：runId -> 中断信息，resume 时取回；Resume 需要 checkpoint，故用内存 saver */
    private final MemorySaver checkpointSaver = new MemorySaver();

    private final Map<String, InterruptionMetadata> interruptions = new ConcurrentHashMap<>();

    /** 本次执行使用的画布定义：runId -> 定义；resume 时按 runId 取回同一份画布（进程重启即失效） */
    private final Map<String, WorkflowProcessRespVO> runDefinitions = new ConcurrentHashMap<>();



    /**
     * 执行工作流：画布定义通过请求体提交（不提交时回落 classpath text.json），节点执行进度通过 SSE 推送。
     *
     * @param definition 画布定义（{@code {nodes, edges}}），可省略
     */
    @PostMapping(value = "/run", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter run(@RequestBody(required = false) WorkflowProcessRespVO definition) throws GraphStateException {
        WorkflowProcessRespVO process = resolveDefinition(definition);
        String runId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(0L);
        // 登记本次执行的定义：人工确认后 resume 时复用同一份画布
        runDefinitions.put(runId, process);
        send(emitter, "run", Map.of("runId", runId));
        CompiledGraph compiledGraph = createGraph(process, nodeOutput -> push(emitter, runId, nodeOutput));
        compiledGraph.stream(initialState(), RunnableConfig.builder().threadId(runId).build())
                .subscribe(
                        nodeOutput -> push(emitter, runId, nodeOutput),
                        error -> completeWithError(emitter, runId, error),
                        () -> completeRun(emitter, runId));
        return emitter;
    }

    /**
     * 人工确认后继续执行，后续节点进度同样通过 SSE 推送。
     *
     * @param runId   执行标识，取自 /run 的 run 事件
     * @param content 人工确认内容，写入状态键 confirmedPlan
     */
    @PostMapping(value = "/resume", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter resume(@RequestParam("run_id") String runId,
                             @RequestParam("content") String content) throws GraphStateException {
        InterruptionMetadata interruption = interruptions.remove(runId);
        if (interruption == null) {
            throw new ServiceException(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), "没有待人工确认的流程: " + runId);
        }
        // 复用本次执行的画布定义；定义缺失（如进程重启）时回落 classpath text.json
        WorkflowProcessRespVO process = runDefinitions.getOrDefault(runId,
                workflowService.getProcess(DEFAULT_WORKFLOW_ID));
        SseEmitter emitter = new SseEmitter(0L);
        RunnableConfig config = RunnableConfig.builder()
                .threadId(runId)
                .addHumanFeedback(interruption)
                .addStateUpdate(Map.of("confirmedPlan", List.of(content)))
                .resume()
                .build();
        CompiledGraph compiledGraph = createGraph(process, nodeOutput -> push(emitter, runId, nodeOutput));
        compiledGraph.stream(Map.of(), config)
                .subscribe(
                        nodeOutput -> push(emitter, runId, nodeOutput),
                        error -> completeWithError(emitter, runId, error),
                        () -> completeRun(emitter, runId));
        return emitter;
    }


    /** 把 graph 的节点输出转成 SSE 事件 */
    private void push(SseEmitter emitter, String runId, NodeOutput nodeOutput) {
        if (nodeOutput instanceof InterruptionMetadata interruption) {
            // 人工确认中断：记下中断点，把待确认内容推给前端
            interruptions.put(runId, interruption);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("runId", runId);
            payload.putAll(interruption.metadata().orElse(Map.of()));
            send(emitter, "interrupt", payload);
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("node", nodeOutput.node());
        payload.put("state", nodeOutput.state() == null ? Map.of() : nodeOutput.state().data());
        send(emitter, "node", payload);
    }

    private void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    /** /run 的初始状态：沿用既有占位键，无业务含义 */
    private Map<String, Object> initialState() {
        Map<String, Object> objectMap = new HashMap<>();
        objectMap.put("__START__", "777");
        return objectMap;
    }

    /**
     * 解析本次执行的定义：优先使用请求体提交的画布定义，未提交时回落 classpath text.json。
     * <p>画布定义的校验（连线 id 必填且唯一）由 {@link WorkflowProcessLoader#validate} 统一负责，
     * 校验失败转成业务异常，使前端拿到可直接展示的原因。
     */
    private WorkflowProcessRespVO resolveDefinition(WorkflowProcessRespVO definition) {
        if (definition == null) {
            return workflowService.getProcess(DEFAULT_WORKFLOW_ID);
        }
        if (definition.getNodes() == null || definition.getNodes().isEmpty()) {
            throw new ServiceException(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), "画布定义为空：至少需要一个节点");
        }
        try {
            return WorkflowProcessLoader.validate(definition);
        } catch (IllegalArgumentException ex) {
            throw new ServiceException(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), ex.getMessage());
        }
    }

    /** 流正常结束：没有待人工确认时清理定义缓存，避免内存泄漏（有中断点时留给 resume 复用） */
    private void completeRun(SseEmitter emitter, String runId) {
        if (!interruptions.containsKey(runId)) {
            runDefinitions.remove(runId);
        }
        emitter.complete();
    }

    /** 流异常结束：清理定义缓存并结束流 */
    private void completeWithError(SseEmitter emitter, String runId, Throwable error) {
        runDefinitions.remove(runId);
        emitter.completeWithError(error);
    }

    @GetMapping("/text")
    public void text() throws GraphStateException {
        CompiledGraph compiledGraph = createGraph(workflowService.getProcess(DEFAULT_WORKFLOW_ID));
        compiledGraph.invoke(Map.of())
                // 只记状态键：终态里是各节点的产出（用户数据），不整体打进日志
                .ifPresent(state -> log.info("/workflow/text 执行结束，终态状态键: {}", state.data().keySet()));
    }

    /** 创建图：按 process 定义装配节点、边、入口出口并编译（不转发节点内部事件） */
    private CompiledGraph createGraph(WorkflowProcessRespVO process) throws GraphStateException {
        return createGraph(process, NodeEventListener.NO_OP);
    }

    /**
     * 创建图：按 process 定义装配节点、边、入口出口并编译。
     *
     * @param listener 节点内部事件回调，用于把迭代体子图里子块的 {@link NodeOutput} 一并推给前端
     */
    private CompiledGraph createGraph(WorkflowProcessRespVO process, NodeEventListener listener) throws GraphStateException {

        StateGraph stateGraph = new StateGraph("text-flow", createKeyStrategyFactory());

        for (WorkflowNode node : process.getNodes()) {
            stateGraph.addNode(node.getId(), NodeActionFactory.of(node, listener, nodeChatClient));
        }

        for (WorkflowEdge edge : process.getEdges()) {
            stateGraph.addEdge(edge.getSource(), edge.getTarget());
        }

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

        return stateGraph.compile(CompileConfig.builder().saverConfig(SaverConfig.builder().register(checkpointSaver).build()).releaseThread(false)
                .build());
    }

    public static KeyStrategyFactory createKeyStrategyFactory() {
        return () -> {
            Map<String, KeyStrategy> keyStrategyMap = new HashMap<>();
            keyStrategyMap.put("", new AppendStrategy());
            return keyStrategyMap;
        };
    }



}
