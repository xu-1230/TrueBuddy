package org.example.backend.module.lecture.model.vo;

import lombok.Data;

/**
 * 数字人播报结果。
 */
@Data
public class DigitalSayRespVO {

    /** 本次播报的幂等标识（worker 侧同 id 不重复播） */
    private String requestId;

    /** 首个音频块到达耗时（毫秒）：端到端延迟的主要构成 */
    private Long firstChunkMs;

    /** 合成出的 PCM 字节数 */
    private Long bytes;

    /** 播报音频时长（毫秒） */
    private Long durationMs;

    /** 落库后的消息（数字人发言，{@code kind=2}） */
    private MeetingMessageVO message;

}
