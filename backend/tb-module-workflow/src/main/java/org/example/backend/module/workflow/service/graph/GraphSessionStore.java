package org.example.backend.module.workflow.service.graph;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.module.workflow.config.WorkflowProperties;
import org.example.backend.module.workflow.model.schema.WorkflowGraphModel;
import org.example.backend.module.workflow.model.vo.NodeResultVO;
import org.example.backend.module.workflow.model.vo.PlanItemVO;
import org.example.backend.module.workflow.model.vo.WorkflowRunRespVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.example.backend.framework.common.exception.util.ServiceExceptionUtil.exception;
import static org.example.backend.module.workflow.enums.ErrorCodeConstants.WORKFLOW_SESSION_NOT_FOUND;

/**
 * 工作流执行会话存储（内存实现，MVP 阶段不持久化）。
 *
 * <p>会话以「执行会话 id（{@code executeId}）」为键，保存「已编译图 + 运行配置 + 节点级执行结果
 * + 终态业务结果」，支撑「SSE 实时推送为主、{@code GET /workflow/get_process} 轮询兜底」的调用模型：
 * 执行结束后会话**保留**至 TTL 到期，保证调用方断线重连 / 刷新页面后仍能取到进度与完整结果。
 *
 * <p>人工确认：执行到人工确认节点时，会话状态置 {@code WAITING_CONFIRM} 并记录待确认清单
 * （{@link GraphSession#getPendingPlan()}），恢复接口据此校验并注入 {@code confirmedPlan} 续跑。
 *
 * <p>并发：执行线程写、请求线程读，因此 {@link GraphSession#getNodeResults()} 等可变集合的
 * 读写由使用方在 {@code session} 对象上加锁。
 *
 * <p>过期策略：访问时惰性清理（不引入定时任务 / 线程池基础设施）；
 * 进程重启后会话丢失，本阶段可接受。
 */
@Slf4j
@Component
public class GraphSessionStore {

    private final Map<String, GraphSession> sessions = new ConcurrentHashMap<>();

    private final WorkflowProperties properties;

    public GraphSessionStore(WorkflowProperties properties) {
        this.properties = properties;
    }

    /** 保存 / 覆盖会话，并顺带清理过期会话 */
    public void save(GraphSession session) {
        evictExpired();
        session.setUpdatedAt(System.currentTimeMillis());
        sessions.put(session.getExecuteId(), session);
    }

    /** 读取会话；不存在或已过期时抛业务异常 */
    public GraphSession get(String executeId) {
        evictExpired();
        GraphSession session = executeId == null ? null : sessions.get(executeId);
        if (session == null) {
            throw exception(WORKFLOW_SESSION_NOT_FOUND);
        }
        return session;
    }

    /** 删除会话（执行结果不再需要查询时可调用） */
    public void remove(String executeId) {
        sessions.remove(executeId);
    }

    private void evictExpired() {
        long ttlMillis = Math.max(1, properties.getSessionTtlMinutes()) * 60_000L;
        long now = System.currentTimeMillis();
        List<String> expired = new ArrayList<>();
        for (Map.Entry<String, GraphSession> entry : sessions.entrySet()) {
            if (now - entry.getValue().getUpdatedAt() > ttlMillis) {
                expired.add(entry.getKey());
            }
        }
        for (String executeId : expired) {
            sessions.remove(executeId);
            log.info("[evictExpired][工作流执行会话已过期并被清理] executeId={}", executeId);
        }
    }

    /**
     * 工作流执行会话。
     */
    @Data
    @Accessors(chain = true)
    public static class GraphSession {

        /** 执行会话 id（同时作为 graph 的 threadId） */
        private String executeId;

        /** 已编译的图 */
        private CompiledGraph graph;

        /** 本次运行的配置（含进度监听器元数据） */
        private RunnableConfig config;

        /** 执行状态 */
        private WorkflowRunStatus status;

        /** 已编译的图模型（组装响应时用于定位确认节点 / 循环节点 / 结束节点） */
        private WorkflowGraphModel model;

        /** 本次执行所用画布对应的工作流定义 id（画布直接传入且未关联已保存工作流时为 null） */
        private String workflowId;

        /** 开始节点播种输入（START 节点的输入 / 输出展示用） */
        private Map<String, Object> seedInputs = new LinkedHashMap<>();

        /** 节点级执行结果，按画布节点顺序（含 Start / End） */
        private Map<String, NodeResultVO> nodeResults = new LinkedHashMap<>();

        /** 已执行节点顺序（联调用） */
        private List<String> nodeTrace = new ArrayList<>();

        /** 最近一次状态快照 */
        private Map<String, Object> lastState;

        /** 暂停在人工确认节点时的节点 id；未处于等待态时为 null */
        private String interruptNodeId;

        /** 暂停在人工确认节点时规划出的批改清单（全部条目）；未处于等待态时为 null */
        private List<PlanItemVO> pendingPlan;

        /** 暂停在人工确认节点时的默认勾选清单；未处于等待态时为 null */
        private List<String> pendingDefaultPlan;

        /** 本次中断的框架元数据（含清单等）；恢复时作为人工反馈回传，未处于等待态时为 null */
        private InterruptionMetadata interruption;

        /** 终态业务结果（执行中为 null） */
        private WorkflowRunRespVO result;

        /** 提示信息：执行中为空串，成功为完成提示，失败为失败原因 */
        private String message;

        /** 总耗时（毫秒，执行结束后填充） */
        private Long exeCostMs;

        /** 提交时间（毫秒） */
        private long startedAt;

        /** 结束时间（毫秒） */
        private long finishedAt;

        /** 节点耗时游标：上一个节点完成的时刻 */
        private long nodeCursor;

        /** 创建时间（毫秒） */
        private long createdAt;

        /** 最近访问时间（毫秒） */
        private long updatedAt;

    }

}
