package org.example.backend.module.lecture.model.vo;

import lombok.Data;

/**
 * 学生入会请求 VO
 */
@Data
public class MeetingJoinReqVO {

    /** 会议号（6 位数字） */
    private String meetingNo;
    /** 参会昵称（入会弹窗输入的名字） */
    private String nickname;

}
