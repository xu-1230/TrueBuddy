package org.example.textflow.service;

import org.example.textflow.entity.WorkflowProcessRespVO;
import org.example.textflow.model.vo.PublicWorkflowVO;
import org.example.textflow.model.vo.WorkflowDetailRespVO;
import org.example.textflow.model.vo.WorkflowSaveReqVO;
import org.example.textflow.model.vo.WorkflowVO;

import java.util.List;

public interface WorkflowService {

    /**
     * 获取工作流的编排定义（节点 + 连线）。
     *
     * <p>当前实现仍读 classpath {@code text.json}（执行链路的行为保持不变）；
     * 画布定义的持久化由 {@link #saveWorkflow} / {@link #getWorkflowDetail} 承担。
     *
     * @param workflowId 工作流标识
     */
    WorkflowProcessRespVO getProcess(String workflowId);

    /**
     * 保存工作流定义。
     *
     * @param reqVO 入参；{@code id} 为空表示新建（生成雪花 ID），非空表示更新该记录
     * @return 工作流 id（雪花 ID 字符串）
     */
    String saveWorkflow(WorkflowSaveReqVO reqVO);

    /**
     * 查询工作流详情（含画布定义 JSON）。
     *
     * @param workflowId 工作流 id（雪花 ID 字符串）
     */
    WorkflowDetailRespVO getWorkflowDetail(String workflowId);

    /**
     * 查询工作流列表（不含画布定义，按更新时间倒序）。
     */
    List<WorkflowVO> listWorkflows();

    /**
     * 切换本人工作流的公有 / 私有（builtin 模板不可改）。
     *
     * @param workflowId 工作流 id（雪花 ID 字符串）
     * @param visibility 0=私有，1=公有
     */
    void updateVisibility(String workflowId, Integer visibility);

    /**
     * 他人公有工作流列表（visibility=1 且非本人创建；匿名返回全部公有行）。
     */
    List<PublicWorkflowVO> listPublicWorkflows();

    /**
     * fork 一份工作流为当前用户的副本：拷名称（「原名（副本）」）/ 说明 / 画布定义，
     * 草稿态、私有、归属当前用户。他人公有流运行的唯一前置通道（ADR 0006 决策 4）。
     *
     * @param workflowId 被 fork 的工作流 id（须为本人或他人公有）
     * @return 副本 id（雪花 ID 字符串）
     */
    String forkWorkflow(String workflowId);

    /**
     * 重命名本人工作流（builtin 拒绝）。只改名称，不触碰画布定义，
     * 避免列表为了改名而拉取并回写整份 `schemaJson`。
     *
     * @param workflowId 工作流 id（雪花 ID 字符串）
     * @param name 新名称（trim 后非空，≤100 字符）
     */
    void renameWorkflow(String workflowId, String name);

    /**
     * 软删除本人工作流（builtin 拒绝）。已存在的会话 / 执行记录不级联删除，
     * 但后续再用该工作流建会话或运行会按「工作流不存在」拒绝。
     *
     * @param workflowId 工作流 id（雪花 ID 字符串）
     */
    void deleteWorkflow(String workflowId);

}
