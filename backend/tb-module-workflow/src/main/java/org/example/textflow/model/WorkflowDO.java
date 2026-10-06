package org.example.textflow.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作流定义 DO（表 {@code workflow}）。
 *
 * <p>一次「画布编排」= 一条记录；画布定义（{@code {nodes, edges}}，含迭代节点内嵌的
 * {@code blocks} / {@code edges}）整体存入 {@link #schemaJson}，不拆分节点 / 连线子表。
 *
 * <p>主键是雪花 ID（19 位），已超出前端 JS 的安全整数范围（{@code Number.MAX_SAFE_INTEGER}），
 * 因此这里直接用 {@link String} 承载，读出来是什么就传什么，避免中间环节被转成数值后丢精度。
 */
@Data
public class WorkflowDO {

    /** 主键（雪花 ID，字符串承载以免前端丢精度） */
    private String id;

    /** 流程名称 */
    private String name;

    /** 流程说明 */
    private String description;

    /** 流程定义 JSON：{@code {nodes, edges}} */
    private String schemaJson;

    /** 状态：0=草稿，1=已发布 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

}
