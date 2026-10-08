package org.example.backend.module.lecture.model.vo;

import lombok.Data;

/**
 * 结束会议请求 VO
 */
@Data
public class MeetingCloseReqVO {

    /** 会议号（6 位数字） */
    private String meetingNo;
    /** 主持人凭据 */
    private String hostToken;

}
