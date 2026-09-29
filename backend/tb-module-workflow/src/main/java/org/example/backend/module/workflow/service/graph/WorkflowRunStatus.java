package org.example.backend.module.workflow.service.graph;

/**
 * 工作流执行状态。
 *
 * <p>对外映射为 {@code /workflow/get_process} 的 {@code exeHistoryStatus}：
 * {@link #RUNNING} → {@code 1}、{@link #FINISHED} → {@code 2}、{@link #FAILED} → {@code 3}、
 * {@link #WAITING_CONFIRM} → {@code 4}。
 *
 * <p>注意：{@link #WAITING_CONFIRM} **不是终态**——执行线程已从人工确认节点返回，但整条流仍在等
 * 调用方提交确认清单，因此轮询方不能把它当作结束条件。
 */
public enum WorkflowRunStatus {

    /** 已提交，正在执行（提交接口立即返回，进度靠 SSE 推送 / 轮询获取） */
    RUNNING,

    /** 执行到人工确认节点暂停，等待调用方提交确认清单（非终态） */
    WAITING_CONFIRM,

    /** 已执行完成 */
    FINISHED,

    /** 执行失败 */
    FAILED,

}
