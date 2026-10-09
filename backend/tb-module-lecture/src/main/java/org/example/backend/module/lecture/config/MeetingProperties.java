package org.example.backend.module.lecture.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 模拟会议配置（前缀 {@code tb.lecture.meeting}）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "tb.lecture.meeting")
public class MeetingProperties {

    /**
     * 入会链接的基地址（如 {@code http://192.168.1.5:5173}）。
     * <p>留空时自动探测本机局域网 IPv4 并拼接前端端口，避免学生手机打不开 localhost。
     */
    private String publicBaseUrl = "";

    /**
     * 探测局域网 IP 时拼接的前端端口（Vite 开发服务器默认 5173）。
     */
    private Integer frontendPort = 5173;

    /**
     * 入会与详情接口返回的历史消息条数上限。
     */
    private Integer historyLimit = 50;

    /**
     * 昵称最大长度。
     */
    private Integer nicknameMaxLength = 20;

    // ========== LiveKit 实时通道 ==========

    /**
     * LiveKit 信令地址（如 {@code ws://192.168.43.1:7880}）。
     * <p>留空时按「ws://{本机局域网 IPv4}:{livekitPort}」推导 —— 与 {@link #publicBaseUrl} 同一套探测逻辑，
     * 因为答辩现场走手机热点，IP 每次开会都可能变。
     */
    private String livekitUrl = "";

    /**
     * 推导 LiveKit 地址时使用的信令端口。
     */
    private Integer livekitPort = 7880;

    /**
     * LiveKit API Key（必须与 {@code D:\ctf\livekit\.env} 的 {@code LIVEKIT_API_KEY} 一致）。
     */
    private String livekitApiKey = "";

    /**
     * LiveKit API Secret（同上，与 {@code LIVEKIT_API_SECRET} 一致）。
     */
    private String livekitApiSecret = "";

    /**
     * 访问令牌有效期（小时）。
     */
    private Integer livekitTokenTtlHours = 6;

    /**
     * 单条消息长度上限（与前端 DataChannel 发出的正文长度口径一致）。
     */
    private Integer messageMaxLength = 500;

}
