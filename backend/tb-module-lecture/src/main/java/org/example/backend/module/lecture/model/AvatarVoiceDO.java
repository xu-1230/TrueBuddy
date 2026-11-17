package org.example.backend.module.lecture.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 音色 DO（表 avatar_voice）。
 *
 * <p>内置音色的 {@code creator} 为空、所有登录用户可见；复刻音色的 {@code creator} 为创建者，仅本人可见。
 * {@code source} 区分来源（1=内置，2=复刻），复刻音色用 {@code ttsModel} 绑定创建时的模型。
 */
@Data
public class AvatarVoiceDO {

    /** 主键（自增） */
    private Long id;
    /** 音色编码（唯一），内置如 longxiaochun_v3，复刻为 DashScope 返回的 voice_id */
    private String voiceCode;
    /** 音色名称 */
    private String name;
    /** 特征描述 */
    private String trait;
    /** 适用年龄段 */
    private String age;
    /** 支持语言 */
    private String language;
    /** 分类；复刻音色固定为「我的复刻」 */
    private String category;
    /** 性别：male / female */
    private String gender;
    /** 来源：1=内置，2=复刻 */
    private Integer source;
    /** 复刻音色绑定的 TTS 模型；为空时使用全局配置 */
    private String ttsModel;
    /** 试听音频 URL（内置音色的官方示例） */
    private String audioPreviewUrl;
    /** 复刻样本文件 id（infra_file.id） */
    private Long sampleFileId;
    /** 复刻样本的公网音频 URL（粘贴 URL 时记录） */
    private String sampleAudioUrl;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
    /** 创建人 id（内置音色为空） */
    private Long creator;
    /** 更新人 id */
    private Long updater;
    /** 是否删除：false=否，true=是 */
    private Boolean deleted;

}
