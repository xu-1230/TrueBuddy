package org.example.backend.module.workflow.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作流定义 DO（表 workflow）。
 *
 * <p>画布结构整存于 {@code schema_json}：不拆分节点 / 连线子表，
 * 迭代嵌套 blocks 与后端专有的 inputs 配置（人工确认清单的 ref 来源）才能原样保真。
 *
 * <p>主键为雪花 ID，由 Service 用 {@code IdUtil.getSnowflakeNextId()} 生成后写入，
 * 因此表不使用数据库自增。
 */
@Data
public class WorkflowDO {

    /** 主键（雪花 ID） */
    private Long id;

    /** 工作流名称 */
    private String name;

    /** 工作流说明（VO 中字段名为 desc） */
    private String description;

    /** 画布结构（JSON 字符串） */
    private String schemaJson;

    /** 状态：0=草稿，1=已发布 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 创建人 id（system_user.id，逻辑外键） */
    private Long creator;

    /** 更新人 id（system_user.id，逻辑外键） */
    private Long updater;

    /** 软删：false=否，true=是 */
    private Boolean deleted;

}
