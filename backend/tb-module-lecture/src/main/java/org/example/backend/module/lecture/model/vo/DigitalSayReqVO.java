package org.example.backend.module.lecture.model.vo;

import lombok.Data;

/**
 * 数字人播报请求（老师侧动作，用 {@code hostToken} 鉴权）。
 */
@Data
public class DigitalSayReqVO {

    /** 会议号 */
    private String meetingNo;

    /** 主持人凭据 */
    private String hostToken;

    /** 要播报的文本 */
    private String text;

}
