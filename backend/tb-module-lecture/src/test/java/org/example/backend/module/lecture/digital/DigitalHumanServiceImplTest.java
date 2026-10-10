package org.example.backend.module.lecture.digital;

import org.example.backend.framework.common.exception.ServiceException;
import org.example.backend.module.lecture.config.DigitalHumanProperties;
import org.example.backend.module.lecture.digital.tts.TtsSynthPort;
import org.example.backend.module.lecture.digital.worker.DigitalHumanWorkerClient;
import org.example.backend.module.lecture.livekit.LiveKitTokenService;
import org.example.backend.module.lecture.model.LectureMeetingDO;
import org.example.backend.module.lecture.model.vo.DigitalCancelReqVO;
import org.example.backend.module.lecture.model.vo.DigitalSayReqVO;
import org.example.backend.module.lecture.model.vo.DigitalSayRespVO;
import org.example.backend.module.lecture.model.vo.DigitalStatusVO;
import org.example.backend.module.lecture.model.vo.MeetingMessageVO;
import org.example.backend.module.lecture.service.MeetingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 数字人播报编排的单测，钉在 {@code docs/17} §3.1 选定的两个接缝上：
 * {@link TtsSynthPort}（合成）与 {@link DigitalHumanWorkerClient}（送进房间）。
 *
 * <p>最值钱的一条是「TTS 产出的字节 == worker 收到的字节」：它同时锁住了管道接线、
 * 关闭时机（不关就永远等不到请求体结束）与不丢不重的流式语义。
 */
@ExtendWith(MockitoExtension.class)
class DigitalHumanServiceImplTest {

    private static final String MEETING_NO = "123456";
    private static final String ROOM = "meeting-123456";
    private static final String HOST_TOKEN = "host-token";
    private static final String AGENT_TOKEN = "agent-jwt";
    private static final String SIGNALLING_URL = "ws://192.168.1.5:7880";
    private static final String TEXT = "同学们好，我们开始上课。";

    @Mock
    private TtsSynthPort ttsPort;
    @Mock
    private DigitalHumanWorkerClient workerClient;
    @Mock
    private LiveKitTokenService tokenService;
    @Mock
    private MeetingService meetingService;

    private DigitalHumanProperties properties;
    private DigitalHumanServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new DigitalHumanProperties();
        properties.setEnabled(true);
        properties.setDashscopeApiKey("sk-test");
        service = new DigitalHumanServiceImpl(properties, ttsPort, workerClient, tokenService, meetingService);
    }

    private DigitalSayReqVO reqVO(String text) {
        DigitalSayReqVO reqVO = new DigitalSayReqVO();
        reqVO.setMeetingNo(MEETING_NO);
        reqVO.setHostToken(HOST_TOKEN);
        reqVO.setText(text);
        return reqVO;
    }

    private LectureMeetingDO meeting() {
        LectureMeetingDO meeting = new LectureMeetingDO();
        meeting.setId(1L);
        meeting.setMeetingNo(MEETING_NO);
        return meeting;
    }

    /** 让 mock 的 MeetingService 走完「主持人凭据有效 + 会议进行中」这条路径 */
    private void stubHappyMeeting() {
        when(meetingService.requireRunningMeetingByHost(MEETING_NO, HOST_TOKEN)).thenReturn(meeting());
        when(tokenService.isConfigured()).thenReturn(true);
    }

    @Test
    @DisplayName("总开关关闭：直接拒绝，不碰 worker 也不碰 LiveKit")
    void disabledRejectsEverything() {
        properties.setEnabled(false);

        ServiceException error = assertThrows(ServiceException.class, () -> service.say(reqVO(TEXT)));

        assertEquals(DIGITAL_DISABLED.getCode(), error.getCode());
        verifyNoInteractions(workerClient, tokenService, meetingService, ttsPort);
    }

    @Test
    @DisplayName("空文本与超长文本：先于任何外部调用被挡下")
    void textValidation() {
        assertEquals(DIGITAL_TEXT_EMPTY.getCode(),
                assertThrows(ServiceException.class, () -> service.say(reqVO("   "))).getCode());

        properties.setMaxTextLength(5);
        assertEquals(DIGITAL_TEXT_TOO_LONG.getCode(),
                assertThrows(ServiceException.class, () -> service.say(reqVO(TEXT))).getCode());

        verifyNoInteractions(workerClient, tokenService, meetingService, ttsPort);
    }

    @Test
    @DisplayName("worker 不可达：给出明确错误码，不开会话、不合成")
    void workerUnreachableFailsFast() {
        stubHappyMeeting();
        when(workerClient.healthy()).thenReturn(false);

        ServiceException error = assertThrows(ServiceException.class, () -> service.say(reqVO(TEXT)));

        assertEquals(DIGITAL_WORKER_UNREACHABLE.getCode(), error.getCode());
        verify(workerClient, never()).openSession(anyString(), anyString(), anyString());
        verifyNoInteractions(ttsPort);
    }

    @Test
    @DisplayName("LiveKit 未配置：拒绝播报（数字人必须有房间可进）")
    void liveKitNotConfigured() {
        when(meetingService.requireRunningMeetingByHost(MEETING_NO, HOST_TOKEN)).thenReturn(meeting());
        when(tokenService.isConfigured()).thenReturn(false);

        ServiceException error = assertThrows(ServiceException.class, () -> service.say(reqVO(TEXT)));

        assertEquals(DIGITAL_LIVEKIT_NOT_CONFIGURED.getCode(), error.getCode());
        verifyNoInteractions(workerClient);
    }

    @Test
    @DisplayName("成功播报：开数字人会话 + PCM 一个字节不差地交给 worker + 以数字人身份落库")
    void sayStreamsPcmAndPersists() throws IOException {
        stubHappyMeeting();
        when(workerClient.healthy()).thenReturn(true);
        when(tokenService.resolveSignallingUrl()).thenReturn(SIGNALLING_URL);
        when(tokenService.createAgentToken(MEETING_NO)).thenReturn(AGENT_TOKEN);

        byte[] pcm = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};
        when(ttsPort.synthesize(eq(TEXT), any(OutputStream.class))).thenAnswer(invocation -> {
            OutputStream out = invocation.getArgument(1);
            out.write(pcm);
            return new TtsSynthPort.TtsStats(1818, pcm.length);
        });

        ByteArrayOutputStream receivedByWorker = new ByteArrayOutputStream();
        doAnswer(invocation -> {
            InputStream in = invocation.getArgument(2);
            in.transferTo(receivedByWorker);
            return null;
        }).when(workerClient).streamPcm(eq(ROOM), anyString(), any(InputStream.class));

        MeetingMessageVO saved = new MeetingMessageVO();
        when(meetingService.sendMessage(eq(1L), isNull(), eq("数字人"), eq(3), eq(TEXT), eq(2))).thenReturn(saved);

        DigitalSayRespVO vo = service.say(reqVO(TEXT));

        verify(workerClient).openSession(ROOM, SIGNALLING_URL, AGENT_TOKEN);
        assertArrayEquals(pcm, receivedByWorker.toByteArray(), "合成产出必须原样到达 worker");
        assertEquals((long) pcm.length, vo.getBytes());
        assertEquals(1818L, vo.getFirstChunkMs());
        // 12 字节 / (48000Hz × 2 字节) ≈ 0.125ms，向下取整为 0：只要求不抛且非负
        assertTrue(vo.getDurationMs() >= 0);
        assertEquals(saved, vo.getMessage());
    }

    @Test
    @DisplayName("合成失败：映射成 DIGITAL_TTS_FAILED 并带上厂商原因，且不落库")
    void ttsFailureIsMappedAndNotPersisted() {
        stubHappyMeeting();
        when(workerClient.healthy()).thenReturn(true);
        when(tokenService.resolveSignallingUrl()).thenReturn(SIGNALLING_URL);
        when(tokenService.createAgentToken(MEETING_NO)).thenReturn(AGENT_TOKEN);
        when(ttsPort.synthesize(anyString(), any(OutputStream.class)))
                .thenThrow(new IllegalStateException("语音合成失败: Model not exist"));

        ServiceException error = assertThrows(ServiceException.class, () -> service.say(reqVO(TEXT)));

        assertEquals(DIGITAL_TTS_FAILED.getCode(), error.getCode());
        assertTrue(error.getMessage().contains("Model not exist"), "原因要透传，否则线上无从判断: " + error.getMessage());
        verify(meetingService, never()).sendMessage(anyLong(), any(), anyString(), anyInt(), anyString(), anyInt());
    }

    @Test
    @DisplayName("状态接口：未启用时不去探测 worker，避免每个学生轮询都打一次本机 HTTP")
    void statusSkipsProbeWhenDisabled() {
        properties.setEnabled(false);

        DigitalStatusVO vo = service.status(MEETING_NO);

        assertFalse(vo.getEnabled());
        assertFalse(vo.getAvailable());
        assertEquals(ROOM, vo.getRoom());
        assertEquals(LiveKitTokenService.AGENT_IDENTITY, vo.getIdentity());
        verifyNoInteractions(workerClient);
    }

    @Test
    @DisplayName("状态接口：启用但 worker 掉线时 available=false（前端据此显示不可用）")
    void statusReportsWorkerDown() {
        when(workerClient.healthy()).thenReturn(false);

        DigitalStatusVO vo = service.status(MEETING_NO);

        assertTrue(vo.getEnabled());
        assertFalse(vo.getWorkerReachable());
        assertFalse(vo.getAvailable());
    }

    @Test
    @DisplayName("结束会议：请 worker 退房间；未启用时不再打扰")
    void leaveRoomBestEffort() {
        service.leaveRoom(MEETING_NO);
        verify(workerClient).closeSession(ROOM);

        properties.setEnabled(false);
        service.leaveRoom(MEETING_NO);
        verify(workerClient, times(1)).closeSession(ROOM);
    }

    @Test
    @DisplayName("文本会被 trim 后再合成与落库")
    void textIsTrimmed() {        stubHappyMeeting();
        when(workerClient.healthy()).thenReturn(true);
        when(tokenService.resolveSignallingUrl()).thenReturn(SIGNALLING_URL);
        when(tokenService.createAgentToken(MEETING_NO)).thenReturn(AGENT_TOKEN);
        when(ttsPort.synthesize(anyString(), any(OutputStream.class)))
                .thenAnswer(invocation -> {
                    ((OutputStream) invocation.getArgument(1)).write("x".getBytes(StandardCharsets.US_ASCII));
                    return new TtsSynthPort.TtsStats(10, 1);
                });
        doAnswer(invocation -> {
            ((InputStream) invocation.getArgument(2)).transferTo(new ByteArrayOutputStream());
            return null;
        }).when(workerClient).streamPcm(eq(ROOM), anyString(), any(InputStream.class));
        when(meetingService.sendMessage(anyLong(), any(), anyString(), anyInt(), anyString(), anyInt()))
                .thenReturn(new MeetingMessageVO());

        service.say(reqVO("  " + TEXT + "  \n"));

        verify(ttsPort).synthesize(eq(TEXT), any(OutputStream.class));
        verify(meetingService).sendMessage(eq(1L), isNull(), eq("数字人"), eq(3), eq(TEXT), eq(2));
    }

    /* ===== 单飞闸门（docs/18 §5.4） ===== */

    @Test
    @DisplayName("同一房间第二段播报被明确拒绝：一条轨上混播比报错更糟")
    void secondSayWhileInFlightIsRejected() throws Exception {
        stubHappyMeeting();
        when(workerClient.healthy()).thenReturn(true);
        when(tokenService.resolveSignallingUrl()).thenReturn(SIGNALLING_URL);
        when(tokenService.createAgentToken(MEETING_NO)).thenReturn(AGENT_TOKEN);

        var inside = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        when(ttsPort.synthesize(anyString(), any(OutputStream.class))).thenAnswer(invocation -> {
            ((OutputStream) invocation.getArgument(1)).write(new byte[]{1, 2, 3, 4});
            return new TtsSynthPort.TtsStats(10, 4);
        });
        // 第一段卡在「交给 worker」这一步，模拟正在播的在途状态
        doAnswer(invocation -> {
            inside.countDown();
            release.await(5, TimeUnit.SECONDS);
            return null;
        }).when(workerClient).streamPcm(eq(ROOM), anyString(), any(InputStream.class));

        Thread first = new Thread(() -> service.say(reqVO(TEXT)));
        first.setDaemon(true);
        first.start();
        assertTrue(inside.await(3, TimeUnit.SECONDS), "第一段应已进入 worker 推送阶段");

        ServiceException busy = assertThrows(ServiceException.class, () -> service.say(reqVO("再来一段")));
        assertEquals(DIGITAL_SPEAK_BUSY.getCode(), busy.getCode());

        release.countDown();
        first.join(5000);
        assertFalse(first.isAlive(), "第一段应正常收尾");
    }

    @Test
    @DisplayName("一段播完后闸门释放：下一次 say 不被误判成忙")
    void gateReleasedAfterSayFinishes() {
        stubHappyMeeting();
        when(workerClient.healthy()).thenReturn(true);
        when(tokenService.resolveSignallingUrl()).thenReturn(SIGNALLING_URL);
        when(tokenService.createAgentToken(MEETING_NO)).thenReturn(AGENT_TOKEN);
        when(ttsPort.synthesize(anyString(), any(OutputStream.class))).thenAnswer(invocation -> {
            ((OutputStream) invocation.getArgument(1)).write(new byte[]{1, 2, 3, 4});
            return new TtsSynthPort.TtsStats(10, 4);
        });
        doAnswer(invocation -> {
            ((InputStream) invocation.getArgument(2)).transferTo(new ByteArrayOutputStream());
            return null;
        }).when(workerClient).streamPcm(eq(ROOM), anyString(), any(InputStream.class));
        when(meetingService.sendMessage(anyLong(), any(), anyString(), anyInt(), anyString(), anyInt()))
                .thenReturn(new MeetingMessageVO());

        service.say(reqVO(TEXT));
        assertDoesNotThrow(() -> service.say(reqVO(TEXT)));
    }

    /* ===== 取消播报 ===== */

    @Test
    @DisplayName("取消要先过主持人凭据校验，再打给 worker")
    void cancelValidatesHostThenCallsWorker() {
        when(meetingService.requireRunningMeetingByHost(MEETING_NO, HOST_TOKEN)).thenReturn(meeting());
        when(workerClient.healthy()).thenReturn(true);
        when(workerClient.cancelSay("req-1")).thenReturn(true);

        assertTrue(service.cancelSay(cancelReq("req-1")));

        verify(meetingService).requireRunningMeetingByHost(MEETING_NO, HOST_TOKEN);
        verify(workerClient).cancelSay("req-1");
    }

    @Test
    @DisplayName("缺 requestId：直接报参数错误，不碰会议也不碰 worker")
    void cancelRequiresRequestId() {
        ServiceException error = assertThrows(ServiceException.class, () -> service.cancelSay(cancelReq("  ")));

        assertEquals(DIGITAL_CANCEL_REQUEST_REQUIRED.getCode(), error.getCode());
        verifyNoInteractions(meetingService, workerClient);
    }

    @Test
    @DisplayName("worker 已不可达时取消返回 false 而不是报错：那段本来就没在响")
    void cancelIsQuietWhenWorkerGone() {
        when(meetingService.requireRunningMeetingByHost(MEETING_NO, HOST_TOKEN)).thenReturn(meeting());
        when(workerClient.healthy()).thenReturn(false);

        assertFalse(service.cancelSay(cancelReq("req-1")));
        verify(workerClient, never()).cancelSay(anyString());
    }

    /* ===== 试听通路（docs/18 §5.3） ===== */

    @Test
    @DisplayName("试听成功：44 字节 WAV 头参数正确 + PCM 原样接在后面，且全程不碰 worker/LiveKit")
    void speakWritesWavWithoutTouchingRoom() throws IOException {
        byte[] pcm = new byte[2048];
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = (byte) i;
        }
        when(ttsPort.synthesize(eq(TEXT), any(OutputStream.class))).thenAnswer(invocation -> {
            ((OutputStream) invocation.getArgument(1)).write(pcm);
            return new TtsSynthPort.TtsStats(1500, pcm.length);
        });

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.speak(TEXT, out);

        byte[] all = out.toByteArray();
        assertEquals(pcm.length + 44, all.length, "只多一个标准 WAV 头");
        assertEquals("RIFF", new String(all, 0, 4, StandardCharsets.US_ASCII));
        assertEquals(36 + pcm.length, readLe32(all, 4), "RIFF 块长 = 36 + 数据长");
        assertEquals("WAVE", new String(all, 8, 4, StandardCharsets.US_ASCII));
        assertEquals("fmt ", new String(all, 12, 4, StandardCharsets.US_ASCII));
        assertEquals(16, readLe32(all, 16), "canonical fmt 块");
        assertEquals(1, readLe16(all, 20), "audio format 1 = PCM");
        assertEquals(1, readLe16(all, 22), "单声道");
        assertEquals(48000, readLe32(all, 24), "采样率必须与合成侧、worker 侧成对");
        assertEquals(96000, readLe32(all, 28), "byteRate = 48000 × 2");
        assertEquals(2, readLe16(all, 32), "blockAlign");
        assertEquals(16, readLe16(all, 34), "位深");
        assertEquals("data", new String(all, 36, 4, StandardCharsets.US_ASCII));
        assertEquals(pcm.length, readLe32(all, 40));
        assertArrayEquals(pcm, java.util.Arrays.copyOfRange(all, 44, all.length), "PCM 不能被改写");

        verifyNoInteractions(workerClient, tokenService, meetingService);
    }

    @Test
    @DisplayName("试听上限比会中播报宽松：整页 remark（>1000 字）不该被 maxTextLength 挡下")
    void speakAllowsWholePageRemark() {
        String longText = "句".repeat(1500);
        when(ttsPort.synthesize(eq(longText), any(OutputStream.class))).thenAnswer(invocation -> {
            ((OutputStream) invocation.getArgument(1)).write(new byte[]{1, 2});
            return new TtsSynthPort.TtsStats(10, 2);
        });

        assertDoesNotThrow(() -> service.speak(longText, new ByteArrayOutputStream()));
    }

    @Test
    @DisplayName("试听的三种拒绝：未启用 / 空文本 / 超长，都不去碰合成")
    void speakValidatesBeforeSynth() {
        properties.setEnabled(false);
        assertEquals(SPEAK_DISABLED.getCode(),
                assertThrows(ServiceException.class, () -> service.speak(TEXT, new ByteArrayOutputStream())).getCode());

        properties.setEnabled(true);
        assertEquals(SPEAK_TEXT_EMPTY.getCode(),
                assertThrows(ServiceException.class, () -> service.speak("   ", new ByteArrayOutputStream())).getCode());

        properties.setMaxSpeakChars(5);
        assertEquals(SPEAK_TEXT_TOO_LONG.getCode(),
                assertThrows(ServiceException.class, () -> service.speak(TEXT, new ByteArrayOutputStream())).getCode());

        verifyNoInteractions(ttsPort, workerClient, tokenService, meetingService);
    }

    @Test
    @DisplayName("试听合成失败：映射成业务错误码并带上厂商原因")
    void speakMapsSynthFailure() {
        when(ttsPort.synthesize(anyString(), any(OutputStream.class)))
                .thenThrow(new IllegalStateException("语音合成失败: Model not exist"));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.speak(TEXT, new ByteArrayOutputStream()));

        assertEquals(SPEAK_SYNTH_FAILED.getCode(), error.getCode());
        assertTrue(error.getMessage().contains("Model not exist"), "原因要透传: " + error.getMessage());
    }

    @Test
    @DisplayName("试听产出超字节上限：拒绝写出，不把半个音频塞给浏览器")
    void speakRejectsOversizedAudio() {
        properties.setMaxSpeakBytes(1024);
        when(ttsPort.synthesize(anyString(), any(OutputStream.class))).thenAnswer(invocation -> {
            ((OutputStream) invocation.getArgument(1)).write(new byte[4096]);
            return new TtsSynthPort.TtsStats(10, 4096);
        });
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        ServiceException error = assertThrows(ServiceException.class, () -> service.speak(TEXT, out));

        assertEquals(SPEAK_SYNTH_FAILED.getCode(), error.getCode());
        assertEquals(0, out.size(), "一个字节都不该写出");
    }

    private DigitalCancelReqVO cancelReq(String requestId) {
        DigitalCancelReqVO reqVO = new DigitalCancelReqVO();
        reqVO.setMeetingNo(MEETING_NO);
        reqVO.setHostToken(HOST_TOKEN);
        reqVO.setRequestId(requestId);
        return reqVO;
    }

    private static int readLe32(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8)
                | ((bytes[offset + 2] & 0xFF) << 16) | ((bytes[offset + 3] & 0xFF) << 24);
    }

    private static int readLe16(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8);
    }
}
