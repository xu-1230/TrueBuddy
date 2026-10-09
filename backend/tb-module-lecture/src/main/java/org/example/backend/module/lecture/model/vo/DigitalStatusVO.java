package org.example.backend.module.lecture.model.vo;

import lombok.Data;

/**
 * 数字人可用性状态：前端据此显示「数字人不可用」，而不是点了播报却静默无声。
 */
@Data
public class DigitalStatusVO {

    /** 配置总开关 */
    private Boolean enabled;

    /** worker 进程是否可达（{@code /healthz} 200） */
    private Boolean workerReachable;

    /** 两者都满足才可用 */
    private Boolean available;

    /** 数字人所在房间名（{@code meeting-{meetingNo}}） */
    private String room;

    /** 数字人在房间里的身份标识，前端按它识别哪条音频轨属于数字人 */
    private String identity;

}
