package org.example.backend.module.lecture.model.vo;

import lombok.Data;

/**
 * 取消播报请求（老师侧动作，用 {@code hostToken} 鉴权）。
 *
 * <p>{@code requestId} 是上一次 {@code digital/say} 响应里返回的那个，前端必须持有它才能掐断正在播的这段。
 */
@Data
public class DigitalCancelReqVO {

    /** 会议号 */
    private String meetingNo;

    /** 主持人凭据 */
    private String hostToken;

    /** 要取消的播报标识（来自 {@code DigitalSayRespVO.requestId}） */
    private String requestId;

}
