package org.example.backend.module.lecture.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模拟会议参会人 DO（表 lecture_meeting_attendee）
 * <p>学生点入会链接并输入名字后落一行，同时签发 {@code attendeeToken}。
 */
@Data
public class LectureMeetingAttendeeDO {

    /** 主键（自增） */
    private Long id;
    /** 会议 id（逻辑外键 lecture_meeting.id） */
    private Long meetingId;
    /** 参会昵称（同一会议内重名自动加后缀） */
    private String nickname;
    /** 参会凭据（会议内身份） */
    private String attendeeToken;
    /** 角色：1=老师，2=学生 */
    private Integer role;
    /** 是否在线 */
    private Boolean online;
    /** 入会时间 */
    private LocalDateTime joinTime;
    /** 离会时间 */
    private LocalDateTime leaveTime;
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
