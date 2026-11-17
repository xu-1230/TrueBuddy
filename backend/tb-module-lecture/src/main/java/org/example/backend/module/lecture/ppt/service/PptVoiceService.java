package org.example.backend.module.lecture.ppt.service;

import org.example.backend.module.lecture.ppt.model.vo.AvatarVoiceListRespVO;
import org.example.backend.module.lecture.ppt.model.vo.AvatarVoiceVO;
import org.example.backend.module.lecture.ppt.model.vo.VoiceCloneCommand;

/**
 * 音色库：列表、复刻、删除，以及播报时的音色解析。
 */
public interface PptVoiceService {

    /** 当前用户可见的音色（内置 + 自己复刻）与能力开关 */
    AvatarVoiceListRespVO list(Long userId);

    /** 复刻音色；成功返回新音色（含试听地址） */
    AvatarVoiceVO clone(Long userId, VoiceCloneCommand command);

    /** 删除自己的复刻音色（软删 + 尽力删云端 + 清空引用它的偏好） */
    void delete(Long userId, Long id);

    /**
     * 解析播报要用的音色。
     *
     * <p>请求音色为空、不存在、无权限或不是自己的复刻音色时回退全局默认音色；
     * 回退原因写进结果，供 SSE 状态告诉前端。
     *
     * @param requestedVoiceCode 请求指定的音色编码（可空）
     * @param userId             当前登录用户 id（/ppt/speech/speak 公开，可能为空）
     * @return 实际使用的音色、模型与回退标记
     */
    VoiceSelection resolveForSpeak(String requestedVoiceCode, Long userId);

    /**
     * 播报音色解析结果。
     *
     * @param voiceCode     实际使用的音色编码（非空）
     * @param model         实际使用的 TTS 模型（非空）
     * @param fallback      是否从请求音色回退到了默认音色
     * @param fallbackReason 回退原因：not_found / forbidden / null
     */
    record VoiceSelection(String voiceCode, String model, boolean fallback, String fallbackReason) {
    }

}
