package org.example.backend.module.lecture.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 数字人配置（前缀 {@code tb.lecture.digital-human}）。
 *
 * <p>与 {@link MeetingProperties} 分开放：会议是本期已落地的能力，数字人是后续二（见
 * {@code docs/17-AI数字人语音链路方案.md}），两者的开关与配额互不相干。
 */
@Data
@Component
@ConfigurationProperties(prefix = "tb.lecture.digital-human")
public class DigitalHumanProperties {

    /**
     * 总开关。关闭时所有 {@code /meeting/digital/*} 接口直接返回「未启用」，不碰 worker 也不碰 DashScope。
     */
    private Boolean enabled = false;

    /**
     * Node worker 的基地址。只应指向环回地址 —— worker 会把声音灌进直播间，不能给同网段任何人驱动。
     */
    private String workerBaseUrl = "http://127.0.0.1:9090";

    /**
     * 与 worker 约定的内部口令（请求头 {@code X-Agent-Token}）。留空时不发送该头，worker 侧应拒绝服务。
     */
    private String agentSharedSecret = "";

    /**
     * DashScope API Key（CosyVoice 走原生 WebSocket，不吃 OpenAI 兼容层，因此与 embedding 那份配置分开）。
     */
    private String dashscopeApiKey = "";

    /**
     * TTS 模型。
     */
    private String ttsModel = "cosyvoice-v3-flash";

    /**
     * TTS 音色。
     */
    private String ttsVoice = "longanyang";

    /**
     * 同时进行的播报条数上限，决定后端合成线程池大小。
     */
    private Integer maxConcurrentSay = 2;

    /**
     * 单条播报的文本长度上限。比聊天消息长，因为一页讲稿常常就超过 500 字。
     */
    private Integer maxTextLength = 1000;

    /**
     * 探测 worker 健康状态的超时（毫秒）。状态接口是前端轮询的，不能慢。
     */
    private Integer workerProbeTimeoutMs = 1500;

}
