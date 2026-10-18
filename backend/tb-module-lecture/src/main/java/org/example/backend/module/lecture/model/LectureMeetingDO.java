package org.example.backend.module.lecture.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模拟会议 DO（表 lecture_meeting）
 * <p>一次「开启会议」= 一行；参会人见 {@link LectureMeetingAttendeeDO}，消息见 {@link LectureMeetingMessageDO}。
 */
@Data
public class LectureMeetingDO {

    /** 主键（自增） */
    private Long id;
    /** 会议号（6 位数字） */
    private String meetingNo;
    /** 会议标题 */
    private String title;
    /** 主持人凭据（仅创建时返回） */
    private String hostToken;
    /** 会议状态：1=进行中，2=已结束 */
    private Integer status;
    /** 开始时间 */
    private LocalDateTime startTime;
    /** 结束时间 */
    private LocalDateTime endTime;
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
