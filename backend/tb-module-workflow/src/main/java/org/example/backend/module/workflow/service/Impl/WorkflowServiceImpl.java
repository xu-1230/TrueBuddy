package org.example.backend.module.workflow.service.Impl;

import cn.hutool.core.util.IdUtil;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.framework.common.exception.ServiceException;
import org.example.backend.module.workflow.mapper.WorkflowMapper;
import org.example.backend.module.workflow.mock.MockDataFactory;
import org.example.backend.module.workflow.model.WorkflowDO;
import org.example.backend.module.workflow.model.schema.NodeInputsDef;
import org.example.backend.module.workflow.model.schema.OutputParamDef;
import org.example.backend.module.workflow.model.schema.SchemaNodeDef;
import org.example.backend.module.workflow.model.schema.WorkflowDetailSchema;
import org.example.backend.module.workflow.model.schema.WorkflowGraphModel;
import org.example.backend.module.workflow.model.schema.WorkflowMetaSchema;
import org.example.backend.module.workflow.model.schema.WorkflowSchema;
import org.example.backend.module.workflow.model.vo.GradingRecordVO;
import org.example.backend.module.workflow.model.vo.GradingResultVO;
import org.example.backend.module.workflow.model.vo.NodeResultVO;
import org.example.backend.module.workflow.model.vo.PlanItemVO;
import org.example.backend.module.workflow.model.vo.TokenAndCostVO;
import org.example.backend.module.workflow.model.vo.WorkflowDetailRespVO;
import org.example.backend.module.workflow.model.vo.WorkflowPendingConfirmVO;
import org.example.backend.module.workflow.model.vo.WorkflowProcessRespVO;
import org.example.backend.module.workflow.model.vo.WorkflowRunReqVO;
import org.example.backend.module.workflow.model.vo.WorkflowRunRespVO;
import org.example.backend.module.workflow.model.vo.WorkflowSaveReqVO;
import org.example.backend.module.workflow.model.vo.WorkflowVO;
import org.example.backend.module.workflow.service.WorkflowService;
import org.example.backend.module.workflow.service.graph.GraphSessionStore;
import org.example.backend.module.workflow.service.graph.LlmPayloadParser;
import org.example.backend.module.workflow.service.graph.RefResolver;
import org.example.backend.module.workflow.service.graph.SchemaGraphBuilder;
import org.example.backend.module.workflow.service.graph.SchemaNormalizer;
import org.example.backend.module.workflow.service.graph.WorkflowNodeType;
import org.example.backend.module.workflow.service.graph.WorkflowProgressListener;
import org.example.backend.module.workflow.service.graph.WorkflowRunStatus;
import org.example.backend.module.workflow.service.graph.WorkflowStateView;
import org.example.backend.module.workflow.service.graph.WorkflowStreamRegistry;
import org.example.backend.module.workflow.service.node.HumanConfirmNodeAction;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.example.backend.framework.common.exception.util.ServiceExceptionUtil.exception;
import static org.example.backend.module.workflow.enums.ErrorCodeConstants.WORKFLOW_CONFIRM_PLAN_EMPTY;
import static org.example.backend.module.workflow.enums.ErrorCodeConstants.WORKFLOW_INPUT_REQUIRED;
import static org.example.backend.module.workflow.enums.ErrorCodeConstants.WORKFLOW_NOT_FOUND;
import static org.example.backend.module.workflow.enums.ErrorCodeConstants.WORKFLOW_NOT_WAITING_CONFIRM;
import static org.example.backend.module.workflow.enums.ErrorCodeConstants.WORKFLOW_RUN_FAILED;
import static org.example.backend.module.workflow.enums.ErrorCodeConstants.WORKFLOW_SCHEMA_REQUIRED;

/**
 * 工作流服务实现：SSE 实时推送 + 轮询兜底 + 人工确认中断。
 *
 * <p>编排要点：
 * <ol>
 *   <li>{@link #runStream} 同步校验画布（校验失败也以 {@code error} 事件回传，调用方只需处理一种协议），
 *       落执行会话（状态=执行中）、注册 SSE 通道并推 {@code created}，随后交由后台线程池执行整个图；</li>
 *   <li>执行线程用 {@code CompiledGraph.stream(...)} 逐步收集节点输出，每步推一条 {@code message}
 *       事件（节点完成）；循环节点每完成一条推一条 {@code message}(record)，实现「批改一份回传一份」；</li>
 *   <li>执行到人工确认节点时图中断：会话置「等待用户输入」并记录待确认清单，推
 *       {@code message}(requires_action)，**通道保持打开**；调用方经 {@link #resume} 提交清单后
 *       从断点续跑，事件继续写回原通道，最终以 {@code completed} + {@code done} 收尾；</li>
 *   <li>{@link #getProcess} 作轮询兜底：等待态返回待确认清单，终态返回完整业务结果，
 *       支撑调用方断线重连 / 刷新页面后取回进度与终态；</li>
 *   <li>执行会话在内存中按 executeId 维护（见 {@link GraphSessionStore}），进程重启即失效。</li>
 * </ol>
 */
@Slf4j
@Service
public class WorkflowServiceImpl implements WorkflowService {

    private static final String MESSAGE_FINISHED = "工作流执行完成";

    /** 等待用户输入时的会话提示信息 */
    private static final String MESSAGE_WAITING_CONFIRM = "等待确认批改清单";

    /** {@code message} 事件子类型：节点开始执行 */
    private static final String MESSAGE_TYPE_NODE_STARTED = "node_started";

    /** {@code message} 事件子类型：节点执行完成 */
    private static final String MESSAGE_TYPE_NODE_FINISHED = "node_finished";

    /** {@code message} 事件子类型：单份批改完成（循环内逐条回传） */
    private static final String MESSAGE_TYPE_RECORD = "record";

    /** {@code message} 事件子类型：等待用户输入 */
    private static final String MESSAGE_TYPE_REQUIRES_ACTION = "requires_action";

    /** 人工确认节点的清单输入变量名（与 {@code HumanConfirmNodeAction} 的约定一致） */
    private static final String PLAN_INPUT_NAME = "plan";

    private static final String FALLBACK_CONFIRM_OUTPUT = "confirmedPlan";

    private static final String FALLBACK_LOOP_OUTPUT = "results";

    /** 保存时未填写名称的兜底值 */
    private static final String DEFAULT_WORKFLOW_NAME = "未命名工作流";

    /** 执行会话 id 前缀 */
    private static final String EXECUTE_ID_PREFIX = "wf-";

    /** SSE 通道超时（毫秒）：0 表示不限时（人工确认可能等待数分钟，不能按默认 30s 掐断） */
    private static final long SSE_TIMEOUT_MS = 0L;

    /** 执行线程序号（便于日志与排障） */
    private static final AtomicInteger RUN_THREAD_SEQ = new AtomicInteger(1);

    /** 工作流定义持久化（画布保存 / 详情 / 列表） */
    private final WorkflowMapper workflowMapper;

    private final SchemaNormalizer schemaNormalizer;

    private final SchemaGraphBuilder graphBuilder;

    private final GraphSessionStore sessionStore;

    private final RefResolver refResolver;

    private final MockDataFactory mockDataFactory;

    private final LlmPayloadParser payloadParser;

    /** SSE 推送通道注册表（Web 类型不下沉到会话层） */
    private final WorkflowStreamRegistry streamRegistry;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 执行线程池：提交接口必须立即返回，整个图的执行在后台线程完成。
     *
     * <p>并发度固定为 2（页面同时开两个会话足够），线程为守护线程，容器关闭时
     * {@link #shutdownRunExecutor()} 回收。
     */
    private final ExecutorService runExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "tb-workflow-run-" + RUN_THREAD_SEQ.getAndIncrement());
        thread.setDaemon(true);
        return thread;
    });

    public WorkflowServiceImpl(SchemaNormalizer schemaNormalizer, SchemaGraphBuilder graphBuilder,
                               GraphSessionStore sessionStore, RefResolver refResolver,
                               MockDataFactory mockDataFactory, LlmPayloadParser payloadParser,
                               WorkflowStreamRegistry streamRegistry, WorkflowMapper workflowMapper) {
        this.workflowMapper = workflowMapper;
        this.schemaNormalizer = schemaNormalizer;
        this.graphBuilder = graphBuilder;
        this.sessionStore = sessionStore;
        this.refResolver = refResolver;
        this.mockDataFactory = mockDataFactory;
        this.payloadParser = payloadParser;
        this.streamRegistry = streamRegistry;
    }

    // ========== 提交执行 / 恢复确认 / 查询状态 ==========

    @Override
    public SseEmitter runStream(WorkflowRunReqVO reqVO) {
        WorkflowRunReqVO request = reqVO == null ? new WorkflowRunReqVO() : reqVO;
        LoadedSchema loaded;
        WorkflowGraphModel model;
        try {
            // 同步校验画布：校验失败也走事件流（error + done），调用方只需处理一种协议
            loaded = loadSchema(request.getSchemaJson());
            model = schemaNormalizer.normalize(loaded.schema(), loaded.name());
        } catch (Exception e) {
            String error = rootMessage(e);
            log.warn("[runStream][提交校验失败] error={}", error);
            return streamRegistry.failedStream(errorPayload(e, error));
        }

        long startedAt = System.currentTimeMillis();
        // 执行会话 id 一律由服务端生成：入参的 executeId 仅用于「恢复等待态会话」，不用于指定新会话
        String executeId = EXECUTE_ID_PREFIX + UUID.randomUUID();
        Map<String, Object> seed = seedStartInputs(model, request);

        GraphSessionStore.GraphSession session = new GraphSessionStore.GraphSession()
                .setExecuteId(executeId)
                .setGraph(graphBuilder.getOrBuild(SchemaGraphBuilder.fingerprint(loaded.json()), model))
                .setStatus(WorkflowRunStatus.RUNNING)
                .setModel(model)
                .setWorkflowId(request.getWorkflowId())
                .setSeedInputs(seed)
                .setNodeResults(initNodeResults(model))
                .setStartedAt(startedAt)
                .setNodeCursor(startedAt)
                .setCreatedAt(startedAt)
                .setMessage("");
        session.setConfig(RunnableConfig.builder()
                .threadId(executeId)
                // 进度监听器属于「本次执行」的上下文，不能挂在节点实例上（图按指纹缓存、会被复用）
                .addMetadata(WorkflowProgressListener.METADATA_KEY, new StreamProgressListener(session))
                .build());
        sessionStore.save(session);

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        streamRegistry.register(executeId, emitter);
        // created 在提交线程内先推：保证调用方收到的第一条事件一定是会话信息
        streamRegistry.send(executeId, WorkflowStreamRegistry.EVENT_CREATED, createdEvent(session));

        log.info("[runStream][已提交执行任务] executeId={}, workflow={}, startInputKeys={}",
                executeId, loaded.name(), seed.keySet());
        runExecutor.execute(() -> drive(session, session.getSeedInputs()));
        return emitter;
    }

    @Override
    public SseEmitter resume(WorkflowRunReqVO reqVO) {
        WorkflowRunReqVO request = reqVO == null ? new WorkflowRunReqVO() : reqVO;
        String executeId = request.getExecuteId();
        List<String> confirmedPlan = request.getConfirmedPlan();
        if (confirmedPlan == null || confirmedPlan.isEmpty()) {
            throw exception(WORKFLOW_CONFIRM_PLAN_EMPTY);
        }
        GraphSessionStore.GraphSession session = sessionStore.get(executeId);
        String interruptNodeId;
        synchronized (session) {
            if (session.getStatus() != WorkflowRunStatus.WAITING_CONFIRM
                    || !hasText(session.getInterruptNodeId())) {
                throw exception(WORKFLOW_NOT_WAITING_CONFIRM, String.valueOf(executeId));
            }
            interruptNodeId = session.getInterruptNodeId();
            session.setStatus(WorkflowRunStatus.RUNNING).setMessage("");
            // 等待确认的时间不计入任何节点耗时：从提交确认这一刻重新起算节点耗时游标
            session.setNodeCursor(System.currentTimeMillis());
        }
        sessionStore.save(session);
        log.info("[resume][已受理确认清单，开始恢复执行] executeId={}, nodeId={}, confirmedCount={}",
                executeId, interruptNodeId, confirmedPlan.size());
        runExecutor.execute(() -> resumeSession(session, interruptNodeId, confirmedPlan));
        // 恢复后的事件继续推回**原通道**，本响应只作「已受理」应答
        return streamRegistry.ackStream();
    }

    @Override
    public WorkflowProcessRespVO getProcess(String executeId) {
        GraphSessionStore.GraphSession session = sessionStore.get(executeId);
        synchronized (session) {
            return toProcessVO(session);
        }
    }

    /**
     * 驱动一次图执行：逐步收集节点输出、实时推事件；若停在人工确认节点则置等待态并**保持通道打开**。
     *
     * @param inputs 本次 stream 的入口输入；恢复执行时传 {@code null}，从检查点续跑到断点节点
     */
    private void drive(GraphSessionStore.GraphSession session, Map<String, Object> inputs) {
        try {
            RunOutcome outcome = execute(session, inputs);
            if (outcome.interruption() != null) {
                String nodeId = markWaitingConfirm(session, outcome.interruption());
                streamRegistry.send(session.getExecuteId(), WorkflowStreamRegistry.EVENT_MESSAGE,
                        requiresActionEvent(session));
                log.info("[drive][已暂停，等待人工确认] executeId={}, nodeId={}",
                        session.getExecuteId(), nodeId);
                // 刻意不结束流：通道保持打开，待调用方提交确认清单后由 resumeSession 续推
                return;
            }
            finishSession(session, outcome);
        } catch (Exception e) {
            failSession(session, e);
        }
    }

    /** 从人工确认断点恢复执行：注入确认清单后续跑，事件继续推回原通道 */
    private void resumeSession(GraphSessionStore.GraphSession session, String interruptNodeId,
                               List<String> confirmedPlan) {
        try {
            // 注入 {nodeId}.{outputName} → confirmedPlan：与节点写状态的口径一致，下游节点无需改动
            String planKey = RefResolver.stateKey(interruptNodeId,
                    firstOutputName(session.getModel().node(interruptNodeId), FALLBACK_CONFIRM_OUTPUT));
            RunnableConfig resumeConfig = session.getGraph().updateState(
                    session.getConfig(), Map.of(planKey, confirmedPlan), interruptNodeId);
            if (session.getInterruption() != null) {
                // 携带人工反馈：框架据此确认「本次中断已被处理」，避免同一节点反复中断
                resumeConfig = RunnableConfig.builder(resumeConfig)
                        .addHumanFeedback(session.getInterruption())
                        .build();
            }
            synchronized (session) {
                session.setConfig(resumeConfig)
                        .setInterruptNodeId(null)
                        .setPendingPlan(null)
                        .setPendingDefaultPlan(null)
                        .setInterruption(null);
            }
            log.info("[resumeSession][已注入确认清单，继续执行] executeId={}, nodeId={}, planKey={}",
                    session.getExecuteId(), interruptNodeId, planKey);
        } catch (Exception e) {
            failSession(session, e);
            return;
        }
        // 传 null：不再注入入口输入，从检查点续跑到断点节点（见 drive 的 inputs 说明）
        drive(session, null);
    }

    /** 执行整个图：逐步收集节点输出、实时推送事件，并识别人工确认中断 */
    private RunOutcome execute(GraphSessionStore.GraphSession session, Map<String, Object> inputs) {
        List<String> trace = new ArrayList<>();
        AtomicReference<Map<String, Object>> stateRef = new AtomicReference<>(new LinkedHashMap<>());
        AtomicReference<InterruptionMetadata> interruptionRef = new AtomicReference<>();
        try {
            session.getGraph().stream(inputs, session.getConfig())
                    .doOnNext(output -> {
                        if (output instanceof InterruptionMetadata interruption) {
                            // 图中断：本次不产出节点输出，由上层置「等待用户输入」并等待确认
                            interruptionRef.set(interruption);
                            return;
                        }
                        trace.add(output.node());
                        Map<String, Object> snapshot = output.state() == null
                                ? Map.of() : output.state().data();
                        if (output.state() != null) {
                            // 状态可能被后续节点继续改写，这里拷贝快照
                            stateRef.set(new LinkedHashMap<>(snapshot));
                        }
                        recordNodeFinish(session, output.node(), snapshot);
                        pushNodeResult(session, output.node(), MESSAGE_TYPE_NODE_FINISHED);
                    })
                    .blockLast();
        } catch (Exception e) {
            log.error("[execute][工作流执行失败] executeId={}, trace={}, error={}",
                    session.getExecuteId(), trace, rootMessage(e));
            throw exception(WORKFLOW_RUN_FAILED, rootMessage(e));
        }
        log.info("[execute][本次图执行结束] executeId={}, nodes={}, interrupted={}",
                session.getExecuteId(), trace.size(), interruptionRef.get() != null);
        return new RunOutcome(trace, stateRef.get(), interruptionRef.get());
    }

    /** 终态：写回会话 → 推 {@code completed} → 关闭流 */
    private void finishSession(GraphSessionStore.GraphSession session, RunOutcome outcome) {
        WorkflowProcessRespVO process;
        synchronized (session) {
            session.setStatus(WorkflowRunStatus.FINISHED)
                    .setNodeTrace(outcome.trace())
                    .setLastState(outcome.state())
                    .setMessage(MESSAGE_FINISHED)
                    .setResult(buildResult(session, outcome.state(), outcome.trace()))
                    .setFinishedAt(System.currentTimeMillis())
                    .setExeCostMs(session.getFinishedAt() - session.getStartedAt());
            process = toProcessVO(session);
        }
        // 刷新 updatedAt：TTL 从结束时刻重新起算，保证断线后仍能用轮询取到终态
        sessionStore.save(session);
        streamRegistry.send(session.getExecuteId(), WorkflowStreamRegistry.EVENT_COMPLETED, process);
        streamRegistry.complete(session.getExecuteId());
        log.info("[finishSession][执行完成] executeId={}, nodes={}",
                session.getExecuteId(), outcome.trace().size());
    }

    /** 异常终态：写回会话 → 推 {@code error} + {@code done} */
    private void failSession(GraphSessionStore.GraphSession session, Exception e) {
        String error = rootMessage(e);
        synchronized (session) {
            session.setStatus(WorkflowRunStatus.FAILED).setMessage(error);
            markFailure(session, error);
            session.setFinishedAt(System.currentTimeMillis());
            session.setExeCostMs(session.getFinishedAt() - session.getStartedAt());
        }
        sessionStore.save(session);
        streamRegistry.send(session.getExecuteId(), WorkflowStreamRegistry.EVENT_ERROR, errorPayload(e, error));
        streamRegistry.complete(session.getExecuteId());
        log.error("[failSession][执行失败] executeId={}, error={}", session.getExecuteId(), error);
    }

    /** 置「等待用户输入」：记录中断节点与待确认清单（同时刷新 TTL） */
    private String markWaitingConfirm(GraphSessionStore.GraphSession session, InterruptionMetadata interruption) {
        String nodeId = interruption.node();
        Object rawPlan = interruption.metadata(HumanConfirmNodeAction.METADATA_PLAN).orElse(null);
        Object rawDefaultPlan = interruption.metadata(HumanConfirmNodeAction.METADATA_DEFAULT_PLAN).orElse(null);
        List<PlanItemVO> plan = rawPlan instanceof List<?> list
                ? list.stream().filter(PlanItemVO.class::isInstance).map(PlanItemVO.class::cast).toList()
                : List.of();
        List<String> defaultPlan = rawDefaultPlan instanceof List<?> list
                ? list.stream().map(String::valueOf).toList() : List.of();
        synchronized (session) {
            session.setStatus(WorkflowRunStatus.WAITING_CONFIRM)
                    .setMessage(MESSAGE_WAITING_CONFIRM)
                    .setInterruptNodeId(nodeId)
                    .setPendingPlan(new ArrayList<>(plan))
                    .setPendingDefaultPlan(new ArrayList<>(defaultPlan))
                    .setInterruption(interruption);
            // 人工确认节点已进入等待：标为「执行中」，让调用方在画布上看到它停在哪
            SchemaNodeDef node = resolveNode(session.getModel(), nodeId);
            NodeResultVO result = node == null ? null : session.getNodeResults().get(node.getId());
            if (result != null) {
                result.setNodeStatus(NodeResultVO.STATUS_RUNNING);
            }
        }
        sessionStore.save(session);
        return nodeId;
    }

    // ========== SSE 事件负载 ==========

    /** {@code created}：会话已建立（执行会话 id + 初始节点清单，节点均为「未执行」） */
    private Map<String, Object> createdEvent(GraphSessionStore.GraphSession session) {
        Map<String, Object> data = new LinkedHashMap<>();
        synchronized (session) {
            data.put("executeId", session.getExecuteId());
            data.put("workflowId", session.getWorkflowId());
            data.put("nodeResults", copyNodeResults(session));
        }
        return data;
    }

    /** {@code message}(requires_action)：等待用户输入，负载携带待确认清单 */
    private Map<String, Object> requiresActionEvent(GraphSessionStore.GraphSession session) {
        WorkflowPendingConfirmVO pending = buildPendingConfirm(session);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", MESSAGE_TYPE_REQUIRES_ACTION);
        data.put("nodeId", pending.getNodeId());
        data.put("nodeName", pending.getNodeName());
        data.put("plan", pending.getPlan());
        data.put("defaultPlan", pending.getDefaultPlan());
        return data;
    }

    /** 推一条节点结果事件（{@code node_started} / {@code node_finished}），负载为该节点最新状态 */
    private void pushNodeResult(GraphSessionStore.GraphSession session, String nodeId, String type) {
        NodeResultVO node = nodeResultOf(session, nodeId);
        if (node == null) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", type);
        data.put("nodeId", node.getNodeId());
        data.put("nodeResult", node);
        streamRegistry.send(session.getExecuteId(), WorkflowStreamRegistry.EVENT_MESSAGE, data);
    }

    /** {@code message}(record)：循环内每完成一份批改即回传该份结果，实现「批改一份回传一份」 */
    private void pushRecord(GraphSessionStore.GraphSession session, String loopNodeId, int index,
                            int total, Object item, Object result) {
        // 循环体迭代项就是作业文件路径，与 confirmedPlan 逐项对应，故无需再取状态
        int fullScore = resolveFullScore(session.getModel(), session.getSeedInputs(), 100);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", MESSAGE_TYPE_RECORD);
        data.put("nodeId", loopNodeId);
        data.put("index", index);
        data.put("total", total);
        data.put("record", toGradingRecord(stringify(item), result, fullScore));
        streamRegistry.send(session.getExecuteId(), WorkflowStreamRegistry.EVENT_MESSAGE, data);
    }

    /** 取节点结果的线程安全副本；节点不在顶层（如循环内部子节点）时返回 null */
    private NodeResultVO nodeResultOf(GraphSessionStore.GraphSession session, String nodeId) {
        synchronized (session) {
            SchemaNodeDef node = resolveNode(session.getModel(), nodeId);
            NodeResultVO result = node == null ? null : session.getNodeResults().get(node.getId());
            return result == null ? null : copyOf(result);
        }
    }

    /** 待确认清单：仅在等待态有值（中断节点 + 全部条目 + 默认勾选） */
    private WorkflowPendingConfirmVO buildPendingConfirm(GraphSessionStore.GraphSession session) {
        synchronized (session) {
            WorkflowPendingConfirmVO pending = new WorkflowPendingConfirmVO();
            pending.setNodeId(session.getInterruptNodeId());
            pending.setPlan(session.getPendingPlan() == null
                    ? new ArrayList<>() : new ArrayList<>(session.getPendingPlan()));
            pending.setDefaultPlan(session.getPendingDefaultPlan() == null
                    ? new ArrayList<>() : new ArrayList<>(session.getPendingDefaultPlan()));
            SchemaNodeDef node = resolveNode(session.getModel(), session.getInterruptNodeId());
            pending.setNodeName(node == null ? session.getInterruptNodeId() : node.title());
            return pending;
        }
    }

    /** {@code error} 事件负载：业务异常用自身错误码，其余归为「工作流执行失败」 */
    private Map<String, Object> errorPayload(Throwable cause, String message) {
        Integer code = cause instanceof ServiceException serviceException
                ? serviceException.getCode() : WORKFLOW_RUN_FAILED.getCode();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", code);
        payload.put("message", message);
        return payload;
    }

    // ========== 节点级执行结果 ==========

    /** 初始化节点级结果：按画布顺序为每个顶层节点建一条（循环内部子节点不单独出条目） */
    private Map<String, NodeResultVO> initNodeResults(WorkflowGraphModel model) {
        Set<String> innerNodeIds = new HashSet<>();
        for (SchemaNodeDef node : model.getNodeIndex().values()) {
            if (node.getBlocks() == null) {
                continue;
            }
            for (SchemaNodeDef block : node.getBlocks()) {
                if (block != null && hasText(block.getId())) {
                    innerNodeIds.add(block.getId());
                }
            }
        }
        Map<String, NodeResultVO> results = new LinkedHashMap<>();
        for (SchemaNodeDef node : model.getNodeIndex().values()) {
            if (innerNodeIds.contains(node.getId())) {
                continue;
            }
            NodeResultVO result = new NodeResultVO();
            result.setNodeId(node.getId());
            result.setNodeName(node.title());
            result.setNodeType(nodeTypeName(node));
            results.put(node.getId(), result);
        }
        return results;
    }

    /** 记录某个节点执行完成：耗时按「与上一个节点完成时刻的差值」计算 */
    private void recordNodeFinish(GraphSessionStore.GraphSession session, String nodeId,
                                  Map<String, Object> state) {
        synchronized (session) {
            SchemaNodeDef node = resolveNode(session.getModel(), nodeId);
            NodeResultVO result = node == null ? null : session.getNodeResults().get(node.getId());
            if (result == null) {
                return;
            }
            long now = System.currentTimeMillis();
            long cost = Math.max(0L, now - session.getNodeCursor());
            session.setNodeCursor(now);
            result.setNodeStatus(NodeResultVO.STATUS_SUCCESS);
            result.setNodeExeCostMs(cost);
            result.setNodeExeCost(formatDuration(cost));
            result.setInput(nodeInputJson(node, state));
            result.setOutput(nodeOutputJson(session, node, state));
        }
    }

    /**
     * 标记失败节点。
     *
     * <p>图执行异常时无法直接得知失败节点，取启发式：优先「执行中」的节点，
     * 否则取最后一个成功节点之后的首个「未执行」节点。
     */
    private void markFailure(GraphSessionStore.GraphSession session, String error) {
        NodeResultVO target = null;
        for (NodeResultVO node : session.getNodeResults().values()) {
            if (node.getNodeStatus() == NodeResultVO.STATUS_RUNNING) {
                target = node;
                break;
            }
        }
        if (target == null) {
            boolean afterSuccess = false;
            for (NodeResultVO node : session.getNodeResults().values()) {
                if (node.getNodeStatus() == NodeResultVO.STATUS_SUCCESS) {
                    afterSuccess = true;
                    continue;
                }
                if (afterSuccess && node.getNodeStatus() == NodeResultVO.STATUS_PENDING) {
                    target = node;
                    break;
                }
            }
        }
        if (target != null) {
            target.setNodeStatus(NodeResultVO.STATUS_FAILED);
            target.setErrorInfo(error);
        }
    }

    /** 节点 id → 画布节点；开始 / 结束节点不注册为图节点，stream 回报的是 StateGraph 的内部标记 */
    private SchemaNodeDef resolveNode(WorkflowGraphModel model, String nodeId) {
        if (!hasText(nodeId)) {
            return null;
        }
        SchemaNodeDef node = model.node(nodeId);
        if (node != null) {
            return node;
        }
        if (StateGraph.START.equals(nodeId)) {
            return model.getStartNode();
        }
        return StateGraph.END.equals(nodeId) ? model.getEndNode() : null;
    }

    /** 节点输出：该节点写入状态的 {@code {nodeId}.*} 键值；开始节点用播种输入兜底 */
    private String nodeOutputJson(GraphSessionStore.GraphSession session, SchemaNodeDef node,
                                  Map<String, Object> state) {
        String prefix = node.getId() + ".";
        Map<String, Object> outputs = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : state.entrySet()) {
            String key = entry.getKey();
            if (key != null && key.startsWith(prefix)) {
                outputs.put(key.substring(prefix.length()), entry.getValue());
            }
        }
        if (outputs.isEmpty() && WorkflowNodeType.of(node.getType()) == WorkflowNodeType.START) {
            for (Map.Entry<String, Object> entry : session.getSeedInputs().entrySet()) {
                String key = entry.getKey();
                int dot = key == null ? -1 : key.indexOf('.');
                if (dot >= 0) {
                    outputs.put(key.substring(dot + 1), entry.getValue());
                }
            }
        }
        return outputs.isEmpty() ? "" : toJson(outputs);
    }

    /** 节点输入：按节点声明的输入参数解析当前状态得到（无输入声明时为空串） */
    private String nodeInputJson(SchemaNodeDef node, Map<String, Object> state) {
        if (node.getData() == null || node.getData().getInputs() == null
                || node.getData().getInputs().getInputParameters() == null) {
            return "";
        }
        try {
            Map<String, Object> params = refResolver.resolveParams(
                    node.getData().getInputs().getInputParameters(), WorkflowStateView.of(state));
            return params.isEmpty() ? "" : toJson(params);
        } catch (Exception e) {
            log.debug("[nodeInputJson][节点输入解析失败] nodeId={}, error={}", node.getId(), e.getMessage());
            return "";
        }
    }

    /** 节点类型展示名（对齐 Coze 形态：Start / LLM / Loop / HumanConfirm / End） */
    private String nodeTypeName(SchemaNodeDef node) {
        WorkflowNodeType type = WorkflowNodeType.of(node.getType());
        if (type == null) {
            return node.getType();
        }
        return switch (type) {
            case START -> "Start";
            case END -> "End";
            case LLM -> "LLM";
            case LOOP -> "Loop";
            case HUMAN_CONFIRM -> "HumanConfirm";
        };
    }

    /**
     * 进度监听器：把循环节点的执行进度**实时推给调用方**。
     *
     * <p>{@code onLoopStart} 把循环节点从「未执行」置为「执行中」并推一条 {@code node_started}；
     * {@code onLoopItem} 每完成一份批改即推一条 {@code message}(record)，实现「批改一份回传一份」，
     * 无需等整个循环结束。调用方断开时推送静默失败，不影响循环执行本身。
     */
    private final class StreamProgressListener implements WorkflowProgressListener {

        private final GraphSessionStore.GraphSession session;

        private StreamProgressListener(GraphSessionStore.GraphSession session) {
            this.session = session;
        }

        @Override
        public void onLoopStart(String loopNodeId, int total) {
            synchronized (session) {
                NodeResultVO node = session.getNodeResults().get(loopNodeId);
                if (node != null && node.getNodeStatus() == NodeResultVO.STATUS_PENDING) {
                    node.setNodeStatus(NodeResultVO.STATUS_RUNNING);
                }
            }
            log.info("[onLoopStart][循环节点开始执行] executeId={}, loopNodeId={}, total={}",
                    session.getExecuteId(), loopNodeId, total);
            pushNodeResult(session, loopNodeId, MESSAGE_TYPE_NODE_STARTED);
        }

        @Override
        public void onLoopItem(String loopNodeId, int index, int total, Object item, Object result) {
            // 回调来自循环执行线程（并发迭代时可能多线程到达），推送内部自行加锁
            pushRecord(session, loopNodeId, index, total, item, result);
        }
    }

    // ========== 响应组装 ==========

    /** 组装执行状态响应（调用方需已持有 session 锁） */
    private WorkflowProcessRespVO toProcessVO(GraphSessionStore.GraphSession session) {
        WorkflowProcessRespVO resp = new WorkflowProcessRespVO();
        resp.setExecuteId(session.getExecuteId());
        resp.setWorkFlowId(session.getWorkflowId());
        resp.setExeHistoryStatus(toExeHistoryStatus(session.getStatus()));
        resp.setExecuteStatus(session.getStatus() == WorkflowRunStatus.FAILED
                ? WorkflowProcessRespVO.EXECUTE_STATUS_ABNORMAL
                : WorkflowProcessRespVO.EXECUTE_STATUS_NORMAL);
        long cost = session.getExeCostMs() != null
                ? session.getExeCostMs() : Math.max(0L, System.currentTimeMillis() - session.getStartedAt());
        resp.setExeCostMs(cost);
        resp.setWorkflowExeCost(formatDuration(cost));
        resp.setNodeEvents(new ArrayList<>());
        resp.setNodeResults(copyNodeResults(session));
        resp.setTokenAndCost(new TokenAndCostVO());
        resp.setProjectId(WorkflowProcessRespVO.DEFAULT_PROJECT_ID);
        resp.setMessage(session.getMessage() == null ? "" : session.getMessage());
        resp.setResult(session.getResult());
        // 等待用户输入：附带待确认清单，调用方据此渲染人工确认卡片（SSE 断开后的兜底路径）
        if (session.getStatus() == WorkflowRunStatus.WAITING_CONFIRM) {
            resp.setPendingConfirm(buildPendingConfirm(session));
        }
        return resp;
    }

    private int toExeHistoryStatus(WorkflowRunStatus status) {
        if (status == null) {
            return WorkflowProcessRespVO.EXE_STATUS_RUNNING;
        }
        return switch (status) {
            case RUNNING -> WorkflowProcessRespVO.EXE_STATUS_RUNNING;
            case WAITING_CONFIRM -> WorkflowProcessRespVO.EXE_STATUS_WAITING;
            case FINISHED -> WorkflowProcessRespVO.EXE_STATUS_FINISHED;
            case FAILED -> WorkflowProcessRespVO.EXE_STATUS_FAILED;
        };
    }

    /** 复制节点结果：避免响应对象与执行线程共享可变实例 */
    private NodeResultVO copyOf(NodeResultVO source) {
        NodeResultVO copy = new NodeResultVO();
        copy.setNodeId(source.getNodeId());
        copy.setNodeName(source.getNodeName());
        copy.setNodeType(source.getNodeType());
        copy.setNodeStatus(source.getNodeStatus());
        copy.setErrorInfo(source.getErrorInfo());
        copy.setErrorLevel(source.getErrorLevel());
        copy.setInput(source.getInput());
        copy.setOutput(source.getOutput());
        copy.setNodeExeCost(source.getNodeExeCost());
        copy.setNodeExeCostMs(source.getNodeExeCostMs());
        copy.setNeedAsync(source.getNeedAsync());
        copy.setLogVersion(source.getLogVersion());
        return copy;
    }

    /** 复制全部节点结果：按画布节点顺序（调用方需已持有 session 锁） */
    private List<NodeResultVO> copyNodeResults(GraphSessionStore.GraphSession session) {
        List<NodeResultVO> copies = new ArrayList<>(session.getNodeResults().size());
        for (NodeResultVO node : session.getNodeResults().values()) {
            copies.add(copyOf(node));
        }
        return copies;
    }

    /** 组装终态业务结果 */
    private WorkflowRunRespVO buildResult(GraphSessionStore.GraphSession session,
                                          Map<String, Object> state, List<String> trace) {
        WorkflowGraphModel model = session.getModel();
        WorkflowRunRespVO result = new WorkflowRunRespVO();
        result.setNodeTrace(trace);
        result.setState(state);
        result.setFullScore(resolveFullScore(model, state, 100));

        List<PlanItemVO> plan = buildPlan(model, state);
        result.setPlan(plan);
        result.setDefaultPlan(plan.stream()
                .filter(item -> Boolean.TRUE.equals(item.getNeedGrading()))
                .map(PlanItemVO::getFilePath)
                .toList());
        result.setConfirmedPlan(confirmedPlan(model, state));
        result.setRecords(buildRecords(model, state));
        result.setContent(renderEndContent(model, state));
        return result;
    }

    /** 批改清单：由人工确认节点声明的 {@code plan} 输入解析（不再经由中断元数据） */
    private List<PlanItemVO> buildPlan(WorkflowGraphModel model, Map<String, Object> state) {
        SchemaNodeDef confirmNode = firstExecutableOfType(model, WorkflowNodeType.HUMAN_CONFIRM);
        if (confirmNode == null || confirmNode.getData() == null
                || confirmNode.getData().getInputs() == null) {
            return new ArrayList<>();
        }
        try {
            Map<String, Object> params = refResolver.resolveParams(
                    confirmNode.getData().getInputs().getInputParameters(), WorkflowStateView.of(state));
            Object raw = params.get(PLAN_INPUT_NAME);
            return payloadParser.parsePlan(raw == null ? null : String.valueOf(raw));
        } catch (Exception e) {
            log.warn("[buildPlan][批改清单解析失败] nodeId={}, error={}", confirmNode.getId(), e.getMessage());
            return new ArrayList<>();
        }
    }

    /** 组装逐份批改记录：循环结果按顺序对应已确认清单 */
    private List<GradingRecordVO> buildRecords(WorkflowGraphModel model, Map<String, Object> state) {
        List<GradingRecordVO> records = new ArrayList<>();
        SchemaNodeDef loopNode = firstExecutableOfType(model, WorkflowNodeType.LOOP);
        if (loopNode == null) {
            return records;
        }
        String resultsKey = RefResolver.stateKey(loopNode.getId(), firstOutputName(loopNode, FALLBACK_LOOP_OUTPUT));
        List<Object> results = toObjectList(state.get(resultsKey));
        List<String> confirmedPlan = confirmedPlan(model, state);
        int fullScore = resolveFullScore(model, state, 100);

        for (int index = 0; index < results.size(); index++) {
            String filePath = index < confirmedPlan.size() ? confirmedPlan.get(index) : null;
            records.add(toGradingRecord(filePath, results.get(index), fullScore));
        }
        log.info("[buildRecords][批改记录组装完成] loopNodeId={}, records={}", loopNode.getId(), records.size());
        return records;
    }

    /**
     * 把一份批改的原始输出转换为接口记录。
     *
     * @param filePath  该条对应的作业路径（取自已确认清单，可为 null）
     * @param rawResult 该条的原始结果（模拟 / 真实大模型输出的 JSON 文本）
     * @param fullScore 本次满分（用于换算等级）
     */
    private GradingRecordVO toGradingRecord(String filePath, Object rawResult, int fullScore) {
        GradingResultVO result = payloadParser.parseGrading(stringify(rawResult));
        GradingRecordVO record = new GradingRecordVO();
        record.setFilePath(filePath);
        record.setFileName(fileNameOf(filePath));
        record.setTotalScore(result.getTotalScore());
        record.setDimensions(result.getDimensions());
        record.setComment(result.getComment());
        record.setGrade(GradingRecordVO.resolveGrade(result.getTotalScore(), fullScore));
        return record;
    }

    /** 结束节点：按声明的输入模板渲染最终输出 */
    private String renderEndContent(WorkflowGraphModel model, Map<String, Object> state) {
        SchemaNodeDef endNode = model.getEndNode();
        if (endNode.getData() == null || endNode.getData().getInputs() == null) {
            return null;
        }
        WorkflowStateView view = WorkflowStateView.of(state);
        NodeInputsDef inputs = endNode.getData().getInputs();
        Map<String, Object> locals = refResolver.resolveParams(inputs.getInputParameters(), view);
        Object content = refResolver.resolveSlot(inputs.getContent(), view, "content");
        return refResolver.renderTemplate(content == null ? null : String.valueOf(content), locals, view);
    }

    // ========== 工作流定义（持久化） ==========

    @Override
    public Long saveWorkflow(WorkflowSaveReqVO reqVO) {
        if (reqVO == null || !hasText(reqVO.getSchemaJson())) {
            throw exception(WORKFLOW_SCHEMA_REQUIRED);
        }
        String name = hasText(reqVO.getName()) ? reqVO.getName().trim() : DEFAULT_WORKFLOW_NAME;
        Integer status = reqVO.getStatus() == null ? 0 : reqVO.getStatus();
        String description = hasText(reqVO.getDesc()) ? reqVO.getDesc().trim() : null;

        Long id = reqVO.getId();
        if (id == null) {
            WorkflowDO created = new WorkflowDO();
            created.setId(IdUtil.getSnowflakeNextId());
            created.setName(name);
            created.setDescription(description);
            created.setSchemaJson(reqVO.getSchemaJson());
            created.setStatus(status);
            workflowMapper.insert(created);
            log.info("[saveWorkflow][新建工作流] id={}, name={}", created.getId(), name);
            return created.getId();
        }

        // 更新前先确认存在：不存在时明确报「工作流不存在」，避免静默返回 0 行
        if (workflowMapper.selectById(id) == null) {
            throw exception(WORKFLOW_NOT_FOUND);
        }
        WorkflowDO update = new WorkflowDO();
        update.setId(id);
        update.setName(name);
        update.setDescription(description);
        update.setSchemaJson(reqVO.getSchemaJson());
        update.setStatus(status);
        workflowMapper.updateById(update);
        log.info("[saveWorkflow][更新工作流] id={}, name={}", id, name);
        return id;
    }

    @Override
    public WorkflowDetailRespVO getWorkflowDetail(Long id) {
        WorkflowDO workflow = id == null ? null : workflowMapper.selectById(id);
        if (workflow == null) {
            throw exception(WORKFLOW_NOT_FOUND);
        }
        WorkflowDetailRespVO resp = new WorkflowDetailRespVO();
        resp.setWorkflow(toWorkflowVO(workflow));
        return resp;
    }

    @Override
    public List<WorkflowVO> listWorkflows() {
        return workflowMapper.selectList();
    }

    /** DO → VO（对外口径：id 为字符串、时间为秒级时间戳） */
    private WorkflowVO toWorkflowVO(WorkflowDO workflow) {
        WorkflowVO vo = new WorkflowVO();
        vo.setWorkflowId(String.valueOf(workflow.getId()));
        vo.setName(workflow.getName());
        vo.setDesc(workflow.getDescription());
        vo.setSchemaJson(workflow.getSchemaJson());
        vo.setStatus(workflow.getStatus());
        vo.setCreateTime(toEpochSecond(workflow.getCreateTime()));
        vo.setUpdateTime(toEpochSecond(workflow.getUpdateTime()));
        return vo;
    }

    /** 时间与前端契约一致：秒级时间戳 */
    private Long toEpochSecond(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toEpochSecond();
    }

    // ========== 入参解析 ==========

    /** 把开始节点声明的输出播种为工作流输入 */
    private Map<String, Object> seedStartInputs(WorkflowGraphModel model, WorkflowRunReqVO request) {
        Map<String, Object> seed = new LinkedHashMap<>();
        SchemaNodeDef startNode = model.getStartNode();
        if (startNode.getData() == null || startNode.getData().getOutputs() == null) {
            return seed;
        }
        for (OutputParamDef output : startNode.getData().getOutputs()) {
            if (output == null || !hasText(output.getName())) {
                continue;
            }
            Object value = resolveStartInput(request, output);
            if (value == null) {
                continue;
            }
            seed.put(RefResolver.stateKey(startNode.getId(), output.getName()), value);
            seed.putIfAbsent(output.getName(), value);
        }
        return seed;
    }

    /** 入参 → 默认值 → 模拟数据：逐级回退解析开始节点输入 */
    private Object resolveStartInput(WorkflowRunReqVO request, OutputParamDef output) {
        String name = output.getName();
        Object value = request.getInputs() == null ? null : request.getInputs().get(name);
        if (value == null) {
            value = typedInput(request, name);
        }
        if (value == null && output.getDefaultValue() != null) {
            value = refResolver.coerce(output.getDefaultValue(), output.getType());
        }
        if (value == null) {
            value = mockDataFactory.fallbackValue(name);
        }
        if (value == null) {
            if (Boolean.TRUE.equals(output.getRequired())) {
                throw exception(WORKFLOW_INPUT_REQUIRED, name);
            }
            return null;
        }
        return refResolver.coerce(value, output.getType());
    }

    /** 常用入参的强类型取值（与开始节点声明的输出同名） */
    private Object typedInput(WorkflowRunReqVO request, String name) {
        return switch (name) {
            case "fileTree" -> request.getFileTree();
            case "knowledgeList" -> request.getKnowledgeList();
            case "aiDescription" -> request.getAiDescription();
            case "fullScore" -> request.getFullScore();
            default -> null;
        };
    }

    // ========== 工具方法 ==========

    /** 解析执行用的画布定义；{@code schemaJson} 为空时由 {@code readDetail} 抛 WORKFLOW_SCHEMA_EMPTY */
    private LoadedSchema loadSchema(String schemaJson) {
        WorkflowDetailSchema detail = schemaNormalizer.readDetail(schemaJson);
        WorkflowSchema schema = schemaNormalizer.extractSchema(detail);
        WorkflowMetaSchema meta = detail.getWorkflow();
        String name = meta == null || !hasText(meta.getName()) ? "workflow" : meta.getName();
        return new LoadedSchema(schemaJson, name, schema);
    }

    private SchemaNodeDef firstExecutableOfType(WorkflowGraphModel model, WorkflowNodeType type) {
        for (SchemaNodeDef node : model.getExecutableNodes()) {
            if (WorkflowNodeType.of(node.getType()) == type) {
                return node;
            }
        }
        return null;
    }

    private String firstOutputName(SchemaNodeDef node, String fallback) {
        if (node != null && node.getData() != null && node.getData().getOutputs() != null) {
            for (OutputParamDef output : node.getData().getOutputs()) {
                if (output != null && hasText(output.getName())) {
                    return output.getName();
                }
            }
        }
        return fallback;
    }

    /** 本次实际批改的清单：人工确认提交后（或清单为空时自动放行）写回的状态值 */
    private List<String> confirmedPlan(WorkflowGraphModel model, Map<String, Object> state) {
        SchemaNodeDef confirmNode = firstExecutableOfType(model, WorkflowNodeType.HUMAN_CONFIRM);
        if (confirmNode == null) {
            return List.of();
        }
        String planKey = RefResolver.stateKey(confirmNode.getId(), firstOutputName(confirmNode, FALLBACK_CONFIRM_OUTPUT));
        return toStringList(state.get(planKey));
    }

    private int resolveFullScore(WorkflowGraphModel model, Map<String, Object> state, int defaultValue) {
        Object value = state.get(RefResolver.stateKey(model.getStartNode().getId(), "fullScore"));
        if (value == null) {
            value = state.get("fullScore");
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {
                // 落到默认值
            }
        }
        Object fallback = mockDataFactory.fullScore();
        return fallback instanceof Number number ? number.intValue() : defaultValue;
    }

    /** 耗时格式化（对齐 Coze 形态：{@code 0.000s} / {@code 2s} / {@code 1m32s}） */
    private String formatDuration(long millis) {
        if (millis <= 0) {
            return "0.000s";
        }
        if (millis < 1000) {
            return String.format(Locale.ROOT, "%.3fs", millis / 1000.0);
        }
        long totalSeconds = millis / 1000;
        if (totalSeconds < 60) {
            return totalSeconds + "s";
        }
        return (totalSeconds / 60) + "m" + (totalSeconds % 60) + "s";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("[toJson][序列化失败] error={}", e.getMessage());
            return "";
        }
    }

    private List<Object> toObjectList(Object value) {
        List<Object> list = new ArrayList<>();
        if (value instanceof List<?> raw) {
            list.addAll(raw);
        } else if (value != null) {
            list.add(value);
        }
        return list;
    }

    private List<String> toStringList(Object value) {
        List<String> list = new ArrayList<>();
        if (value instanceof List<?> raw) {
            for (Object item : raw) {
                list.add(stringify(item));
            }
        } else if (value != null) {
            list.add(stringify(value));
        }
        return list;
    }

    private String stringify(Object value) {
        if (value == null) {
            return "";
        }
        return value instanceof String string ? string : String.valueOf(value);
    }

    private String fileNameOf(String path) {
        if (path == null) {
            return null;
        }
        String normalized = path.replace('\\', '/');
        int index = normalized.lastIndexOf('/');
        return index < 0 ? normalized : normalized.substring(index + 1);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }

    /** 容器关闭时回收执行线程池 */
    @PreDestroy
    public void shutdownRunExecutor() {
        runExecutor.shutdownNow();
    }

    /** 一次图执行的产出；{@code interruption} 非空表示本次停在人工确认节点 */
    private record RunOutcome(List<String> trace, Map<String, Object> state,
                              InterruptionMetadata interruption) {
    }

    /** 已加载的画布定义（保留原始 JSON 文本用于图缓存指纹） */
    private record LoadedSchema(String json, String name, WorkflowSchema schema) {
    }

}
