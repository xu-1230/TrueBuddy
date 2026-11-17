package org.example.backend.module.lecture.digital.tts;

import com.alibaba.dashscope.audio.ttsv2.enrollment.Voice;
import com.alibaba.dashscope.audio.ttsv2.enrollment.VoiceEnrollmentService;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.module.lecture.config.DigitalHumanProperties;
import org.springframework.stereotype.Component;

/**
 * DashScope 声音复刻（CosyVoice 音色注册）。
 *
 * <p>注意：{@code createVoice} 只接受**公网可访问的音频 URL**（wav/mp3，10~60 秒，≤10MB），
 * 不会接收本地文件或字节流；因此上传路径必须先落到公网可达的存储上（见
 * {@link DigitalHumanProperties#getPublicBaseUrl()}）。
 */
@Slf4j
@Component
public class CosyVoiceVoiceEnrollment implements VoiceEnrollmentPort {

    private final DigitalHumanProperties properties;

    public CosyVoiceVoiceEnrollment(DigitalHumanProperties properties) {
        this.properties = properties;
    }

    @Override
    public String createVoice(String targetModel, String prefix, String audioUrl) {
        String apiKey = requireApiKey();
        VoiceEnrollmentService service = new VoiceEnrollmentService(apiKey);
        try {
            Voice voice = service.createVoice(targetModel, prefix, audioUrl);
            String voiceId = voice == null ? null : voice.getVoiceId();
            if (voiceId == null || voiceId.isBlank()) {
                throw new IllegalStateException("声音复刻没有返回 voice_id");
            }
            log.info("[voice-enroll][创建成功 model={}, prefix={}, voiceId={}, requestId={}]",
                    targetModel, prefix, voiceId, service.getLastRequestId());
            return voiceId;
        } catch (Exception e) {
            log.warn("[voice-enroll][创建失败 model={}, prefix={}, url={}]: {}",
                    targetModel, prefix, audioUrl, e.getMessage());
            throw new IllegalStateException(rootMessage(e), e);
        }
    }

    @Override
    public void deleteVoice(String voiceCode) {
        String apiKey = requireApiKey();
        VoiceEnrollmentService service = new VoiceEnrollmentService(apiKey);
        try {
            service.deleteVoice(voiceCode);
            log.info("[voice-enroll][云端删除成功 voiceId={}, requestId={}]", voiceCode, service.getLastRequestId());
        } catch (Exception e) {
            // 云端删除是尽力而为：本地软删已经完成，失败只记日志
            log.warn("[voice-enroll][云端删除失败 voiceId={}]: {}", voiceCode, e.getMessage());
            throw new IllegalStateException(rootMessage(e), e);
        }
    }

    private String requireApiKey() {
        String apiKey = properties.getDashscopeApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("数字人语音未配置 DashScope API Key");
        }
        return apiKey;
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

}
