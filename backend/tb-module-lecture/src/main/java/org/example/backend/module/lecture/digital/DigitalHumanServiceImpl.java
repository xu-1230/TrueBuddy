package org.example.backend.module.lecture.digital;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.module.lecture.config.DigitalHumanProperties;
import org.example.backend.module.lecture.digital.tts.TtsSynthPort;
import org.example.backend.module.lecture.digital.worker.DigitalHumanWorkerClient;
import org.example.backend.module.lecture.livekit.LiveKitTokenService;
import org.example.backend.module.lecture.model.LectureMeetingDO;
import org.example.backend.module.lecture.model.vo.DigitalSayReqVO;
import org.example.backend.module.lecture.model.vo.DigitalSayRespVO;
import org.example.backend.module.lecture.model.vo.DigitalStatusVO;
import org.example.backend.module.lecture.model.vo.MeetingMessageVO;
import org.example.backend.module.lecture.service.MeetingService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.example.backend.framework.common.exception.util.ServiceExceptionUtil.exception;
import static org.example.backend.module.lecture.enums.ErrorCodeConstants.DIGITAL_DISABLED;
import static org.example.backend.module.lecture.enums.ErrorCodeConstants.DIGITAL_LIVEKIT_NOT_CONFIGURED;
import static org.example.backend.module.lecture.enums.ErrorCodeConstants.DIGITAL_TEXT_EMPTY;
import static org.example.backend.module.lecture.enums.ErrorCodeConstants.DIGITAL_TEXT_TOO_LONG;
import static org.example.backend.module.lecture.enums.ErrorCodeConstants.DIGITAL_TTS_FAILED;
import static org.example.backend.module.lecture.enums.ErrorCodeConstants.DIGITAL_WORKER_UNREACHABLE;

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

    private final DigitalHumanProperties properties;
    private final TtsSynthPort ttsPort;
    private final DigitalHumanWorkerClient workerClient;
    private final LiveKitTokenService tokenService;
    private final MeetingService meetingService;
    private final ExecutorService synthExecutor;

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
        String requestId = UUID.randomUUID().toString().replace("-", "");
        TtsSynthPort.TtsStats stats;
        try {
            stats = streamToWorker(meeting.getMeetingNo(), room, requestId, text);
        } catch (RuntimeException e) {
            log.warn("[say][播报失败 meetingNo={}, requestId={}]: {}", meeting.getMeetingNo(), requestId, e.getMessage());
            throw exception(DIGITAL_TTS_FAILED, rootMessage(e));
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
