package org.example.backend.module.lecture.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会议信息 VO（不含主持人凭据）
 */
@Data
public class MeetingInfoVO {

    /** 会议 id */
    private Long id;
    /** 会议号（6 位数字） */
    private String meetingNo;
    /** 会议标题 */
    private String title;
    /** 会议状态：1=进行中，2=已结束 */
    private Integer status;
    /** 开始时间 */
    private LocalDateTime startTime;
    /** 结束时间 */
    private LocalDateTime endTime;
    /** 累计入会人数（台账行数，不是在线人数） */
    private Integer attendeeCount;

}
