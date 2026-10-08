package org.example.backend.module.lecture.model.vo;

import lombok.Data;

/**
 * 创建会议响应 VO
 */
@Data
public class MeetingCreateRespVO {

    /** 会议 id */
    private Long meetingId;
    /** 会议号（6 位数字） */
    private String meetingNo;
    /** 学生入会链接 */
    private String joinUrl;
    /** 主持人凭据：仅创建时返回一次，控制台操作需携带 */
    private String hostToken;

}
