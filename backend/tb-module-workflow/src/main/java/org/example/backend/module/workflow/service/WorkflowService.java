package org.example.backend.module.workflow.service;

import org.example.backend.module.workflow.model.vo.WorkflowDetailRespVO;
import org.example.backend.module.workflow.model.vo.WorkflowProcessRespVO;
import org.example.backend.module.workflow.model.vo.WorkflowRunReqVO;
import org.example.backend.module.workflow.model.vo.WorkflowSaveReqVO;
import org.example.backend.module.workflow.model.vo.WorkflowVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 自定义工作流服务（画布定义已持久化到 workflow 表；大模型与知识库检索仍为模拟实现）。
 *
 * <p>执行模型：{@link #runStream} 提交任务并以 SSE 实时推送进度事件；执行到人工确认节点时暂停，
 * 调用方经 {@link #resume} 提交确认清单后从断点续跑（事件继续推回原通道）；
 * {@link #getProcess} 作轮询兜底，供断线重连 / 刷新页面后取回进度与终态。
 */
public interface WorkflowService {

    /**
     * 提交执行：同步校验画布 → 落执行会话（状态=执行中）→ 注册 SSE 通道 → 后台线程执行整个图。
     *
     * <p>校验失败（画布为空 / JSON 非法 / 结构非法）也以事件流回传：先推 {@code error} 再推
     * {@code done}，调用方只需处理一种协议，无需区分 HTTP 错误与流内错误。
     *
     * @param reqVO 执行入参（{@code schemaJson} 必填；其余字段可缺省，缺省时回退模拟数据）
     * @return SSE 通道；事件序列 {@code created → message* → completed → done}，
     *         执行到人工确认节点时推 {@code message}(requires_action) 且**保持通道打开**
     */
    SseEmitter runStream(WorkflowRunReqVO reqVO);

    /**
     * 恢复人工确认：提交已确认清单，从断点续跑。
     *
     * <p>后续事件继续写回**原来那条 SSE 通道**；本方法返回的通道只作「已受理」应答（推一条
     * {@code done} 后立即结束），调用方无需读取其内容。
     *
     * @param reqVO {@code executeId} 为等待态会话 id，{@code confirmedPlan} 为已确认清单（非空）
     * @return 一次性确认用的 SSE 通道
     */
    SseEmitter resume(WorkflowRunReqVO reqVO);

    /**
     * 查询执行状态与结果（SSE 的轮询兜底，建议每 2 秒一次）。
     *
     * @param executeId 执行会话 id（{@code runStream} 的 {@code created} 事件下发，或恢复请求的入参）
     * @return 执行状态响应；{@code exeHistoryStatus} 为 1=执行中 / 2=执行成功 / 3=执行失败 /
     *         4=等待用户输入（**非终态**，此时额外返回 {@code pendingConfirm} 待确认清单）
     */
    WorkflowProcessRespVO getProcess(String executeId);

    /**
     * 保存工作流定义：{@code id} 为空时新建（生成雪花 ID），非空时更新。
     *
     * <p>保存即覆盖当前记录，**不做版本管理**（无版本表、无历史快照）。
     *
     * @param reqVO 保存入参
     * @return 工作流 id
     */
    Long saveWorkflow(WorkflowSaveReqVO reqVO);

    /**
     * 查询工作流详情（含画布结构）。
     *
     * @param id 工作流 id
     */
    WorkflowDetailRespVO getWorkflowDetail(Long id);

    /**
     * 工作流列表（不含画布结构，按更新时间倒序）。
     */
    List<WorkflowVO> listWorkflows();

}
