package org.example.backend.module.workflow.model.schema;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 工作流主体元信息（对齐前端 {@code WorkflowVO}），其中 {@code schemaJson} 为画布结构。
 *
 * <p>注意：前端契约中 {@code schema_json} 为「JSON 字符串」；本模块内部按对象处理，
 * 解析 {@code /workflow/run} 入参画布时由 Jackson 反序列化为 {@link WorkflowSchema}。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowMetaSchema {

    @JsonProperty("workflow_id")
    private String workflowId;

    /** 工作流名称 */
    private String name;

    /** 工作流说明 */
    private String desc;

    /** schema 版本类型（当前为 1） */
    @JsonProperty("schema_type")
    private Integer schemaType;

    /** 工作流类型（0 = 低代码工作流） */
    private Integer type;

    /** 状态（1 = 已发布） */
    private Integer status;

    /** 所属空间 id */
    @JsonProperty("space_id")
    private String spaceId;

    /** 创建时间（秒级时间戳） */
    @JsonProperty("create_time")
    private Long createTime;

    /** 更新时间（秒级时间戳） */
    @JsonProperty("update_time")
    private Long updateTime;

    /** 画布结构：nodes / edges / versions */
    @JsonProperty("schema_json")
    private WorkflowSchema schema;

}
