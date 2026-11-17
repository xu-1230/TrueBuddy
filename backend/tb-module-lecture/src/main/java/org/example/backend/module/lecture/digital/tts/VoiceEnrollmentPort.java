package org.example.backend.module.lecture.digital.tts;

/**
 * 声音复刻端口：把一段公网音频注册成 DashScope 音色。
 *
 * <p>单独抽成端口是为了让业务层可以在单测里替换掉真实网络调用；实现见
 * {@link CosyVoiceVoiceEnrollment}。
 */
public interface VoiceEnrollmentPort {

    /**
     * 创建复刻音色。
     *
     * @param targetModel 目标 TTS 模型（如 cosyvoice-v3-flash）
     * @param prefix      音色编码前缀（小写字母/数字，1~10 位，以字母开头）
     * @param audioUrl    公网可访问的音频样本地址
     * @return DashScope 返回的 voice_id（落库为 voice_code）
     */
    String createVoice(String targetModel, String prefix, String audioUrl);

    /**
     * 删除云端复刻音色（尽力而为；失败由调用方决定是否只记日志）。
     *
     * @param voiceCode DashScope 返回的 voice_id
     */
    void deleteVoice(String voiceCode);

}
