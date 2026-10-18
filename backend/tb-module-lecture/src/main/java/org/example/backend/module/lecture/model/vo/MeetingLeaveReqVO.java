package org.example.backend.module.lecture.model.vo;

import lombok.Data;

/**
 * 学生离会请求 VO
 *
 * <p>由学生端在 SPA 内离开会议室路由时（组件卸载路径，用 {@code sendBeacon} 发）调用，
 * 只负责给参会台账补记离会时间。<b>刷新页面不会调用</b>——刷新后凭据继续复用、不会重新 join，
 * 那时写离会等于留下一条假事实（见 CONTEXT.md「尚未显式离开」）。
 *
 * <p>它<b>不是</b>「在线」的数据来源：在线与否只由 LiveKit 房间事件得出（docs/adr/0002）。
 */
@Data
public class MeetingLeaveReqVO {

    /** 会议号（6 位数字） */
    private String meetingNo;
    /** 参会凭据（入会时下发） */
    private String attendeeToken;

}
