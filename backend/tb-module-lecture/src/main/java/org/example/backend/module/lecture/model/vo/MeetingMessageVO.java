package org.example.backend.module.lecture.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会议消息 VO
 */
@Data
public class MeetingMessageVO {

    /** 消息 id */
    private Long id;
    /** 发送人 id */
    private Long attendeeId;
    /** 发送人昵称 */
    private String nickname;
    /** 发送人角色：1=老师，2=学生 */
    private Integer role;
    /** 消息正文 */
    private String content;
    /** 消息类型：1=文字 */
    private Integer kind;
    /** 创建时间 */
    private LocalDateTime createTime;

}
