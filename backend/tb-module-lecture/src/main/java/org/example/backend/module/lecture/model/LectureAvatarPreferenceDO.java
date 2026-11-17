package org.example.backend.module.lecture.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 教师数字人与音色偏好 DO（表 lecture_avatar_preference）。
 *
 * <p>一个登录用户一行；两个编码为空都表示「使用默认」。
 */
@Data
public class LectureAvatarPreferenceDO {

    /** 主键（自增） */
    private Long id;
    /** 用户 id（system_user.id） */
    private Long creator;
    /** 当前选中的形象编码，空=默认形象 */
    private String avatarModelCode;
    /** 当前选中的音色编码，空=默认音色 */
    private String voiceCode;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
    /** 更新人 id */
    private Long updater;
    /** 是否删除：false=否，true=是 */
    private Boolean deleted;

}
