package org.example.backend.module.lecture.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 数字人配置（前缀 {@code tb.lecture.digital-human}）。
 *
 * <p>与 {@link MeetingProperties} 分开放：会议是本期已落地的能力，数字人播报是后续二（见
 * {@code docs/17-AI数字人语音链路方案.md}），两者的开关与配额互不相干。
 *
 * <p>worker 相关配置项（基地址 / 内部口令 / 探活超时）已随 worker 一起删除：合成结果现在直接以 SSE
 * 回给放映页浏览器（见 {@code docs/16} §5.7）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "tb.lecture.digital-human")
public class DigitalHumanProperties {

    /**
     * 总开关。关闭时播报接口直接返回「未启用」。
     */
    private Boolean enabled = false;

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
     * 同时进行的播报条数上限，决定后端合成线程池大小。超出的播报会排队（音要一段一段说）。
     */
    private Integer maxConcurrentSpeak = 2;

    /**
     * 单条播报的文本长度上限。比聊天消息长，因为一页讲稿常常就超过 500 字。
     */
    private Integer maxTextLength = 1000;

    /**
     * 公网可达的存储地址（可选），用于声音复刻时让 DashScope 拉取上传的音频样本。
     *
     * <p>本项目的 MinIO 默认是 `http://localhost:9000`，阿里云侧无法访问；只有把
     * MinIO（或反向代理）暴露到公网、并允许匿名读取对应对象时，上传复刻才可用。
     * 未配置时设置弹窗会禁用「上传音频」，只保留「音频链接」。
     *
     * <p>形如 `https://static.example.com`；拼出的地址是 `{publicBaseUrl}/{bucket}/{objectName}`。
     */
    private String publicBaseUrl = "";

}
