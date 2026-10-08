package org.example.backend.module.lecture.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模拟会议消息 DO（表 lecture_meeting_message）
 */
@Data
public class LectureMeetingMessageDO {

    /** 主键（自增） */
    private Long id;
    /** 会议 id（逻辑外键 lecture_meeting.id） */
    private Long meetingId;
    /** 发送人 id（逻辑外键 lecture_meeting_attendee.id） */
    private Long attendeeId;
    /** 发送人昵称（冗余存储） */
    private String nickname;
    /** 发送人角色：1=老师，2=学生 */
    private Integer role;
    /** 消息正文 */
    private String content;
    /** 消息类型：1=文字（2/3 预留给语音转写与数字人发言） */
    private Integer kind;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
    /** 创建人 id */
    private Long creator;
    /** 更新人 id */
    private Long updater;
    /** 是否删除：false=否，true=是 */
    private Boolean deleted;

}
