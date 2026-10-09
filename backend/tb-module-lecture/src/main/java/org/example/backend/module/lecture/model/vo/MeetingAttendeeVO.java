package org.example.backend.module.lecture.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 参会人 VO（不含参会凭据）
 *
 * <p>这是<b>累计入会台账</b>的一行，不是在线成员：在线名单只由前端从 LiveKit 房间事件得出。
 */
@Data
public class MeetingAttendeeVO {

    /** 参会人 id */
    private Long id;
    /** 参会昵称 */
    private String nickname;
    /** 角色：1=老师，2=学生 */
    private Integer role;
    /** 入会时间 */
    private LocalDateTime joinTime;
    /** 离会时间（null = 尚未显式离开，≠ 在线） */
    private LocalDateTime leaveTime;

}
