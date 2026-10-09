package org.example.backend.module.lecture.digital;

import org.example.backend.framework.common.exception.ServiceException;
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

import static org.example.backend.module.lecture.enums.ErrorCodeConstants.DIGITAL_DISABLED;
import static org.example.backend.module.lecture.enums.ErrorCodeConstants.DIGITAL_LIVEKIT_NOT_CONFIGURED;
import static org.example.backend.module.lecture.enums.ErrorCodeConstants.DIGITAL_TEXT_EMPTY;
import static org.example.backend.module.lecture.enums.ErrorCodeConstants.DIGITAL_TEXT_TOO_LONG;
import static org.example.backend.module.lecture.enums.ErrorCodeConstants.DIGITAL_TTS_FAILED;
import static org.example.backend.module.lecture.enums.ErrorCodeConstants.DIGITAL_WORKER_UNREACHABLE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
    void textIsTrimmed() {
        stubHappyMeeting();
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
}
