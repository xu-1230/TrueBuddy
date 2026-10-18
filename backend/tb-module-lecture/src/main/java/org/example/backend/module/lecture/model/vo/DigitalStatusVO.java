package org.example.backend.module.lecture.model.vo;

import lombok.Data;

/**
 * 数字人可用性状态：前端据此显示「语音不可用」，而不是点了播报却静默无声。
 *
 * <p>worker 已删（见 {@code docs/16} §5.7），因此不再有「进程可达性」与「房间/身份」这类字段：
 * 合成在本进程内完成，能不能出声只取决于开关与 DashScope 配置。
 */
@Data
public class DigitalStatusVO {

    /** 配置总开关 */
    private Boolean enabled;

    /** 开关打开且合成配置齐备（不探测任何进程） */
    private Boolean available;

}
