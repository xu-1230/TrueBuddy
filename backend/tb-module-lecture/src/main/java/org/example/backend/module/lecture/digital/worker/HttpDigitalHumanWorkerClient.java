package org.example.backend.module.lecture.digital.worker;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.module.lecture.config.DigitalHumanProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * worker 的本机 HTTP 客户端。
 *
 * <p>用 JDK {@code HttpClient} 而不是模块里已有的 Hutool：{@code /say} 的请求体是<b>边合成边写</b>的
 * chunked 裸流，Hutool 会先把请求体整体缓冲进内存，拿不到流式效果（见 {@code docs/17} §5.2）。
 */
@Slf4j
@Component
public class HttpDigitalHumanWorkerClient implements DigitalHumanWorkerClient {

    /** PCM 固定参数：与 CosyVoiceTtsSynth 的输出格式严格成对 */
    private static final int SAMPLE_RATE = 48000;
    private static final int CHANNELS = 1;
    /** 建会话与关会话的超时（会阻塞接口，不能长） */
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(5);
    /** 一次播报允许的最长时间：TTS 本身最长 60s，留一点余量 */
    private static final Duration SAY_TIMEOUT = Duration.ofSeconds(90);

    private final DigitalHumanProperties properties;
    private final HttpClient httpClient;

    public HttpDigitalHumanWorkerClient(DigitalHumanProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(CALL_TIMEOUT)
                .build();
    }

    @Override
    public boolean healthy() {
        var request = baseRequest("/healthz").GET().timeout(Duration.ofMillis(properties.getWorkerProbeTimeoutMs())).build();
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (IOException e) {
            log.debug("[healthy][worker 不可达: {}]", e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public void openSession(String room, String livekitUrl, String agentToken) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("room", room);
        body.put("url", livekitUrl);
        body.put("token", agentToken);
        var request = baseRequest("/sessions")
                .POST(HttpRequest.BodyPublishers.ofString(JSONUtil.toJsonStr(body), StandardCharsets.UTF_8))
                .timeout(CALL_TIMEOUT)
                .build();
        sendAndRequire(request, "打开数字人会话");
    }

    @Override
    public void closeSession(String room) {
        var request = baseRequest("/sessions/" + room).DELETE().timeout(CALL_TIMEOUT).build();
        try {
            int status = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).statusCode();
            log.info("[closeSession][room={} 会话已关闭 status={}]", room, status);
        } catch (IOException e) {
            log.warn("[closeSession][关闭数字人会话失败 room={}: {}]", room, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void streamPcm(String room, String requestId, InputStream pcm) {
        String path = "/say?requestId=" + requestId + "&room=" + room
                + "&sampleRate=" + SAMPLE_RATE + "&channels=" + CHANNELS;
        // ofInputStream 的 Supplier 在连接重试时会被再次调用，而管道流只能读一次；
        // 这里成立的前提是本客户端不发任何会触发重试的配置（默认也不重试请求体）
        var request = baseRequest(path)
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> pcm))
                .timeout(SAY_TIMEOUT)
                .build();
        sendAndRequire(request, "推送播报音频");
    }

    @Override
    public boolean cancelSay(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return false;
        }
        var request = baseRequest("/say/" + requestId).DELETE().timeout(CALL_TIMEOUT).build();
        try {
            int status = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).statusCode();
            log.info("[cancelSay][requestId={} 已请求取消 status={}]", requestId, status);
            return status / 100 == 2;
        } catch (IOException e) {
            log.warn("[cancelSay][取消请求失败 requestId={}: {}]", requestId, e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private HttpRequest.Builder baseRequest(String path) {
        String base = properties.getWorkerBaseUrl();
        if (base == null || base.isBlank()) {
            throw new IllegalStateException("数字人 worker 地址未配置");
        }
        var builder = HttpRequest.newBuilder(URI.create(trimTrailingSlash(base) + path));
        String secret = properties.getAgentSharedSecret();
        if (secret != null && !secret.isBlank()) {
            builder.header("X-Agent-Token", secret);
        }
        return builder;
    }

    /** 要求 2xx，否则抛带 worker 响应正文的异常 */
    private void sendAndRequire(HttpRequest request, String action) {
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(action + "失败：worker 不可达（" + e.getMessage() + "）", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(action + "被中断", e);
        }
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException(action + "失败：worker 返回 " + response.statusCode()
                    + " " + truncate(response.body()));
        }
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 200 ? text : text.substring(0, 200) + "…";
    }
}
