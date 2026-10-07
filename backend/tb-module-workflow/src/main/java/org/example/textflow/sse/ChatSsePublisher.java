package org.example.textflow.sse;

import org.example.textflow.model.ChatProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code /chat} 的事件流推送器：把执行过程中的状态与消息封成 {@code conversation.*} 事件
 * （协议见 {@link ChatProtocol}）。
 *
 * <p>每帧都带 {@code chat_id} / {@code conversation_id} / {@code execute_id} 三个标识，
 * 前端据此串联「会话 - 轮次 - 执行」：{@code conversation_id} 跨轮不变、{@code chat_id} 每轮新生成、
 * {@code execute_id} 跨轮复用（同一行 {@code workflow_run}）。
 *
 * <p><b>断开语义</b>：发送失败（客户端断开、容器回收）只记 DEBUG 日志、**不打断图执行**——
 * 前端断开不改变服务端状态，执行照常跑完并把状态落到 {@code workflow_run}。
 */
public class ChatSsePublisher {

    private static final Logger log = LoggerFactory.getLogger(ChatSsePublisher.class);

    private final SseEmitter emitter;
    private final String conversationId;
    private final String executeId;
    private final String chatId;
    private final long createdAt = Instant.now().getEpochSecond();

    public ChatSsePublisher(SseEmitter emitter, String conversationId, String executeId, String chatId) {
        this.emitter = emitter;
        this.conversationId = conversationId;
        this.executeId = executeId;
        this.chatId = chatId;
    }

    public String getExecuteId() {
        return executeId;
    }

    public String getChatId() {
        return chatId;
    }

    /** 第一帧：请求已受理，下发三个标识与绑定的工作流 */
    public void created(String workflowId) {
        Map<String, Object> data = envelope();
        data.put("workflow_id", workflowId);
        data.put("status", ChatProtocol.STATUS_CREATED);
        data.put("last_error", lastError(0, ""));
        send(ChatProtocol.CHAT_CREATED, data);
    }

    /** 第二帧：图开始执行 */
    public void inProgress() {
        Map<String, Object> data = envelope();
        data.put("status", ChatProtocol.STATUS_IN_PROGRESS);
        data.put("last_error", lastError(0, ""));
        send(ChatProtocol.CHAT_IN_PROGRESS, data);
    }

    /**
     * 节点产出帧：{@code content.data.node} 与 {@code content.data.state[node]} 与旧 {@code node} 事件同形，
     * 前端画布页的运行态归约逻辑可直接复用。
     */
    public void nodeFinished(String node, Map<String, Object> state) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("node", node);
        payload.put("state", state == null ? Map.of() : state);

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("msg_type", ChatProtocol.MSG_TYPE_NODE_FINISHED);
        content.put("data", payload);
        sendMessage(ChatProtocol.TYPE_VERBOSE, ChatProtocol.CONTENT_TYPE_VERBOSE, content);
    }

    /**
     * 表单卡片帧：中断节点的弹窗数据。
     *
     * <p>{@code data} 是只读回显的「返回数据」（上游产出），{@code fields} 是待用户填写的字段。
     * 字段清单**只在这一处**下发（不像 Coze 散在 {@code input_card_data} / {@code variables} /
     * {@code elements} 三处还得手工保持一致）。
     */
    public void card(String nodeId, String title, List<Map<String, Object>> data, List<Map<String, Object>> fields) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("response_type", ChatProtocol.CONTENT_TYPE_CARD);
        content.put("card_type", ChatProtocol.CARD_TYPE_INPUT);
        content.put("node_id", nodeId);
        content.put("title", title);
        content.put("data", data == null ? List.of() : data);
        content.put("fields", fields == null ? List.of() : fields);
        sendMessage(ChatProtocol.TYPE_ANSWER, ChatProtocol.CONTENT_TYPE_CARD, content);
    }

    /** 最终答案帧（结束节点组装正式结果后启用；当前结束节点仍是 TODO，故一期不发） */
    public void answer(String text) {
        sendMessage(ChatProtocol.TYPE_ANSWER, ChatProtocol.CONTENT_TYPE_TEXT, text);
    }

    /** 中断终态：等待人工确认（**中断轮不发 {@code chat.completed}**） */
    public void requiresAction() {
        terminal(ChatProtocol.STATUS_REQUIRES_ACTION, ChatProtocol.CHAT_REQUIRES_ACTION, null);
    }

    /** 成功终态 */
    public void completed() {
        terminal(ChatProtocol.STATUS_COMPLETED, ChatProtocol.CHAT_COMPLETED, null);
    }

    /** 失败终态 */
    public void failed(int code, String message) {
        terminal(ChatProtocol.STATUS_FAILED, ChatProtocol.CHAT_FAILED, lastError(code, message));
    }

    /** 流关闭帧：每轮都发，与成败无关 */
    public void done() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("conversation_id", conversationId);
        data.put("execute_id", executeId);
        send(ChatProtocol.DONE, data);
    }

    public void complete() {
        emitter.complete();
    }

    public void completeWithError(Throwable error) {
        emitter.completeWithError(error);
    }

    /** 每帧共有的三个标识与创建时间 */
    private Map<String, Object> envelope() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("chat_id", chatId);
        data.put("conversation_id", conversationId);
        data.put("execute_id", executeId);
        data.put("created_at", createdAt);
        return data;
    }

    private void sendMessage(String type, String contentType, Object content) {
        Map<String, Object> data = envelope();
        data.put("role", ChatProtocol.ROLE_ASSISTANT);
        data.put("type", type);
        data.put("content_type", contentType);
        data.put("content", content);
        send(ChatProtocol.MESSAGE_COMPLETED, data);
    }

    private void terminal(String status, String event, Map<String, Object> error) {
        Map<String, Object> data = envelope();
        data.put("status", status);
        data.put("completed_at", Instant.now().getEpochSecond());
        if (error != null) {
            data.put("last_error", error);
        }
        send(event, data);
    }

    private static Map<String, Object> lastError(int code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("msg", message == null ? "" : message);
        return error;
    }

    private void send(String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException | IllegalStateException e) {
            // 连接已断开：执行不中断（见类注释），只留一条调试日志
            log.debug("[chat] 事件推送失败（连接可能已断开），event={}，executeId={}: {}",
                    event, executeId, e.getMessage());
        }
    }

}
