package org.example.backend.module.workflow.model.schema;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 工作流定义外层结构（对齐前端 {@code api/workflow.js} 的 WorkflowDetailVO）：
 * {@code { workflow: {...}, workflow_version: "v0.0.1" }}。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowDetailSchema {

    /** 工作流主体 */
    private WorkflowMetaSchema workflow;

    /** 工作流版本号，如 v0.0.1 */
    @JsonProperty("workflow_version")
    private String workflowVersion;

}
