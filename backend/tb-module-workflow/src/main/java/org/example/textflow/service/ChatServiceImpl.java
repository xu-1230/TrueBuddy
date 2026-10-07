package org.example.textflow.service;

import cn.hutool.core.util.IdUtil;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import org.example.backend.framework.common.exception.ServiceException;
import org.example.backend.framework.common.exception.enums.GlobalErrorCodeConstants;
import org.example.backend.framework.security.LoginUser;
import org.example.backend.framework.security.util.SecurityFrameworkUtils;
import org.example.textflow.entity.WorkflowProcessRespVO;
import org.example.textflow.model.ChatProtocol;
import org.example.textflow.model.WorkflowConstants;
import org.example.textflow.model.WorkflowConversationDO;
import org.example.textflow.model.WorkflowDO;
import org.example.textflow.model.WorkflowRunDO;
import org.example.textflow.model.vo.ChatMessageVO;
import org.example.textflow.model.vo.ChatReqVO;
import org.example.textflow.model.vo.ConversationCreateReqVO;
import org.example.textflow.model.vo.ConversationVO;
import org.example.textflow.node.WorkflowGraphFactory;
import org.example.textflow.repository.WorkflowConversationRepository;
import org.example.textflow.repository.WorkflowDefinitionRepository;
import org.example.textflow.repository.WorkflowRunRepository;
import org.example.textflow.sse.ChatSsePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * {@code /chat} 的实现。
 *
 * <p><b>分流规则</b>（客户端只传 {@code conversation_id}，不传状态）：取该会话最新一条
 * {@code workflow_run}——中断（4）则续跑并复用同一条执行记录，终态（2/3/5）或没有记录则新起一条，
 * 执行中（0/1）直接拒绝，避免同一会话并发跑两条流程。
 *
 * <p><b>中断与续跑</b>：中断点用内存 Map 保存（{@code executeId} → {@link InterruptionMetadata}），
 * 续跑时必须把**同一个中断对象**交回图引擎（{@code addHumanFeedback}）才能从断点继续，
 * 因此进程重启后该会话不可续跑——这与 {@code workflow_run} 表「执行详情由 SSE 承载、不落库」的口径一致。
 * 检查点用同生命周期的 {@link MemorySaver}。
 *
 * <p><b>落库范围</b>：只写执行实例与会话的元数据（状态、时间、耗时、标题）；
 * 卡片、最终答案与用户提交内容**只走事件流**，不落库。
 */
@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);

    /** SSE 失败帧里的错误码（HTTP 已是 200 事件流，错误只能走流内） */
    private static final int CODE_SERVER_ERROR = 500;

    /** 会话标题长度上限（与 workflow_conversation.title 的 varchar(200) 对齐） */
    private static final int TITLE_MAX_LENGTH = 200;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 中断点：executeId → 中断信息（续跑复用同一条执行记录，故按执行编号索引） */
    private final Map<String, InterruptionMetadata> interruptions = new ConcurrentHashMap<>();

    /** 中断续跑依赖检查点，与中断点同生命周期 */
    private final MemorySaver checkpointSaver = new MemorySaver();

    private final WorkflowService workflowService;
    private final WorkflowDefinitionRepository workflowRepository;
    private final WorkflowConversationRepository conversationRepository;
    private final WorkflowRunRepository runRepository;
    private final ChatClient nodeChatClient;

    public ChatServiceImpl(WorkflowService workflowService,
                          WorkflowDefinitionRepository workflowRepository,
                          WorkflowConversationRepository conversationRepository,
                          WorkflowRunRepository runRepository,
                          @Qualifier("nodeChatClient") ChatClient nodeChatClient) {
        this.workflowService = workflowService;
        this.workflowRepository = workflowRepository;
        this.conversationRepository = conversationRepository;
        this.runRepository = runRepository;
        this.nodeChatClient = nodeChatClient;
    }

    @Override
    public SseEmitter chat(ChatReqVO reqVO) {
        String conversationId = trimToNull(reqVO == null ? null : reqVO.getConversationId());
        if (conversationId == null) {
            throw badRequest("会话编号 conversation_id 不能为空");
        }
        WorkflowConversationDO conversation = conversationRepository.selectByConversationId(conversationId)
                .orElseThrow(() -> badRequest("会话不存在: " + conversationId));

        String requestedWorkflowId = trimToNull(reqVO.getWorkflowId());
        if (requestedWorkflowId != null && !requestedWorkflowId.equals(conversation.getWorkflowId())) {
            throw badRequest("会话绑定的工作流与请求不一致，请重新建立会话");
        }

        // 定义必须已落库：/chat 不接受内联画布定义（定义来源见 WorkflowService#getProcess）
        WorkflowProcessRespVO process = workflowService.getProcess(conversation.getWorkflowId());

        Optional<WorkflowRunDO> latestRun = runRepository.selectLatestByConversationId(conversationId);
        int latestStatus = latestRun.map(run -> run.getStatus() == null ? -1 : run.getStatus()).orElse(-1);
        if (latestStatus == WorkflowConstants.RUN_STATUS_PENDING
                || latestStatus == WorkflowConstants.RUN_STATUS_RUNNING) {
            throw badRequest("该会话已有正在执行的工作流，请等待本次执行结束");
        }

        boolean resume = latestStatus == WorkflowConstants.RUN_STATUS_INTERRUPTED;
        InterruptionMetadata resumeFrom = null;
        Map<String, Object> humanFeedback = null;
        String executeId;
        if (resume) {
            executeId = latestRun.get().getExecuteId();
            resumeFrom = interruptions.remove(executeId);
            if (resumeFrom == null) {
                throw badRequest("该会话的人工确认已失效（服务重启后不可续跑），请重新发起执行");
            }
            humanFeedback = valuesOf(reqVO.getMessage());
            runRepository.markRunning(executeId);
            log.info("[chat] 续跑执行，conversationId={}，executeId={}", conversationId, executeId);
        } else {
            executeId = UUID.randomUUID().toString();
            runRepository.insert(newRun(conversation, executeId));
            log.info("[chat] 新建执行，conversationId={}，executeId={}，workflowId={}",
                    conversationId, executeId, conversation.getWorkflowId());
        }
        conversationRepository.touch(conversationId);

        SseEmitter emitter = new SseEmitter(0L);
        ChatSsePublisher publisher = new ChatSsePublisher(
                emitter, conversationId, executeId, UUID.randomUUID().toString());
        publisher.created(conversation.getWorkflowId());
        publisher.inProgress();

        final InterruptionMetadata feedback = resumeFrom;
        final Map<String, Object> submitted = humanFeedback;
        try {
            CompiledGraph graph = WorkflowGraphFactory.createGraph(
                    process, output -> push(publisher, executeId, output), nodeChatClient, checkpointSaver);

            RunnableConfig.Builder configBuilder = RunnableConfig.builder().threadId(executeId);
            Map<String, Object> input = Map.of();
            if (feedback != null) {
                // 带上原中断对象 + 本次填写值：人工确认节点据此不再中断，并把填写值回写状态
                configBuilder.addHumanFeedback(feedback)
                        .addStateUpdate(Map.of(ChatProtocol.HUMAN_FEEDBACK_KEY, submitted))
                        .resume();
            } else {
                input = initialState();
            }

            graph.stream(input, configBuilder.build()).subscribe(
                    output -> push(publisher, executeId, output),
                    error -> failRun(publisher, executeId, error),
                    () -> finishRun(publisher, executeId));
        } catch (GraphStateException | RuntimeException e) {
            // 建图失败 / 提交失败：HTTP 已按事件流返回，错误只能走流内
            failRun(publisher, executeId, e);
        }
        return emitter;
    }

    @Override
    public ConversationVO createConversation(ConversationCreateReqVO reqVO) {
        String workflowId = trimToNull(reqVO == null ? null : reqVO.getWorkflowId());
        if (workflowId == null) {
            throw badRequest("工作流 id 不能为空");
        }
        WorkflowDO workflow = workflowRepository.selectById(workflowId)
                .orElseThrow(() -> badRequest("工作流不存在: " + workflowId));

        WorkflowConversationDO conversation = new WorkflowConversationDO();
        conversation.setId(String.valueOf(IdUtil.getSnowflakeNextId()));
        conversation.setConversationId(UUID.randomUUID().toString());
        conversation.setWorkflowId(workflow.getId());
        conversation.setWorkflowName(workflow.getName());
        conversation.setTitle(titleOf(reqVO.getTitle(), workflow.getName()));
        conversation.setCreator(currentUserId());
        conversationRepository.insert(conversation);
        log.info("[chat] 新建会话，conversationId={}，workflowId={}",
                conversation.getConversationId(), workflow.getId());
        return toVO(conversation);
    }

    @Override
    public List<ConversationVO> listConversations() {
        return conversationRepository.selectListOrderByUpdateTime().stream()
                .map(ChatServiceImpl::toVO)
                .collect(Collectors.toList());
    }

    /** 节点输出 → 事件：中断转成表单卡片帧，其余转成节点产出帧 */
    private void push(ChatSsePublisher publisher, String executeId, NodeOutput output) {
        if (output instanceof InterruptionMetadata interruption) {
            // 记下中断点供续跑复用（只能存内存，见类注释）
            interruptions.put(executeId, interruption);
            publisher.card(nodeIdOf(interruption), titleOf(interruption),
                    itemListOf(interruption, "data"), itemListOf(interruption, "fields"));
            return;
        }
        Map<String, Object> state = output.state() == null ? Map.of() : output.state().data();
        publisher.nodeFinished(output.node(), state);
    }

    /** 流正常结束：以「是否有待确认的中断点」区分中断与成功（中断轮不发 chat.completed） */
    private void finishRun(ChatSsePublisher publisher, String executeId) {
        if (interruptions.containsKey(executeId)) {
            runRepository.markFinished(executeId, WorkflowConstants.RUN_STATUS_INTERRUPTED);
            publisher.requiresAction();
        } else {
            runRepository.markFinished(executeId, WorkflowConstants.RUN_STATUS_SUCCESS);
            publisher.completed();
        }
        publisher.done();
        publisher.complete();
    }

    /** 流异常结束：落失败态并推失败帧（HTTP 仍 200，终态由 chat.failed 表达） */
    private void failRun(ChatSsePublisher publisher, String executeId, Throwable error) {
        interruptions.remove(executeId);
        try {
            runRepository.markFinished(executeId, WorkflowConstants.RUN_STATUS_FAILED);
        } catch (RuntimeException e) {
            log.warn("[chat] 落失败态出错，executeId={}: {}", executeId, e.getMessage());
        }
        log.error("[chat] 执行失败，executeId={}", executeId, error);
        publisher.failed(CODE_SERVER_ERROR, messageOf(error));
        publisher.done();
        publisher.complete();
    }

    private WorkflowRunDO newRun(WorkflowConversationDO conversation, String executeId) {
        WorkflowRunDO run = new WorkflowRunDO();
        run.setId(String.valueOf(IdUtil.getSnowflakeNextId()));
        run.setExecuteId(executeId);
        run.setConversationId(conversation.getConversationId());
        run.setWorkflowId(conversation.getWorkflowId());
        run.setWorkflowName(conversation.getWorkflowName());
        run.setStatus(WorkflowConstants.RUN_STATUS_RUNNING);
        run.setCreator(currentUserId());
        return run;
    }

    /** /chat 的初始状态：沿用既有占位键，无业务含义（首轮的业务输入由开始节点提供模拟数据） */
    private Map<String, Object> initialState() {
        Map<String, Object> state = new HashMap<>();
        state.put("__START__", "777");
        return state;
    }

    private static String nodeIdOf(InterruptionMetadata interruption) {
        return String.valueOf(interruption.metadata("nodeId").orElse(interruption.node()));
    }

    private static String titleOf(InterruptionMetadata interruption) {
        Object label = interruption.metadata("label").orElse(null);
        return label == null ? "" : String.valueOf(label);
    }

    /** 取出中断 metadata 里的列表（卡片 data / fields），并统一成 Map 的形态 */
    private static List<Map<String, Object>> itemListOf(InterruptionMetadata interruption, String key) {
        Object value = interruption.metadata(key).orElse(null);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> typed = new LinkedHashMap<>();
            map.forEach((name, fieldValue) -> typed.put(String.valueOf(name), fieldValue));
            result.add(typed);
        }
        return result;
    }

    /** 续跑必须带表单填写值：key 对应中断节点 outputs 声明的变量名 */
    private static Map<String, Object> valuesOf(ChatMessageVO message) {
        if (message == null || message.getValues() == null || message.getValues().isEmpty()) {
            throw badRequest("人工确认内容不能为空（message.values）");
        }
        return message.getValues();
    }

    private static ConversationVO toVO(WorkflowConversationDO conversation) {
        ConversationVO vo = new ConversationVO();
        vo.setConversationId(conversation.getConversationId());
        vo.setWorkflowId(conversation.getWorkflowId());
        vo.setWorkflowName(conversation.getWorkflowName());
        vo.setTitle(conversation.getTitle());
        vo.setCreateTime(formatTime(conversation.getCreateTime()));
        vo.setUpdateTime(formatTime(conversation.getUpdateTime()));
        return vo;
    }

    private static Long currentUserId() {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        return loginUser == null ? null : loginUser.getId();
    }

    /** 标题缺省取工作流名称（会话列表直接可读），并截到列宽上限 */
    private static String titleOf(String title, String fallback) {
        String value = trimToNull(title);
        if (value == null) {
            value = fallback;
        }
        if (value == null) {
            return null;
        }
        return value.length() > TITLE_MAX_LENGTH ? value.substring(0, TITLE_MAX_LENGTH) : value;
    }

    private static String formatTime(LocalDateTime time) {
        return time == null ? null : TIME_FORMATTER.format(time);
    }

    private static String messageOf(Throwable error) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.isBlank() ? "工作流执行失败" : message;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static ServiceException badRequest(String message) {
        return new ServiceException(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), message);
    }

}
