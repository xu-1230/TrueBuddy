package org.example.backend.module.lecture.digital;

import org.example.backend.framework.common.exception.ServiceException;
import org.example.backend.module.lecture.config.DigitalHumanProperties;
import org.example.backend.module.lecture.digital.tts.TtsSynthPort;
import org.example.backend.module.lecture.model.LectureMeetingDO;
import org.example.backend.module.lecture.model.vo.DigitalSpeakReqVO;
import org.example.backend.module.lecture.model.vo.DigitalStatusVO;
import org.example.backend.module.lecture.model.vo.MeetingMessageVO;
import org.example.backend.module.lecture.service.MeetingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.example.backend.framework.common.exception.util.ServiceExceptionUtil.exception;
import static org.example.backend.module.lecture.enums.ErrorCodeConstants.DIGITAL_DISABLED;
import static org.example.backend.module.lecture.enums.ErrorCodeConstants.DIGITAL_TEXT_EMPTY;
import static org.example.backend.module.lecture.enums.ErrorCodeConstants.DIGITAL_TEXT_TOO_LONG;
import static org.example.backend.module.lecture.enums.ErrorCodeConstants.MEETING_HOST_TOKEN_INVALID;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 数字人播报的单测，钉在两个接缝上：{@link TtsSynthPort}（合成）与 SSE 发射器（把 PCM 推给浏览器）。
 *
 * <p>最值钱的是三条：
 * <ol>
 *   <li>合成产出的字节要能<b>原样解回来</b>——base64 若被 JSON 包成带引号的字符串，前端播放器会静默无声；</li>
 *   <li>连接断开时写入必须抛出 IOException：这是合成侧停止继续向厂商要音频的<b>唯一</b>信号（见
 *       {@code CosyVoiceTtsSynth} 的回调），也是「停止演讲」能立刻停下来的原因；</li>
 *   <li>只有带齐会议凭据的播报才落库——彩排试听不该在会议记录里说话。</li>
 * </ol>
 *
 * <p>合成跑在独立线程上（{@link TtsSynthPort} 是阻塞接口），所以断言统一用 Mockito 的
 * {@code timeout} 等它跑完，而不是 sleep。
 */
@ExtendWith(MockitoExtension.class)
class DigitalHumanServiceImplTest {

    private static final String MEETING_NO = "123456";
    private static final String HOST_TOKEN = "host-token";
    private static final String TEXT = "同学们好，我们开始上课。";
    private static final byte[] PCM = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};

    @Mock
    private TtsSynthPort ttsPort;
    @Mock
    private MeetingService meetingService;

    private DigitalHumanProperties properties;
    private DigitalHumanServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new DigitalHumanProperties();
        properties.setEnabled(true);
        properties.setDashscopeApiKey("sk-test");
        service = new DigitalHumanServiceImpl(properties, ttsPort, meetingService);
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    private DigitalSpeakReqVO reqVO(String text) {
        DigitalSpeakReqVO reqVO = new DigitalSpeakReqVO();
        reqVO.setMeetingNo(MEETING_NO);
        reqVO.setHostToken(HOST_TOKEN);
        reqVO.setText(text);
        return reqVO;
    }

    /** 彩排试听：连会议号都不带 */
    private DigitalSpeakReqVO rehearsalReqVO(String text) {
        DigitalSpeakReqVO reqVO = new DigitalSpeakReqVO();
        reqVO.setText(text);
        return reqVO;
    }

    private LectureMeetingDO meeting() {
        LectureMeetingDO meeting = new LectureMeetingDO();
        meeting.setId(1L);
        meeting.setMeetingNo(MEETING_NO);
        return meeting;
    }

    /** 合成产出 {@link #PCM}，首包耗时 1818ms（与厂商实测同量级） */
    private void stubSynthesis() {
        when(ttsPort.synthesize(eq(TEXT), any(OutputStream.class))).thenAnswer(invocation -> {
            ((OutputStream) invocation.getArgument(1)).write(PCM);
            return new TtsSynthPort.TtsStats(1818, PCM.length);
        });
    }

    @Test
    @DisplayName("总开关关闭：直接拒绝，不碰合成也不碰会议")
    void disabledRejectsEverything() {
        properties.setEnabled(false);

        ServiceException error = assertThrows(ServiceException.class, () -> service.speak(reqVO(TEXT)));

        assertEquals(DIGITAL_DISABLED.getCode(), error.getCode());
        verifyNoInteractions(ttsPort, meetingService);
    }

    @Test
    @DisplayName("空文本与超长文本：先于任何外部调用被挡下")
    void textValidation() {
        assertEquals(DIGITAL_TEXT_EMPTY.getCode(),
                assertThrows(ServiceException.class, () -> service.speak(reqVO("   "))).getCode());

        properties.setMaxTextLength(5);
        assertEquals(DIGITAL_TEXT_TOO_LONG.getCode(),
                assertThrows(ServiceException.class, () -> service.speak(reqVO(TEXT))).getCode());

        verifyNoInteractions(ttsPort, meetingService);
    }

    @Test
    @DisplayName("彩排试听（不带会议信息）：照常合成，但不校验会议、不落库")
    void rehearsalSynthesizesWithoutPersisting() {
        stubSynthesis();

        service.speak(rehearsalReqVO(TEXT));

        verify(ttsPort, timeout(2000)).synthesize(eq(TEXT), any(OutputStream.class));
        verifyNoInteractions(meetingService);
    }

    @Test
    @DisplayName("会中播报：文本 trim 后交给合成，并以数字人身份落库")
    void meetingSpeakPersistsAsDigitalHuman() {
        when(meetingService.requireRunningMeetingByHost(MEETING_NO, HOST_TOKEN)).thenReturn(meeting());
        stubSynthesis();
        when(meetingService.sendMessage(eq(1L), isNull(), eq("数字人"), eq(3), eq(TEXT), eq(2)))
                .thenReturn(new MeetingMessageVO());

        service.speak(reqVO("  " + TEXT + "  \n"));

        verify(ttsPort, timeout(2000)).synthesize(eq(TEXT), any(OutputStream.class));
        verify(meetingService, timeout(2000)).sendMessage(eq(1L), isNull(), eq("数字人"), eq(3), eq(TEXT), eq(2));
    }

    @Test
    @DisplayName("凭据无效：一次音频都不合成，直接报错（不假装这场会说过话）")
    void invalidCredentialFailsBeforeSynthesis() {
        when(meetingService.requireRunningMeetingByHost(MEETING_NO, HOST_TOKEN))
                .thenThrow(exception(MEETING_HOST_TOKEN_INVALID));

        ServiceException error = assertThrows(ServiceException.class, () -> service.speak(reqVO(TEXT)));

        assertEquals(MEETING_HOST_TOKEN_INVALID.getCode(), error.getCode());
        verifyNoInteractions(ttsPort);
    }

    @Test
    @DisplayName("合成失败：事件流已建立，不再往外抛异常，也不落库")
    void synthFailureDoesNotPersist() {
        when(meetingService.requireRunningMeetingByHost(MEETING_NO, HOST_TOKEN)).thenReturn(meeting());
        when(ttsPort.synthesize(anyString(), any(OutputStream.class)))
                .thenThrow(new IllegalStateException("语音合成失败: Model not exist"));

        assertDoesNotThrow(() -> {
            service.speak(reqVO(TEXT));
            verify(ttsPort, timeout(2000)).synthesize(eq(TEXT), any(OutputStream.class));
        });
        verify(meetingService, never()).sendMessage(anyLong(), any(), anyString(), anyInt(), anyString(), anyInt());
    }

    @Test
    @DisplayName("状态接口：开关或合成 Key 缺一即不可用（不再探测任何进程）")
    void statusReflectsConfig() {
        DigitalStatusVO ready = service.status();
        assertTrue(ready.getEnabled());
        assertTrue(ready.getAvailable(), "开关打开 + Key 配好 = 确实能出声");

        properties.setDashscopeApiKey("   ");
        assertFalse(service.status().getAvailable(), "Key 是空白时不能谎报可用");

        properties.setEnabled(false);
        DigitalStatusVO off = service.status();
        assertFalse(off.getEnabled());
        assertFalse(off.getAvailable());
    }

    @Test
    @DisplayName("SSE 分帧：chunk 的载荷是不带引号的 base64（被 JSON 包成字符串会让前端静默无声）")
    void chunksAreFramedAsPlainBase64() throws IOException {
        TestEmitter emitter = new TestEmitter(false);
        DigitalHumanServiceImpl.PcmSseWriter writer = new DigitalHumanServiceImpl.PcmSseWriter(emitter);
        String base64 = Base64.getEncoder().encodeToString(PCM);

        writer.write(PCM, 0, PCM.length);

        assertTrue(emitter.hasEntry(base64, MediaType.TEXT_PLAIN),
                "chunk 必须是裸 base64 + text/plain，实际条目: " + emitter.entries);
        assertTrue(emitter.hasText("event:chunk"), "事件名要出现在事件体里: " + emitter.entries);
    }

    @Test
    @DisplayName("SSE 分帧：status 载荷是 JSON（带 state），前端按事件名分流")
    void statusIsFramedAsJson() {
        TestEmitter emitter = new TestEmitter(false);
        DigitalHumanServiceImpl.PcmSseWriter writer = new DigitalHumanServiceImpl.PcmSseWriter(emitter);

        writer.status("done", Map.of("bytes", PCM.length));

        assertTrue(emitter.entries.stream().anyMatch(entry -> entry.getData() instanceof Map<?, ?> body
                && "done".equals(body.get("state"))
                && MediaType.APPLICATION_JSON.equals(entry.getMediaType())), "实际条目: " + emitter.entries);
    }

    @Test
    @DisplayName("连接断开：写入抛 IOException 并置 broken —— 合成侧据此取消，不再发后续事件")
    void writeFailsOnceConnectionIsGone() {
        DigitalHumanServiceImpl.PcmSseWriter writer =
                new DigitalHumanServiceImpl.PcmSseWriter(new TestEmitter(true));

        assertThrows(IOException.class, () -> writer.write(PCM, 0, PCM.length));
        assertTrue(writer.isBroken(), "断开后必须置位，调用方（合成线程）才有据可依");
        // 断开后：写入继续抛（合成侧靠它一路退出），而收尾事件直接丢弃，不再尝试发送、也不再抛
        assertThrows(IOException.class, () -> writer.write(PCM, 0, PCM.length));
        assertDoesNotThrow(() -> {
            writer.status("done", Map.of());
            writer.error("boom");
        });
    }

    @Test
    @DisplayName("未初始化就 send：进早发队列，不抛异常（真实运行时由框架的 ReturnValueHandler 接管）")
    void uninitializedEmitterBuffersEarlyEvents() {
        DigitalHumanServiceImpl.PcmSseWriter writer =
                new DigitalHumanServiceImpl.PcmSseWriter(new SseEmitter(0L));

        assertDoesNotThrow(() -> writer.write(PCM, 0, PCM.length));
        assertFalse(writer.isBroken());
    }

    /**
     * 捕获事件的最小替身。
     *
     * <p>为什么不用真实响应：构造 `SseEmitter` 之后必须由框架初始化（{@code ResponseBodyEmitter.Handler}
     * 与 {@code initialize} 都是包级私有，外部碰不到），能由测试接住的只有 `send` 这一层。
     */
    private static final class TestEmitter extends SseEmitter {

        private final List<ResponseBodyEmitter.DataWithMediaType> entries = new ArrayList<>();
        private final boolean failOnSend;

        private TestEmitter(boolean failOnSend) {
            super(0L);
            this.failOnSend = failOnSend;
        }

        @Override
        public void send(SseEventBuilder builder) {
            if (failOnSend) {
                throw new IllegalStateException("连接已断开");
            }
            entries.addAll(builder.build());
        }

        @Override
        public void send(Object data, MediaType mediaType) {
            if (failOnSend) {
                throw new IllegalStateException("连接已断开");
            }
            entries.add(new ResponseBodyEmitter.DataWithMediaType(data, mediaType));
        }

        private boolean hasEntry(Object data, MediaType mediaType) {
            return entries.stream()
                    .anyMatch(entry -> data.equals(entry.getData()) && mediaType.equals(entry.getMediaType()));
        }

        private boolean hasText(String fragment) {
            return entries.stream().anyMatch(entry -> String.valueOf(entry.getData()).contains(fragment));
        }
    }
}
