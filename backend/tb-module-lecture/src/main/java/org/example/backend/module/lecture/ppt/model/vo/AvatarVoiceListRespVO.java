package org.example.backend.module.lecture.ppt.model.vo;

import lombok.Data;

import java.util.List;

/**
 * 音色列表响应：列表本身 + 弹窗需要的三个能力开关。
 */
@Data
public class AvatarVoiceListRespVO {

    /** 当前用户可见的音色（内置 + 自己复刻） */
    private List<AvatarVoiceVO> items;
    /** 全局默认音色编码（未选音色时播报使用） */
    private String defaultVoiceCode;
    /** 是否允许复刻（数字人已启用且 DashScope Key 已配置） */
    private Boolean cloneEnabled;
    /** 是否允许上传音频复刻（配置了公网可达的存储地址） */
    private Boolean uploadEnabled;

}
