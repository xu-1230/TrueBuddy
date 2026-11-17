package org.example.backend.module.lecture.ppt.service.Impl;

import org.example.backend.framework.common.exception.ServiceException;
import org.example.backend.module.infra.mapper.InfraFileMapper;
import org.example.backend.module.infra.service.MinioFileStorageService;
import org.example.backend.module.lecture.config.DigitalHumanProperties;
import org.example.backend.module.lecture.digital.tts.VoiceEnrollmentPort;
import org.example.backend.module.lecture.mapper.AvatarVoiceMapper;
import org.example.backend.module.lecture.mapper.LectureAvatarPreferenceMapper;
import org.example.backend.module.lecture.model.AvatarVoiceDO;
import org.example.backend.module.lecture.ppt.model.vo.AvatarVoiceListRespVO;
import org.example.backend.module.lecture.ppt.model.vo.AvatarVoiceVO;
import org.example.backend.module.lecture.ppt.model.vo.VoiceCloneCommand;
import org.example.backend.module.lecture.ppt.service.PptVoiceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.example.backend.module.lecture.ppt.enums.PptErrorCodeConstants.PPT_VOICE_CLONE_FAILED;
import static org.example.backend.module.lecture.ppt.enums.PptErrorCodeConstants.PPT_VOICE_DELETE_FORBIDDEN;
import static org.example.backend.module.lecture.ppt.enums.PptErrorCodeConstants.PPT_VOICE_LOGIN_REQUIRED;
import static org.example.backend.module.lecture.ppt.enums.PptErrorCodeConstants.PPT_VOICE_SAMPLE_INVALID;
import static org.example.backend.module.lecture.ppt.enums.PptErrorCodeConstants.PPT_VOICE_UPLOAD_DISABLED;
import static org.example.backend.module.lecture.ppt.enums.PptErrorCodeConstants.PPT_VOICE_URL_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 音色库的可见性、复刻校验、播报回退与删除边界。
 */
@ExtendWith(MockitoExtension.class)
class PptVoiceServiceImplTest {

    private static final Long USER_ID = 1L;
    private static final String TTS_MODEL = "cosyvoice-v3-flash";
    private static final String SAMPLE_URL = "https://static.example.com/voice-sample/a.mp3";

    @Mock
    private AvatarVoiceMapper voiceMapper;
    @Mock
    private LectureAvatarPreferenceMapper preferenceMapper;
    @Mock
    private VoiceEnrollmentPort enrollmentPort;
    @Mock
    private MinioFileStorageService minioFileStorageService;
    @Mock
    private InfraFileMapper infraFileMapper;

    private DigitalHumanProperties properties;
    private PptVoiceServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new DigitalHumanProperties();
        properties.setEnabled(true);
        properties.setDashscopeApiKey("sk-test");
        properties.setTtsVoice("longanyang");
        properties.setTtsModel(TTS_MODEL);
        properties.setPublicBaseUrl("https://static.example.com");
        service = new PptVoiceServiceImpl(voiceMapper, preferenceMapper, enrollmentPort, properties,
                minioFileStorageService, infraFileMapper);
    }

    private AvatarVoiceDO systemVoice() {
        AvatarVoiceDO voice = new AvatarVoiceDO();
        voice.setId(1L);
        voice.setVoiceCode("longanyang");
        voice.setName("龙安洋");
        voice.setCategory("社交陪伴");
        voice.setGender("male");
        voice.setSource(1);
        voice.setAudioPreviewUrl("https://help.example.com/longanyang.mp3");
        return voice;
    }

    private AvatarVoiceDO clonedVoice(Long creator) {
        AvatarVoiceDO voice = new AvatarVoiceDO();
        voice.setId(2L);
        voice.setVoiceCode("cosyvoice-v3-flash-myvoice-abc");
        voice.setName("我的声音");
        voice.setCategory("我的复刻");
        voice.setGender("female");
        voice.setSource(2);
        voice.setTtsModel(TTS_MODEL);
        voice.setSampleAudioUrl(SAMPLE_URL);
        voice.setCreator(creator);
        return voice;
    }

    private VoiceCloneCommand urlCommand(String url) {
        return new VoiceCloneCommand("我的声音", "female", "温和", "中文", "url", url, null);
    }

    @Test
    @DisplayName("音色列表要求登录")
    void listRequiresLogin() {
        assertEquals(PPT_VOICE_LOGIN_REQUIRED.getCode(),
                assertThrows(ServiceException.class, () -> service.list(null)).getCode());
        verify(voiceMapper, never()).selectVisible(any());
    }

    @Test
    @DisplayName("列表：内置可见不可删；自己的复刻可删；能力开关按配置返回")
    void listMarksVisibilityAndCapabilities() {
        when(voiceMapper.selectVisible(USER_ID)).thenReturn(List.of(systemVoice(), clonedVoice(USER_ID)));

        AvatarVoiceListRespVO resp = service.list(USER_ID);

        assertEquals(2, resp.getItems().size());
        assertEquals("longanyang", resp.getDefaultVoiceCode());
        assertTrue(resp.getCloneEnabled());
        assertTrue(resp.getUploadEnabled());

        AvatarVoiceVO system = resp.getItems().get(0);
        assertEquals("system", system.getSource());
        assertFalse(system.getCanDelete());
        assertEquals("https://help.example.com/longanyang.mp3", system.getPreviewUrl());

        AvatarVoiceVO cloned = resp.getItems().get(1);
        assertEquals("cloned", cloned.getSource());
        assertTrue(cloned.getCanDelete());
        assertEquals(SAMPLE_URL, cloned.getPreviewUrl());
    }

    @Test
    @DisplayName("未配置公网存储：上传复刻开关为 false")
    void uploadDisabledWithoutPublicBase() {
        properties.setPublicBaseUrl("  ");
        when(voiceMapper.selectVisible(USER_ID)).thenReturn(List.of());

        assertFalse(service.list(USER_ID).getUploadEnabled());
    }

    @Test
    @DisplayName("播报解析：空编码用默认；未知编码回退 not_found")
    void resolveFallsBackWhenMissing() {
        PptVoiceService.VoiceSelection direct = service.resolveForSpeak(null, USER_ID);
        assertFalse(direct.fallback());
        assertEquals("longanyang", direct.voiceCode());
        assertEquals(TTS_MODEL, direct.model());

        when(voiceMapper.selectByCode("missing")).thenReturn(null);
        PptVoiceService.VoiceSelection missing = service.resolveForSpeak("missing", USER_ID);
        assertTrue(missing.fallback());
        assertEquals("not_found", missing.fallbackReason());
        assertEquals("longanyang", missing.voiceCode());
    }

    @Test
    @DisplayName("播报解析：别人的复刻音色回退 forbidden；自己的用行内模型")
    void resolveClonedOwnership() {
        when(voiceMapper.selectByCode("other")).thenReturn(clonedVoice(2L));
        PptVoiceService.VoiceSelection other = service.resolveForSpeak("other", USER_ID);
        assertTrue(other.fallback());
        assertEquals("forbidden", other.fallbackReason());

        AvatarVoiceDO mine = clonedVoice(USER_ID);
        mine.setTtsModel("cosyvoice-v3-plus");
        when(voiceMapper.selectByCode(mine.getVoiceCode())).thenReturn(mine);
        PptVoiceService.VoiceSelection own = service.resolveForSpeak(mine.getVoiceCode(), USER_ID);
        assertFalse(own.fallback());
        assertEquals("cosyvoice-v3-plus", own.model());
    }

    @Test
    @DisplayName("URL 复刻成功：调用厂商、落库并返回可删的复刻音色")
    void cloneByUrlPersists() {
        when(voiceMapper.countByVisibleName(USER_ID, "我的声音")).thenReturn(0);
        when(enrollmentPort.createVoice(eq(TTS_MODEL), anyString(), eq(SAMPLE_URL)))
                .thenReturn("cosyvoice-v3-flash-myvoice-abc");
        when(voiceMapper.insert(any(AvatarVoiceDO.class))).thenAnswer(invocation -> {
            ((AvatarVoiceDO) invocation.getArgument(0)).setId(10L);
            return 1;
        });

        AvatarVoiceVO created = service.clone(USER_ID, urlCommand(SAMPLE_URL));

        assertEquals("cosyvoice-v3-flash-myvoice-abc", created.getVoiceCode());
        assertEquals("cloned", created.getSource());
        assertTrue(created.getCanDelete());
        assertEquals(SAMPLE_URL, created.getPreviewUrl());
        verify(voiceMapper).insert(any(AvatarVoiceDO.class));
    }

    @Test
    @DisplayName("复刻失败：厂商异常转成业务错误；入库失败清理云端音色")
    void cloneFailurePaths() {
        when(voiceMapper.countByVisibleName(USER_ID, "我的声音")).thenReturn(0);
        when(enrollmentPort.createVoice(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("quota exceeded"));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.clone(USER_ID, urlCommand(SAMPLE_URL)));
        assertEquals(PPT_VOICE_CLONE_FAILED.getCode(), error.getCode());

        // 入库失败 → 清理刚创建的云端音色（用 doReturn 覆盖上一个会抛异常的 stub）
        org.mockito.Mockito.doReturn("cosyvoice-v3-flash-myvoice-abc")
                .when(enrollmentPort).createVoice(anyString(), anyString(), anyString());
        when(voiceMapper.insert(any(AvatarVoiceDO.class))).thenThrow(new IllegalStateException("db down"));
        assertThrows(IllegalStateException.class, () -> service.clone(USER_ID, urlCommand(SAMPLE_URL)));
        verify(enrollmentPort).deleteVoice("cosyvoice-v3-flash-myvoice-abc");
    }

    @Test
    @DisplayName("上传复刻：未配置公网存储时拒绝")
    void uploadRequiresPublicBase() {
        properties.setPublicBaseUrl("");
        when(voiceMapper.countByVisibleName(USER_ID, "我的声音")).thenReturn(0);
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        VoiceCloneCommand command = new VoiceCloneCommand("我的声音", "female", null, null, "upload", null, file);

        assertEquals(PPT_VOICE_UPLOAD_DISABLED.getCode(),
                assertThrows(ServiceException.class, () -> service.clone(USER_ID, command)).getCode());
    }

    @Test
    @DisplayName("上传复刻：格式/大小不合规直接拒绝")
    void uploadRejectsInvalidSample() {
        when(voiceMapper.countByVisibleName(USER_ID, "我的声音")).thenReturn(0);
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(11L * 1024 * 1024);
        VoiceCloneCommand command = new VoiceCloneCommand("我的声音", "female", null, null, "upload", null, file);

        assertEquals(PPT_VOICE_SAMPLE_INVALID.getCode(),
                assertThrows(ServiceException.class, () -> service.clone(USER_ID, command)).getCode());
    }

    @Test
    @DisplayName("URL 复刻：非 http/https 拒绝")
    void urlMustBeHttp() {
        when(voiceMapper.countByVisibleName(USER_ID, "我的声音")).thenReturn(0);

        assertEquals(PPT_VOICE_URL_INVALID.getCode(),
                assertThrows(ServiceException.class, () -> service.clone(USER_ID, urlCommand("ftp://x/a.mp3"))).getCode());
    }

    @Test
    @DisplayName("删除：只能删自己的复刻；删除后清空引用它的偏好并尽力删云端")
    void deleteOwnedOnly() {
        AvatarVoiceDO system = systemVoice();
        when(voiceMapper.selectById(1L)).thenReturn(system);
        assertEquals(PPT_VOICE_DELETE_FORBIDDEN.getCode(),
                assertThrows(ServiceException.class, () -> service.delete(USER_ID, 1L)).getCode());

        AvatarVoiceDO mine = clonedVoice(USER_ID);
        when(voiceMapper.selectById(2L)).thenReturn(mine);
        when(voiceMapper.softDeleteOwned(2L, USER_ID)).thenReturn(1);

        service.delete(USER_ID, 2L);

        verify(preferenceMapper).clearVoiceByCode(mine.getVoiceCode());
        verify(enrollmentPort).deleteVoice(mine.getVoiceCode());
    }

}
