package org.example.backend.module.lecture.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 参会人 VO（不含参会凭据）
 */
@Data
public class MeetingAttendeeVO {

    /** 参会人 id */
    private Long id;
    /** 参会昵称 */
    private String nickname;
    /** 角色：1=老师，2=学生 */
    private Integer role;
    /** 是否在线 */
    private Boolean online;
    /** 入会时间 */
    private LocalDateTime joinTime;

}
