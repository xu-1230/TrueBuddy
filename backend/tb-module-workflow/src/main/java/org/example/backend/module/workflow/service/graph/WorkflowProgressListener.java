package org.example.backend.module.workflow.service.graph;

/**
 * 工作流执行进度监听器：支撑「执行中的节点状态实时可见」。
 *
 * <p>传递方式：提交接口把它放进
 * {@link com.alibaba.cloud.ai.graph.RunnableConfig} 的元数据（键为 {@link #METADATA_KEY}），
 * 建图时生成的节点包装器再从配置里取出并挂到 {@link WorkflowStateView}；循环节点每完成一条即回调，
 * 随后由调用方决定如何处理（当前实现为**推送 SSE 事件**：循环节点开始执行时把该节点置「执行中」，
 * 每完成一条推一条 {@code message} 事件，实现「批改一份回传一份」；
 * 调用方断开时事件推送静默失败，不影响循环执行本身）。
 *
 * <p>骨架方法均有默认空实现，未注入监听器时使用 {@link #NO_OP}。
 */
public interface WorkflowProgressListener {

    /** RunnableConfig 元数据键：进度监听器 */
    String METADATA_KEY = "tb.workflow.progress-listener";

    /** 空实现：未注入监听器的调用使用 */
    WorkflowProgressListener NO_OP = new WorkflowProgressListener() {
    };

    /**
     * 循环开始：告知本次迭代的总条目数。
     *
     * @param loopNodeId 循环节点 id
     * @param total      总条目数
     */
    default void onLoopStart(String loopNodeId, int total) {
    }

    /**
     * 循环内完成一条。
     *
     * @param loopNodeId 循环节点 id
     * @param index      序号（从 1 开始）
     * @param total      总条目数
     * @param item       当前迭代项（如作业文件路径）
     * @param result     该条的原始结果（模拟 / 真实大模型输出的 JSON 文本）
     */
    default void onLoopItem(String loopNodeId, int index, int total, Object item, Object result) {
    }

}
