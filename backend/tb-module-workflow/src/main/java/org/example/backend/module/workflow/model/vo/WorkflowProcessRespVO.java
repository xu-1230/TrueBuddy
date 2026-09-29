package org.example.backend.module.workflow.model.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 工作流执行状态响应（{@code GET /workflow/get_process?workflow_id=} 的 {@code data}）。
 *
 * <p>整体字段口径对齐 Coze 的执行状态接口（{@code exeHistoryStatus} / {@code executeStatus} /
 * {@code workflowExeCost} / {@code nodeResults} 等），状态一律使用数字码，便于前端按数字判态。
 *
 * <p>业务结果（批改清单、逐份记录、结束节点输出等）不展开在顶层，而是执行结束后整体放入
 * {@link #result}，避免与进度字段混杂。
 */
@Data
public class WorkflowProcessRespVO {

    /** 执行状态：执行中 */
    public static final int EXE_STATUS_RUNNING = 1;
    /** 执行状态：执行成功 */
    public static final int EXE_STATUS_FINISHED = 2;
    /** 执行状态：执行失败 */
    public static final int EXE_STATUS_FAILED = 3;
    /** 执行状态：等待用户输入（人工确认节点已暂停，**非终态**，调用方应继续轮询 / 等 SSE） */
    public static final int EXE_STATUS_WAITING = 4;

    /** 执行结果码：正常 */
    public static final int EXECUTE_STATUS_NORMAL = 1;
    /** 执行结果码：异常 */
    public static final int EXECUTE_STATUS_ABNORMAL = 2;

    /** 项目 id（固定 "0"，对齐 Coze 契约） */
    public static final String DEFAULT_PROJECT_ID = "0";

    /** 执行会话 id（= {@code POST /workflow/run} 的返回值，轮询时回传） */
    private String executeId;

    /** 本次执行所用画布对应的工作流定义 id；画布由请求直接传入且未关联已保存工作流时为 null */
    private String workFlowId;

    /** 执行状态：1=执行中，2=执行成功，3=执行失败，4=等待用户输入（非终态） */
    private Integer exeHistoryStatus = EXE_STATUS_RUNNING;

    /** 执行结果码：1=正常，2=异常 */
    private Integer executeStatus = EXECUTE_STATUS_NORMAL;

    /** 总耗时（人类可读，如 {@code 0.000s} / {@code 2s} / {@code 1m32s}） */
    private String workflowExeCost = "0.000s";

    /** 总耗时（毫秒） */
    private Long exeCostMs = 0L;

    /** 事件列表（预留，固定空数组） */
    private List<Object> nodeEvents = new ArrayList<>();

    /** 节点级执行结果（按画布节点顺序，每个可执行节点一条） */
    private List<NodeResultVO> nodeResults = new ArrayList<>();

    /** token 与费用（本项目固定零值占位） */
    private TokenAndCostVO tokenAndCost = new TokenAndCostVO();

    /** 项目 id（固定 "0"） */
    private String projectId = DEFAULT_PROJECT_ID;

    /** 提示信息：执行中为空串，成功为完成提示，失败为失败原因 */
    private String message = "";

    /** 业务结果：执行中为 null，终态返回完整结果 */
    private WorkflowRunRespVO result;

    /**
     * 待确认清单：仅在 {@code exeHistoryStatus = 4}（等待用户输入）时有值。
     *
     * <p>调用方据此渲染人工确认卡片（画布页挂在暂停节点正下方），提交时把勾选结果回传给
     * {@code POST /workflow/run}（带 {@code executeId} + {@code confirmedPlan}）以恢复执行。
     */
    private WorkflowPendingConfirmVO pendingConfirm;

}
