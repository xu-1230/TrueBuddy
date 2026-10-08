package org.example.backend.module.lecture.model.vo;

import lombok.Data;

/**
 * 消息落库请求 VO。
 *
 * <p>聊天实时广播走 LiveKit 的 DataChannel（不经后端），本接口**只负责把消息写进历史**，
 * 因此发送者身份由凭据解析：老师用 {@code hostToken}，学生用 {@code attendeeToken}。
 */
@Data
public class MeetingMessageReqVO {

    /** 会议号 */
    private String meetingNo;
    /** 学生参会凭据（学生发送时必填） */
    private String attendeeToken;
    /** 主持人凭据（老师发送时必填） */
    private String hostToken;
    /** 消息正文 */
    private String content;

}
