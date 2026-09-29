package org.example.backend.module.workflow.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 工作流 Response VO（对应前端 {@code api/workflow.js} 的 WorkflowVO）。
 *
 * <p>字段口径：下划线命名、时间为秒级时间戳，
 * 且不含已下线字段：type / schema_type / space_id / workflow_version。
 *
 * <p>列表接口只填充 id / name / status / create_time / update_time，
 * desc 与 schema_json 为 null（避免列表拉大字段）。
 */
@Data
public class WorkflowVO {

    @JsonProperty("workflow_id")
    private String workflowId;

    /** 工作流名称 */
    private String name;

    /** 工作流说明 */
    private String desc;

    /** 画布结构（JSON 字符串，需前端 JSON.parse） */
    @JsonProperty("schema_json")
    private String schemaJson;

    /** 状态：0=草稿，1=已发布 */
    private Integer status;

    /** 创建时间（秒级时间戳） */
    @JsonProperty("create_time")
    private Long createTime;

    /** 更新时间（秒级时间戳） */
    @JsonProperty("update_time")
    private Long updateTime;

}
