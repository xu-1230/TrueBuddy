package org.example.backend.module.workflow.service.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 工作流 SSE 推送通道注册表：把「执行进度 → 调用方」的推送集中到一处。
 *
 * <p>存在理由：{@link SseEmitter} 属于 Web 层类型，不应下沉到图执行/会话层；由本组件持有
 * 「执行会话 id → 通道」映射，执行侧只调用 {@link #send} 即可，断开清理与心跳也在此收口。
 *
 * <p>事件契约（负载序列化为 JSON，即业务数据本身，不经 {@code CommonResult} 包装）：
 * <ul>
 *   <li>{@link #EVENT_CREATED}：会话已建立，携带执行会话 id 与初始节点清单；</li>
 *   <li>{@link #EVENT_MESSAGE}：增量过程事件（节点完成 / 单份批改回传 / 等待用户输入）；</li>
 *   <li>{@link #EVENT_COMPLETED}：整体执行完成，携带终态响应；</li>
 *   <li>{@link #EVENT_ERROR}：校验失败或执行异常；</li>
 *   <li>{@link #EVENT_DONE}：流终止（必发、最后一条），调用方据此收尾。</li>
 * </ul>
 *
 * <p>连接断开（刷新页面 / 网络中断）只**注销**通道并停发事件，**不打断**图执行：已完成的进度与
 * 终态仍留在执行会话里，调用方靠 {@code GET /workflow/get_process} 轮询兜底取回。
 *
 * <p>心跳：每 {@link #HEARTBEAT_INTERVAL_MS} 毫秒向在用通道发一条注释帧，避免反向代理 / 容器按
 * 空闲超时掐断长连接。
 */
@Slf4j
@Component
public class WorkflowStreamRegistry {

    /** 事件名：会话已建立（任务已提交） */
    public static final String EVENT_CREATED = "created";

    /** 事件名：增量过程事件 */
    public static final String EVENT_MESSAGE = "message";

    /** 事件名：整体执行完成 */
    public static final String EVENT_COMPLETED = "completed";

    /** 事件名：校验失败 / 执行异常 */
    public static final String EVENT_ERROR = "error";

    /** 事件名：流终止（必发，最后一条） */
    public static final String EVENT_DONE = "done";

    /** 心跳间隔（毫秒） */
    private static final long HEARTBEAT_INTERVAL_MS = 20_000L;

    /** 执行会话 id → 推送通道 */
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 心跳调度：单守护线程，容器关闭时回收 */
    private final ScheduledExecutorService heartbeatExecutor =
            Executors.newSingleThreadScheduledExecutor(newHeartbeatThreadFactory());

    public WorkflowStreamRegistry() {
        heartbeatExecutor.scheduleWithFixedDelay(this::sendHeartbeats,
                HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * 注册本次执行的推送通道，并挂上断开回调（注销通道，不影响图执行）。
     *
     * @param executeId 执行会话 id
     * @param emitter   已创建的 SSE 通道（调用方负责设置超时策略）
     */
    public void register(String executeId, SseEmitter emitter) {
        SseEmitter previous = emitters.put(executeId, emitter);
        if (previous != null && previous != emitter) {
            log.warn("[register][同一会话重复注册推送通道，关闭旧通道] executeId={}", executeId);
            previous.complete();
        }
        emitter.onCompletion(() -> {
            emitters.remove(executeId, emitter);
            log.info("[onCompletion][推送通道已关闭] executeId={}", executeId);
        });
        emitter.onTimeout(() -> {
            emitters.remove(executeId, emitter);
            log.info("[onTimeout][推送通道超时关闭，执行不受影响] executeId={}", executeId);
        });
        emitter.onError(error -> {
            emitters.remove(executeId, emitter);
            log.info("[onError][推送通道异常关闭，执行不受影响] executeId={}, error={}",
                    executeId, error == null ? null : error.getMessage());
        });
        log.info("[register][已注册推送通道] executeId={}", executeId);
    }

    /** 指定会话当前是否仍有可用推送通道（调用方已断开时为 false） */
    public boolean isActive(String executeId) {
        return executeId != null && emitters.containsKey(executeId);
    }

    /**
     * 推一条命名事件。
     *
     * <p>通道不存在（调用方已断开）时**静默忽略**：执行照常进行，结果由轮询接口兜底取回。
     *
     * @param executeId 执行会话 id
     * @param event     事件名，取 {@link #EVENT_CREATED} 等常量
     * @param data      事件负载（序列化为 JSON；{@code null} 时推空对象）
     */
    public void send(String executeId, String event, Object data) {
        SseEmitter emitter = emitters.get(executeId);
        if (emitter == null) {
            return;
        }
        String payload;
        try {
            payload = objectMapper.writeValueAsString(data == null ? Map.of() : data);
        } catch (Exception e) {
            log.warn("[send][事件负载序列化失败] executeId={}, event={}, error={}", executeId, event, e.getMessage());
            return;
        }
        try {
            // 负载已是 JSON 文本，按纯文本发送，避免 String 被二次加引号
            emitter.send(SseEmitter.event().name(event).data(payload, MediaType.TEXT_PLAIN));
        } catch (Exception e) {
            emitters.remove(executeId, emitter);
            log.info("[send][推送失败，注销通道（执行不受影响）] executeId={}, event={}, error={}",
                    executeId, event, e.getMessage());
        }
    }

    /**
     * 一次性错误流：提交阶段校验失败时返回。
     *
     * <p>推 {@code error}（负载即错误码与提示）+ {@code done} 后立即结束，让调用方**只需处理一种协议**，
     * 无需区分「HTTP 层错误」与「流内错误」。
     */
    public SseEmitter failedStream(Object errorPayload) {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            emitter.send(SseEmitter.event().name(EVENT_ERROR)
                    .data(objectMapper.writeValueAsString(errorPayload), MediaType.TEXT_PLAIN));
            emitter.send(SseEmitter.event().name(EVENT_DONE).data("{}", MediaType.TEXT_PLAIN));
            emitter.complete();
        } catch (Exception e) {
            emitter.complete();
        }
        return emitter;
    }

    /**
     * 一次性确认流：恢复（提交确认清单）请求的响应。
     *
     * <p>恢复后的执行事件继续推回**原通道**，本响应只作「已受理」应答——推一条 {@code done}
     * 后立即结束，调用方无需读取其内容。
     */
    public SseEmitter ackStream() {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            emitter.send(SseEmitter.event().name(EVENT_DONE)
                    .data("{\"accepted\":true}", MediaType.TEXT_PLAIN));
            emitter.complete();
        } catch (Exception e) {
            emitter.complete();
        }
        return emitter;
    }

    /**
     * 结束流：补发 {@link #EVENT_DONE} 后关闭通道。
     *
     * <p>无论成功、失败还是调用方已断开，都应调用一次，保证调用方能收到流终止信号。
     */
    public void complete(String executeId) {
        SseEmitter emitter = emitters.remove(executeId);
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(EVENT_DONE).data("{}", MediaType.TEXT_PLAIN));
            emitter.complete();
            log.info("[complete][推送通道已正常结束] executeId={}", executeId);
        } catch (Exception e) {
            log.info("[complete][推送通道结束失败（调用方可能已断开）] executeId={}, error={}",
                    executeId, e.getMessage());
        }
    }

    /** 心跳：向所有在用通道发注释帧，失败即注销 */
    private void sendHeartbeats() {
        Collection<Map.Entry<String, SseEmitter>> snapshot = emitters.entrySet();
        for (Map.Entry<String, SseEmitter> entry : snapshot) {
            SseEmitter emitter = entry.getValue();
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (Exception e) {
                emitters.remove(entry.getKey(), emitter);
                log.info("[sendHeartbeats][心跳失败，注销通道] executeId={}, error={}",
                        entry.getKey(), e.getMessage());
            }
        }
    }

    private static ThreadFactory newHeartbeatThreadFactory() {
        AtomicInteger seq = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, "tb-workflow-sse-heartbeat-" + seq.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }

    @PreDestroy
    public void shutdown() {
        heartbeatExecutor.shutdownNow();
        for (Map.Entry<String, SseEmitter> entry : emitters.entrySet()) {
            try {
                entry.getValue().complete();
            } catch (Exception ignored) {
                // 容器关闭时忽略
            }
        }
        emitters.clear();
    }

}
