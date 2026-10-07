package org.example.textflow.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作流执行实例（{@code workflow_run} 表一行）。
 *
 * <p>一次执行（{@code executeId}）落一行，续跑复用同一条记录（{@code status} 4 → 1），终态后再次发起才新增行。
 * 表中不存画布定义、入参、产出与错误详情，执行详情由 SSE 事件流承载。
 */
@Data
public class WorkflowRunDO {

    /** 主键（雪花 ID） */
    private String id;

    /** 本次执行编号（UUID，对外，与 SSE 事件 execute_id 一致） */
    private String executeId;

    /** 所属会话编号（逻辑外键 workflow_conversation.conversation_id） */
    private String conversationId;

    /** 逻辑外键 workflow.id */
    private String workflowId;

    /** 流程名称（冗余，便于展示） */
    private String workflowName;

    /** 状态：0=待运行，1=执行中，2=成功，3=失败，4=中断，5=已取消 */
    private Integer status;

    /** 执行开始时间 */
    private LocalDateTime startTime;

    /** 执行结束时间 */
    private LocalDateTime endTime;

    /** 执行耗时（毫秒） */
    private Long duration;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 创建人 id（system_user.id） */
    private Long creator;

}
