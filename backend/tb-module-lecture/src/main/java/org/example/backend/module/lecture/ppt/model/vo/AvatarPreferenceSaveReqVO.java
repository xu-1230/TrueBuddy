package org.example.backend.module.lecture.ppt.model.vo;

import lombok.Data;

/**
 * 保存数字人与音色偏好的请求；两个字段都可为空，表示回退到默认。
 */
@Data
public class AvatarPreferenceSaveReqVO {

    /** 形象编码；为空表示默认形象 */
    private String avatarModelCode;
    /** 音色编码；为空表示默认音色 */
    private String voiceCode;

}
