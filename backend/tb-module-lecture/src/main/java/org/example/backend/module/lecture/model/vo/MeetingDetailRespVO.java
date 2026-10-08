package org.example.backend.module.lecture.model.vo;

import lombok.Data;

import java.util.List;

/**
 * 会议详情响应 VO（老师控制台用）
 */
@Data
public class MeetingDetailRespVO {

    /** 会议信息 */
    private MeetingInfoVO meeting;
    /** 参会人列表 */
    private List<MeetingAttendeeVO> attendees;
    /** 最近消息（按时间正序） */
    private List<MeetingMessageVO> messages;
    /**
     * 实时通道接入信息。
     * <p>老师端拿到的是可发布令牌（能共享屏幕），学生端（{@code /meeting/state}）拿到的是只读令牌；
     * LiveKit 未配置时为 {@code null}，此时控制台仍可查看历史，但不能开会。
     */
    private MeetingRealtimeVO realtime;

}
