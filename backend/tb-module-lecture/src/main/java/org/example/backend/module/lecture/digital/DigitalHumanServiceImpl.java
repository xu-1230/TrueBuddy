package org.example.backend.module.lecture.digital;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.framework.security.LoginUser;
import org.example.backend.framework.security.util.SecurityFrameworkUtils;
import org.example.backend.module.lecture.config.DigitalHumanProperties;
import org.example.backend.module.lecture.digital.tts.TtsSynthPort;
import org.example.backend.module.lecture.model.LectureMeetingDO;
import org.example.backend.module.lecture.model.vo.DigitalSpeakReqVO;
import org.example.backend.module.lecture.model.vo.DigitalStatusVO;
import org.example.backend.module.lecture.ppt.service.PptVoiceService;
import org.example.backend.module.lecture.service.MeetingService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.example.backend.framework.common.exception.util.ServiceExceptionUtil.exception;
import static org.example.backend.module.lecture.enums.ErrorCodeConstants.DIGITAL_DISABLED;
import static org.example.backend.module.lecture.enums.ErrorCodeConstants.DIGITAL_TEXT_EMPTY;
import static org.example.backend.module.lecture.enums.ErrorCodeConstants.DIGITAL_TEXT_TOO_LONG;

/**
 * 播报的编排者：合成在本地线程里跑，PCM 一边产出就一边以 SSE 推给**发起请求的那个浏览器**。
 *
 * <p>为什么不再中继给 worker 进程：声音的终点本来就是老师放映的那一页——它正被屏幕共享，
 * 声音随共享轨到学生端。中继一圈只会多一个进程、多一份要同步的配置、多一条会挂的链路（见 docs/16 §5.7）。
 *
 * <p>「停止」因此变成免费能力：浏览器一断，推事件就会失败，{@link TtsSynthPort} 的实现据此取消合成，
 * 不再继续向厂商要音频。旧实现必须额外做一次 {@code DELETE /say/{id}} 才停得下来。
 */
@Slf4j
@Service
public class DigitalHumanServiceImpl implements DigitalHumanService {

    /** 数字人角色（1=老师，2=学生，3=数字人；与消息类型 kind=2 配套） */
    private static final int ROLE_AGENT = 3;
    /** 消息类型：数字人播报 */
    private static final int KIND_DIGITAL = 2;
    /** 数字人发言时的昵称 */
    private static final String AGENT_NICKNAME = "数字人";
    /** 音频格式：与 {@code CosyVoiceTtsSynth} 的出口口径一致，前端播放器按它建 AudioContext */
    private static final int PCM_SAMPLE_RATE = 48_000;
    private static final int PCM_CHANNELS = 1;
    private static final String PCM_ENCODING = "pcm_s16le";

    private final DigitalHumanProperties properties;
    private final TtsSynthPort ttsPort;
    private final MeetingService meetingService;
    private final PptVoiceService pptVoiceService;
    private final ExecutorService synthExecutor;

    public DigitalHumanServiceImpl(DigitalHumanProperties properties,
                                   TtsSynthPort ttsPort,
                                   MeetingService meetingService,
                                   PptVoiceService pptVoiceService) {
        this.properties = properties;
        this.ttsPort = ttsPort;
        this.meetingService = meetingService;
        this.pptVoiceService = pptVoiceService;
        this.synthExecutor = Executors.newFixedThreadPool(Math.max(1, properties.getMaxConcurrentSpeak()), runnable -> {
            Thread thread = new Thread(runnable, "digital-human-tts");
            thread.setDaemon(true);
            return thread;
        });
    }

    @PreDestroy
    public void shutdown() {
        synthExecutor.shutdownNow();
    }

    /**
     * 启动自检：把「能不能出声」这句话在启动日志里说清楚。
     *
     * <p>这条日志是为一次真实排查加的：`application.yml` 里明明填了 Key，`status` 却报「未配置」——
     * 因为 IDE 的 EnvFile 插件把根目录 `.env` 注入成了环境变量，而那里是**空值**；
     * Spring 的 `${VAR:默认值}` 只在 VAR 未定义时才用默认值，**空串照样顶掉默认值**。
     */
    @PostConstruct
    void logSpeechReadiness() {
        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            log.info("[digital][语音播报未启用：tb.lecture.digital-human.enabled=false]");
            return;
        }
        String apiKey = properties.getDashscopeApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[digital][未配置 DashScope Key：/ppt/speech/speak 会以 error 事件失败。"
                    + "若 yml 里已填真值，检查是否有**空值**环境变量 DASHSCOPE_API_KEY（IDE 的 EnvFile 会注入 .env）把它顶掉了]");
            return;
        }
        log.info("[digital][语音播报就绪：model={}, voice={}]",
                properties.getTtsModel(), properties.getTtsVoice());
    }

    @Override
    public DigitalStatusVO status() {
        boolean enabled = Boolean.TRUE.equals(properties.getEnabled());
        String apiKey = properties.getDashscopeApiKey();

        DigitalStatusVO vo = new DigitalStatusVO();
        vo.setEnabled(enabled);
        // 不再探测任何进程：开关打开且合成 Key 配好，就确实能出声
        vo.setAvailable(enabled && apiKey != null && !apiKey.isBlank());
        return vo;
    }

    @Override
    public SseEmitter speak(DigitalSpeakReqVO reqVO) {
        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            throw exception(DIGITAL_DISABLED);
        }
        String text = reqVO == null || reqVO.getText() == null ? "" : reqVO.getText().trim();
        if (text.isEmpty()) {
            throw exception(DIGITAL_TEXT_EMPTY);
        }
        int maxLength = properties.getMaxTextLength();
        if (text.length() > maxLength) {
            throw exception(DIGITAL_TEXT_TOO_LONG, maxLength);
        }
        // 带齐会议号与凭据才落库；凭据不合法直接抛错（静默跳过会让老师以为播报进了聊天记录）
        Long meetingId = resolveMeetingId(reqVO);
        PptVoiceService.VoiceSelection selection = resolveVoice(reqVO.getVoiceCode());

        // 0 = 不超时：一条播报最长几十秒，超时判断交给合成侧自己的上限
        SseEmitter emitter = new SseEmitter(0L);
        PcmSseWriter writer = new PcmSseWriter(emitter);
        // 先报格式再交给合成线程：老师点完按钮立刻有回音，采样率也不必写死在两个地方。
        // 同时把实际音色与回退标记告诉前端：请求的复刻音色不可用时，这里的 voiceCode 是默认音色。
        writer.status("started", Map.of(
                "sampleRate", PCM_SAMPLE_RATE,
                "channels", PCM_CHANNELS,
                "encoding", PCM_ENCODING,
                "voiceCode", emptyIfNull(selection.voiceCode()),
                "voiceFallback", selection.fallback(),
                "voiceFallbackReason", emptyIfNull(selection.fallbackReason())));
        synthExecutor.execute(() -> synthesize(emitter, writer, text, meetingId, selection));
        return emitter;
    }

    /**
     * 解析本次播报实际使用的音色。
     *
     * <p>音色表查询失败（例如 SQL 未执行 / 数据库短暂不可用）不能让公开的 speak 接口整体失败：
     * 回退到配置里的默认音色，并把原因标成 lookup_failed 告诉前端。
     */
    private PptVoiceService.VoiceSelection resolveVoice(String voiceCode) {
        try {
            return pptVoiceService.resolveForSpeak(voiceCode, currentUserId());
        } catch (RuntimeException e) {
            log.warn("[speak][音色解析失败，回退默认音色 voiceCode={}]: {}", voiceCode, e.getMessage());
            String fallbackVoice = properties.getTtsVoice() == null ? "" : properties.getTtsVoice();
            String fallbackModel = properties.getTtsModel() == null ? "" : properties.getTtsModel();
            return new PptVoiceService.VoiceSelection(fallbackVoice, fallbackModel,
                    voiceCode != null && !voiceCode.isBlank(), "lookup_failed");
        }
    }

    /** 合成 → SSE。失败一律收尾成一条 {@code error} 事件，不让连接悬着。 */
    private void synthesize(SseEmitter emitter, PcmSseWriter writer, String text, Long meetingId,
                            PptVoiceService.VoiceSelection selection) {
        try {
            TtsSynthPort.TtsStats stats = ttsPort.synthesize(text, selection.voiceCode(), selection.model(), writer);
            Map<String, Object> done = new LinkedHashMap<>();
            done.put("state", "done");
            done.put("bytes", stats.bytes());
            done.put("firstChunkMs", stats.firstChunkMs());
            done.put("durationMs", stats.durationMs());
            writer.status("done", done);
            log.info("[speak][播报完成 meetingId={}, voice={}, model={}, fallback={}, bytes={}, firstChunkMs={}, durationMs={}]",
                    meetingId, selection.voiceCode(), selection.model(), selection.fallback(),
                    stats.bytes(), stats.firstChunkMs(), stats.durationMs());
            persist(meetingId, text);
        } catch (RuntimeException e) {
            if (writer.isBroken()) {
                // 调用方断开（老师点了「停止演讲」或关掉了放映页）：合成已被取消，属预期路径
                log.debug("[speak][调用方断开，合成已取消 meetingId={}]", meetingId);
            } else {
                log.warn("[speak][播报失败 meetingId={}]: {}", meetingId, rootMessage(e));
                writer.error(rootMessage(e));
            }
        } finally {
            emitter.complete();
        }
    }

    /** 播报内容以数字人身份落消息表：失败只记日志——音频已经播出去了，不能因为落库失败就当播报没发生 */
    private void persist(Long meetingId, String text) {
        if (meetingId == null) {
            return;
        }
        try {
            meetingService.sendMessage(meetingId, null, AGENT_NICKNAME, ROLE_AGENT, text, KIND_DIGITAL);
        } catch (RuntimeException e) {
            log.warn("[speak][数字人发言落库失败 meetingId={}]: {}", meetingId, e.getMessage());
        }
    }

    /**
     * 只有会议号与主持人凭据都带齐才返回会议主键（用于落库）。
     *
     * <p>凭据无效时直接抛业务异常：这条播报是「替这场会说的」，落不了库就不该假装说过了。
     */
    private Long resolveMeetingId(DigitalSpeakReqVO reqVO) {
        String meetingNo = trimToNull(reqVO.getMeetingNo());
        String hostToken = trimToNull(reqVO.getHostToken());
        if (meetingNo == null || hostToken == null) {
            return null;
        }
        LectureMeetingDO meeting = meetingService.requireRunningMeetingByHost(meetingNo, hostToken);
        return meeting.getId();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 当前登录用户 id；speak 是公开接口，未登录时返回 null（复刻音色会回退默认）。 */
    private static Long currentUserId() {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        return loginUser == null ? null : loginUser.getId();
    }

    private static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    /**
     * PCM → SSE {@code chunk} 事件的适配器（{@link OutputStream} 是 {@link TtsSynthPort} 唯一认的出口）。
     *
     * <p>断开后不再尝试发送，并把写入失败**抛回**给合成侧——合成实现正是靠这个 IOException
     * 停止继续向厂商要音频（见 {@code CosyVoiceTtsSynth} 的回调）。
     *
     * <p>包级可见是为了让单测能直接钉住分帧（base64 不能被 JSON 包成带引号的字符串，见
     * {@code DigitalHumanServiceImplTest}）。
     */
    static final class PcmSseWriter extends OutputStream {

        /** 不带换行的 base64 编码器：SSE 的 data 行遇到换行会被拆成多个 data 行 */
        private static final Base64.Encoder BASE64 = Base64.getEncoder();

        private final SseEmitter emitter;
        private boolean broken;

        PcmSseWriter(SseEmitter emitter) {
            this.emitter = emitter;
        }

        boolean isBroken() {
            return broken;
        }

        @Override
        public void write(int value) throws IOException {
            write(new byte[]{(byte) value}, 0, 1);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            if (broken) {
                throw new IOException("调用方已断开");
            }
            try {
                byte[] frame = Arrays.copyOfRange(bytes, offset, offset + length);
                String base64 = new String(BASE64.encode(frame), StandardCharsets.US_ASCII);
                emitter.send(SseEmitter.event().name("chunk").data(base64, MediaType.TEXT_PLAIN));
            } catch (IOException | IllegalStateException e) {
                broken = true;
                throw new IOException("调用方已断开", e);
            }
        }

        void status(String state, Map<String, Object> payload) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("state", state);
            body.putAll(payload);
            send("status", body);
        }

        void error(String message) {
            send("error", Map.of("message", message));
        }

        private void send(String event, Object payload) {
            if (broken) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().name(event).data(payload, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                // 连接已断开：这里没有补救手段，交给下一次写入统一报出去
                broken = true;
            }
        }
    }
}
