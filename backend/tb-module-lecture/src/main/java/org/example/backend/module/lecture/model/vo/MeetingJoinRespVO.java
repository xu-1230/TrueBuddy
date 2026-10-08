package org.example.backend.module.lecture.model.vo;

import lombok.Data;

import java.util.List;

/**
 * 学生入会响应 VO
 */
@Data
public class MeetingJoinRespVO {

    /** 参会人 id */
    private Long attendeeId;
    /** 参会凭据：会议内身份，WebSocket 握手与上行消息携带 */
    private String attendeeToken;
    /** 会议信息 */
    private MeetingInfoVO meeting;
    /** 参会人列表 */
    private List<MeetingAttendeeVO> attendees;
    /** 最近消息（按时间正序） */
    private List<MeetingMessageVO> messages;
    /** 实时通道接入信息（LiveKit 地址与参会令牌） */
    private MeetingRealtimeVO realtime;

}
