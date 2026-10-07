package org.example.textflow.model;

/**
 * {@code /chat} 事件流协议常量：事件名、状态、消息维度与卡片类型。
 *
 * <p>命名对齐字节 Coze 开放平台的 {@code /v1/chat} 事件流（{@code conversation.chat.*} /
 * {@code conversation.message.*} / {@code done}），但**裁掉了冗余信号**——同一件事只留一个帧：
 * 中断只用 {@code conversation.chat.requires_action}（不再叠 {@code verbose} 的
 * {@code msg_type=interrupt} 与 {@code finish_reason}），成败只用
 * {@code conversation.chat.completed} / {@code .failed}。
 *
 * <p>{@code conversation.message.delta}（逐字增量）一期**保留事件名但不发送**：大模型节点当前是
 * 模拟产出、没有真流式，前端按「有 delta 就逐字渲染、没有就整段渲染」实现，二期接真模型时不必改协议。
 *
 * <p>载荷字段用 {@code snake_case}（{@code conversation_id} / {@code execute_id} / {@code chat_id} …）
 * 与事件名同源，便于与 Coze 文档逐字对照；REST 请求体仍用项目惯例的 camelCase。
 */
public final class ChatProtocol {

    // ==================== 事件名 ====================

    /** 每轮交互的第一帧：请求被接受，会话 / 执行 / 轮次三个标识在此下发 */
    public static final String CHAT_CREATED = "conversation.chat.created";

    /** 每轮交互的第二帧：图开始执行 */
    public static final String CHAT_IN_PROGRESS = "conversation.chat.in_progress";

    /** 文本增量帧：一期不发（保留事件名，见类注释） */
    public static final String MESSAGE_DELTA = "conversation.message.delta";

    /** 一条消息完成：节点产出（verbose）/ 表单卡片（card）/ 最终答案（text） */
    public static final String MESSAGE_COMPLETED = "conversation.message.completed";

    /** 中断终态：等待人工确认（非成功，也非失败） */
    public static final String CHAT_REQUIRES_ACTION = "conversation.chat.requires_action";

    /** 成功终态 */
    public static final String CHAT_COMPLETED = "conversation.chat.completed";

    /** 失败终态 */
    public static final String CHAT_FAILED = "conversation.chat.failed";

    /** 流关闭帧：**每轮都发，与成败无关**（判断终态看上面三个终态帧，不看流是否结束） */
    public static final String DONE = "done";

    // ==================== chat 状态 ====================

    public static final String STATUS_CREATED = "created";
    public static final String STATUS_IN_PROGRESS = "in_progress";
    public static final String STATUS_REQUIRES_ACTION = "requires_action";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_FAILED = "failed";

    // ==================== message 维度 ====================

    /** 消息角色：模型侧（本模块只推模型侧消息） */
    public static final String ROLE_ASSISTANT = "assistant";

    /** 消息类型：正文 */
    public static final String TYPE_ANSWER = "answer";

    /** 消息类型：过程消息（节点产出走这里） */
    public static final String TYPE_VERBOSE = "verbose";

    public static final String CONTENT_TYPE_CARD = "card";
    public static final String CONTENT_TYPE_TEXT = "text";
    public static final String CONTENT_TYPE_VERBOSE = "verbose";

    /** 过程消息子类型：某个节点执行完成 */
    public static final String MSG_TYPE_NODE_FINISHED = "node_finished";

    // ==================== 表单卡片 ====================

    /** 卡片类型：表单输入卡片 */
    public static final String CARD_TYPE_INPUT = "INPUT";

    /** 续跑请求里 {@code message.type} 的取值：提交表单卡片 */
    public static final String MESSAGE_TYPE_CARD_SUBMIT = "card_submit";

    /**
     * 人工反馈注入图状态的键。
     *
     * <p>续跑时由调用方经 {@code RunnableConfig.Builder#addStateUpdate} 注入「字段名 → 取值」，
     * 人工确认节点的 {@code apply} 再把它按 {@code state[节点id][变量名]} 的口径回写。
     * 该键在 {@code WorkflowGraphFactory#keyStrategyFactory()} 里**显式注册**（{@code ReplaceStrategy}），
     * 否则检查点合并时会按策略表过滤、把未注册的键静默丢弃。
     */
    public static final String HUMAN_FEEDBACK_KEY = "__human_feedback__";

    private ChatProtocol() {
    }

}
