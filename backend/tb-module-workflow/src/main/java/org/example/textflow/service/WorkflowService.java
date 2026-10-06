package org.example.textflow.service;

import org.example.textflow.entity.WorkflowProcessRespVO;
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

}
