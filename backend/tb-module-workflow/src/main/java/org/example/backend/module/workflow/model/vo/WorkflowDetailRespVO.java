package org.example.backend.module.workflow.model.vo;

import lombok.Data;

/**
 * 工作流详情 Response（对齐前端 {@code api/workflow.js} 的 WorkflowDetailVO）。
 *
 * <p>原契约中的 {@code workflow_version} 已下线：不做版本管理，故不再返回该字段。
 */
@Data
public class WorkflowDetailRespVO {

    /** 工作流主体 */
    private WorkflowVO workflow;

}
