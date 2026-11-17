package org.example.backend.module.lecture.ppt.model.vo;

import lombok.Data;

/**
 * 音色列表项（设置弹窗「声音」Tab 用）。
 */
@Data
public class AvatarVoiceVO {

    /** 主键（删除接口用） */
    private Long id;
    /** 音色编码（播报接口用） */
    private String voiceCode;
    /** 音色名称 */
    private String name;
    /** 特征描述 */
    private String trait;
    /** 适用年龄段 */
    private String age;
    /** 支持语言 */
    private String language;
    /** 分类 */
    private String category;
    /** 性别：male / female */
    private String gender;
    /** 来源：system=内置，cloned=复刻 */
    private String source;
    /** 试听地址；没有可用地址时为空 */
    private String previewUrl;
    /** 当前登录用户是否可以删除（仅自己的复刻音色） */
    private Boolean canDelete;

}
