package org.example.backend.module.lecture.digital;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.module.lecture.config.DigitalHumanProperties;
import org.example.backend.module.lecture.digital.tts.TtsSynthPort;
import org.example.backend.module.lecture.digital.tts.WavHeader;
import org.example.backend.module.lecture.digital.worker.DigitalHumanWorkerClient;
import org.example.backend.module.lecture.livekit.LiveKitTokenService;
import org.example.backend.module.lecture.model.LectureMeetingDO;
import org.example.backend.module.lecture.model.vo.DigitalCancelReqVO;
import org.example.backend.module.lecture.model.vo.DigitalSayReqVO;
import org.example.backend.module.lecture.model.vo.DigitalSayRespVO;
import org.example.backend.module.lecture.model.vo.DigitalStatusVO;
import org.example.backend.module.lecture.model.vo.MeetingMessageVO;
import org.example.backend.module.lecture.service.MeetingService;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.example.backend.framework.common.exception.util.ServiceExceptionUtil.exception;
import static org.example.backend.module.lecture.enums.ErrorCodeConstants.DIGITAL_CANCEL_REQUEST_REQUIRED;
import static org.example.backend.module.lecture.enums.ErrorCodeConstants.DIGITAL_DISABLED;
import static org.example.backend.module.lecture.enums.ErrorCodeConstants.DIGITAL_LIVEKIT_NOT_CONFIGURED;
import static org.example.backend.module.lecture.enums.ErrorCodeConstants.DIGITAL_SPEAK_BUSY;
import static org.example.backend.module.lecture.enums.ErrorCodeConstants.DIGITAL_TEXT_EMPTY;
import static org.example.backend.module.lecture.enums.ErrorCodeConstants.DIGITAL_TEXT_TOO_LONG;
import static org.example.backend.module.lecture.enums.ErrorCodeConstants.DIGITAL_TTS_FAILED;
import static org.example.backend.module.lecture.enums.ErrorCodeConstants.DIGITAL_WORKER_UNREACHABLE;
import static org.example.backend.module.lecture.enums.ErrorCodeConstants.SPEAK_DISABLED;
import static org.example.backend.module.lecture.enums.ErrorCodeConstants.SPEAK_SYNTH_FAILED;
import static org.example.backend.module.lecture.enums.ErrorCodeConstants.SPEAK_TEXT_EMPTY;
import static org.example.backend.module.lecture.enums.ErrorCodeConstants.SPEAK_TEXT_TOO_LONG;

/**
 * 播报的编排者：合成在本地线程里跑，PCM 一边产出就一边推给 worker。
 *
 * <p>用 {@link PipedOutputStream} 把「回调式合成」接成「流式请求体」，是为了<b>不等整段合成完</b>：
 * 实测 CosyVoice 首包约 1.8s、一段 5s 音频总耗时约 5s，等完再推会让学生多空等约 3s。
 */
@Slf4j
@Service
public class DigitalHumanServiceImpl implements DigitalHumanService {

    /** 角色：数字人（与 LiveKit 令牌 metadata.role 同口径） */
    private static final int ROLE_AGENT = 3;
    /** 消息类型：数字人播报（1=文字，3=语音转写预留，见 docs/16 §4） */
    private static final int KIND_DIGITAL = 2;
    /** 数字人发言时的昵称 */
    private static final String AGENT_NICKNAME = "数字人";
    /** 管道缓冲：太小会让合成线程频繁阻塞 */
    private static final int PIPE_BUFFER_BYTES = 64 * 1024;
    /** 合成侧自身上限 60s，这里留余量兜底 */
    private static final int SYNTH_WAIT_SECONDS = 75;
    /** 客户端可自带的播报标识形状：worker 侧要把它拼进 {@code /say/{id}} 路径 */
    private static final Pattern REQUEST_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]+");

    private final DigitalHumanProperties properties;
    private final TtsSynthPort ttsPort;
    private final DigitalHumanWorkerClient workerClient;
    private final LiveKitTokenService tokenService;
    private final MeetingService meetingService;
    private final ExecutorService synthExecutor;

    /** 在途播报：房间名 → requestId。用于单飞闸门，进程内状态，重启即清 */
    private final Map<String, String> inFlight = new ConcurrentHashMap<>();

    public DigitalHumanServiceImpl(DigitalHumanProperties properties,
                                   TtsSynthPort ttsPort,
                                   DigitalHumanWorkerClient workerClient,
                                   LiveKitTokenService tokenService,
                                   MeetingService meetingService) {
        this.properties = properties;
        this.ttsPort = ttsPort;
        this.workerClient = workerClient;
        this.tokenService = tokenService;
        this.meetingService = meetingService;
        this.synthExecutor = Executors.newFixedThreadPool(Math.max(1, properties.getMaxConcurrentSay()), runnable -> {
            Thread thread = new Thread(runnable, "digital-human-tts");
            thread.setDaemon(true);
            return thread;
        });
    }

    @PreDestroy
    public void shutdown() {
        synthExecutor.shutdownNow();
    }

    @Override
    public DigitalStatusVO status(String meetingNo) {
        boolean enabled = Boolean.TRUE.equals(properties.getEnabled());
        boolean reachable = enabled && workerClient.healthy();

        DigitalStatusVO vo = new DigitalStatusVO();
        vo.setEnabled(enabled);
        vo.setWorkerReachable(reachable);
        vo.setAvailable(reachable);
        vo.setRoom(LiveKitTokenService.roomNameOf(meetingNo == null ? "" : meetingNo.trim()));
        vo.setIdentity(LiveKitTokenService.AGENT_IDENTITY);
        return vo;
    }

    @Override
    public DigitalSayRespVO say(DigitalSayReqVO reqVO) {
        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            throw exception(DIGITAL_DISABLED);
        }
        String text = reqVO.getText() == null ? "" : reqVO.getText().trim();
        if (text.isEmpty()) {
            throw exception(DIGITAL_TEXT_EMPTY);
        }
        int maxLength = properties.getMaxTextLength();
        if (text.length() > maxLength) {
            throw exception(DIGITAL_TEXT_TOO_LONG, maxLength);
        }
        LectureMeetingDO meeting = meetingService.requireRunningMeetingByHost(reqVO.getMeetingNo(), reqVO.getHostToken());
        if (!tokenService.isConfigured()) {
            throw exception(DIGITAL_LIVEKIT_NOT_CONFIGURED);
        }
        if (!workerClient.healthy()) {
            throw exception(DIGITAL_WORKER_UNREACHABLE);
        }

        String room = LiveKitTokenService.roomNameOf(meeting.getMeetingNo());
        String requestId = resolveRequestId(reqVO.getRequestId());

        // 单飞闸门：一个房间只有一条常驻音频轨，两段同时推会在轨上交错混播（docs/18 §5.4）。
        // 编排在前端，后端保持「一次 say = 一段文本」，所以这里只做明确拒绝，不排队。
        if (inFlight.putIfAbsent(room, requestId) != null) {
            throw exception(DIGITAL_SPEAK_BUSY);
        }
        TtsSynthPort.TtsStats stats;
        try {
            stats = streamToWorker(meeting.getMeetingNo(), room, requestId, text);
        } catch (RuntimeException e) {
            log.warn("[say][播报失败 meetingNo={}, requestId={}]: {}", meeting.getMeetingNo(), requestId, e.getMessage());
            throw exception(DIGITAL_TTS_FAILED, rootMessage(e));
        } finally {
            inFlight.remove(room, requestId);
        }

        MeetingMessageVO message = meetingService.sendMessage(
                meeting.getId(), null, AGENT_NICKNAME, ROLE_AGENT, text, KIND_DIGITAL);

        DigitalSayRespVO vo = new DigitalSayRespVO();
        vo.setRequestId(requestId);
        vo.setFirstChunkMs(stats.firstChunkMs());
        vo.setBytes(stats.bytes());
        vo.setDurationMs(stats.durationMs());
        vo.setMessage(message);
        log.info("[say][数字人播报已交给 worker meetingNo={}, requestId={}, durationMs={}, firstChunkMs={}]",
                meeting.getMeetingNo(), requestId, stats.durationMs(), stats.firstChunkMs());
        return vo;
    }

    @Override
    public void leaveRoom(String meetingNo) {
        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            return;
        }
        workerClient.closeSession(LiveKitTokenService.roomNameOf(meetingNo));
    }

    @Override
    public boolean cancelSay(DigitalCancelReqVO reqVO) {
        String requestId = reqVO.getRequestId() == null ? "" : reqVO.getRequestId().trim();
        if (requestId.isEmpty()) {
            throw exception(DIGITAL_CANCEL_REQUEST_REQUIRED);
        }
        // 凭据校验与 say 同一条路径：不是主持人就不能掐断别人正在播的这段
        LectureMeetingDO meeting = meetingService.requireRunningMeetingByHost(reqVO.getMeetingNo(), reqVO.getHostToken());
        if (!Boolean.TRUE.equals(properties.getEnabled()) || !workerClient.healthy()) {
            // worker 不在了意味着这段本来就没在响，按「已取消」返回让编排层把状态收敛掉
            inFlight.remove(LiveKitTokenService.roomNameOf(meeting.getMeetingNo()), requestId);
            return false;
        }
        boolean cancelled = workerClient.cancelSay(requestId);
        inFlight.remove(LiveKitTokenService.roomNameOf(meeting.getMeetingNo()), requestId);
        return cancelled;
    }

    @Override
    public void speak(String text, OutputStream out) {
        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            throw exception(SPEAK_DISABLED);
        }
        String spoken = text == null ? "" : text.trim();
        if (spoken.isEmpty()) {
            throw exception(SPEAK_TEXT_EMPTY);
        }
        int maxChars = properties.getMaxSpeakChars();
        if (spoken.length() > maxChars) {
            throw exception(SPEAK_TEXT_TOO_LONG, maxChars);
        }

        // 刻意先把 PCM 收满再写头：WAV 的 data 长度必须已知，而 chunked 响应没法回填。
        // 上限由 maxSpeakBytes 兜住（合成侧本身 60s 超时，正常到不了）。
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        TtsSynthPort.TtsStats stats;
        try {
            stats = ttsPort.synthesize(spoken, pcm);
        } catch (RuntimeException e) {
            throw exception(SPEAK_SYNTH_FAILED, rootMessage(e));
        }
        byte[] body = pcm.toByteArray();
        if (body.length > properties.getMaxSpeakBytes()) {
            throw exception(SPEAK_SYNTH_FAILED, "音频超出上限 " + properties.getMaxSpeakBytes() + " 字节");
        }
        try {
            WavHeader.write(out, body.length);
            out.write(body);
            out.flush();
        } catch (IOException e) {
            // 客户端断开（前端 AbortController）会走到这里：不是故障，也不值得往上报错
            log.info("[speak][试听写出中断: {}]", e.getMessage());
        }
        log.info("[speak][试听合成完成 firstChunkMs={}, bytes={}]", stats.firstChunkMs(), body.length);
    }

    /**
     * 合成 → 管道 → worker。失败一律收敛成带原因的运行时异常，由调用方映射成业务错误码。
     */
    private TtsSynthPort.TtsStats streamToWorker(String meetingNo, String room, String requestId, String text) {
        workerClient.openSession(room, tokenService.resolveSignallingUrl(), tokenService.createAgentToken(meetingNo));

        PipedInputStream pcmIn;
        final PipedOutputStream pcmOut;
        try {
            pcmIn = new PipedInputStream(PIPE_BUFFER_BYTES);
            pcmOut = new PipedOutputStream(pcmIn);
        } catch (IOException e) {
            throw new IllegalStateException("音频管道初始化失败: " + e.getMessage(), e);
        }

        Future<TtsSynthPort.TtsStats> future = synthExecutor.submit(() -> {
            try {
                return ttsPort.synthesize(text, pcmOut);
            } finally {
                closeQuietly(pcmOut);
            }
        });
        try {
            workerClient.streamPcm(room, requestId, pcmIn);
            return future.get(SYNTH_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            future.cancel(true);
            throw new IllegalStateException(rootMessage(e), e);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new IllegalStateException("播报超时（" + SYNTH_WAIT_SECONDS + "s 未结束）", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new IllegalStateException("播报被中断", e);
        } finally {
            closeQuietly(pcmIn);
        }
    }

    /**
     * 播报标识：优先用客户端带来的（前端要在响应回来之前就具备打断能力），
     * 为空或形状不对就退回后端生成——不相信客户端的字符串形状，它会被拼进 worker 的 URL 路径。
     */
    private static String resolveRequestId(String provided) {
        String candidate = provided == null ? "" : provided.trim();
        if (candidate.length() >= 8 && candidate.length() <= 64 && REQUEST_ID_PATTERN.matcher(candidate).matches()) {
            return candidate;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private static void closeQuietly(OutputStream out) {
        try {
            out.close();
        } catch (IOException ignored) {
            // 关闭失败不影响结果：流已写完或早已断开
        }
    }

    private static void closeQuietly(InputStream in) {
        try {
            in.close();
        } catch (IOException ignored) {
            // 同上
        }
    }
}
