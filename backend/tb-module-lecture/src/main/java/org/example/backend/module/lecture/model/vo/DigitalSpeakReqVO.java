package org.example.backend.module.lecture.model.vo;

import lombok.Data;

/**
 * 数字人播报请求（前端 `/index/ppt` 的放映页在老师点「开始演讲」时发出）。
 *
 * <p>它不再是「会议接口」：音频是**流式回给调用方浏览器**的（SSE），由那一页播放，而那一页正被屏幕共享，
 * 声音因此随共享轨一起到学生端（见 {@code docs/16} §5.7）。所以会中播报与彩排试听是同一条路，
 * {@code meetingNo} / {@code hostToken} 都可空——带齐了才把这段播报以数字人身份落进会议消息表。
 */
@Data
public class DigitalSpeakReqVO {

    /** 要播报的文本（讲稿正文） */
    private String text;

    /**
     * 音色编码，可空：为空时用全局默认音色。
     *
     * <p>内置音色所有人可用；复刻音色只有创建者可用（公开的 speak 接口可能没有登录用户，
     * 这种情况会回退默认音色）。
     */
    private String voiceCode;

    /** 会议号，可空：为空即彩排试听，不落库 */
    private String meetingNo;

    /** 主持人凭据，可空：与 {@code meetingNo} 一起给出时才落库 */
    private String hostToken;

}
