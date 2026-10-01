package org.example.textflow.service;

import org.example.textflow.entity.WorkflowProcessRespVO;

public interface WorkflowService {

    /**
     * 获取工作流的编排定义（节点 + 连线）。
     *
     * @param workflowId 工作流标识
     */
    WorkflowProcessRespVO getProcess(String workflowId);

}
