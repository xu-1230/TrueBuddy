package org.example.backend.module.lecture.ppt.model.vo;

import lombok.Data;

/**
 * 当前用户的数字人与音色偏好；两个字段为空都表示使用默认。
 */
@Data
public class AvatarPreferenceVO {

    /** 当前选中的形象编码 */
    private String avatarModelCode;
    /** 当前选中的音色编码 */
    private String voiceCode;

}
