package org.example.backend.module.lecture.ppt.service.Impl;

import org.example.backend.framework.common.exception.ServiceException;
import org.example.backend.module.lecture.mapper.AvatarModelMapper;
import org.example.backend.module.lecture.mapper.AvatarVoiceMapper;
import org.example.backend.module.lecture.mapper.LectureAvatarPreferenceMapper;
import org.example.backend.module.lecture.model.AvatarModelDO;
import org.example.backend.module.lecture.model.AvatarVoiceDO;
import org.example.backend.module.lecture.model.LectureAvatarPreferenceDO;
import org.example.backend.module.lecture.ppt.model.vo.AvatarPreferenceSaveReqVO;
import org.example.backend.module.lecture.ppt.model.vo.AvatarPreferenceVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.example.backend.module.lecture.ppt.enums.PptErrorCodeConstants.PPT_AVATAR_MODEL_NOT_FOUND;
import static org.example.backend.module.lecture.ppt.enums.PptErrorCodeConstants.PPT_PREFERENCE_LOGIN_REQUIRED;
import static org.example.backend.module.lecture.ppt.enums.PptErrorCodeConstants.PPT_VOICE_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 偏好保存的校验与 upsert：形象必须在启用目录里、音色必须对当前用户可见。
 */
@ExtendWith(MockitoExtension.class)
class PptAvatarServiceImplTest {

    private static final Long USER_ID = 1L;

    @Mock
    private AvatarModelMapper avatarModelMapper;
    @Mock
    private AvatarVoiceMapper avatarVoiceMapper;
    @Mock
    private LectureAvatarPreferenceMapper preferenceMapper;

    private PptAvatarServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PptAvatarServiceImpl(avatarModelMapper, avatarVoiceMapper, preferenceMapper);
    }

    private AvatarModelDO model(String code) {
        AvatarModelDO model = new AvatarModelDO();
        model.setModelCode(code);
        model.setName(code);
        model.setEnabled(true);
        return model;
    }

    private AvatarVoiceDO voice(String code, Long creator) {
        AvatarVoiceDO voice = new AvatarVoiceDO();
        voice.setVoiceCode(code);
        voice.setCreator(creator);
        voice.setSource(creator == null ? 1 : 2);
        return voice;
    }

    private AvatarPreferenceSaveReqVO req(String avatarCode, String voiceCode) {
        AvatarPreferenceSaveReqVO req = new AvatarPreferenceSaveReqVO();
        req.setAvatarModelCode(avatarCode);
        req.setVoiceCode(voiceCode);
        return req;
    }

    @Test
    @DisplayName("没有登录用户：偏好读写都拒绝")
    void requiresLoginUser() {
        assertEquals(PPT_PREFERENCE_LOGIN_REQUIRED.getCode(),
                assertThrows(ServiceException.class, () -> service.getPreference(null)).getCode());
        assertEquals(PPT_PREFERENCE_LOGIN_REQUIRED.getCode(),
                assertThrows(ServiceException.class, () -> service.savePreference(null, req("Haru", "longanyang"))).getCode());

        verify(preferenceMapper, never()).selectByCreator(any());
    }

    @Test
    @DisplayName("形象编码不在启用目录：拒绝保存")
    void rejectsUnknownAvatar() {
        when(avatarModelMapper.selectEnabledByCode("Nope")).thenReturn(null);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.savePreference(USER_ID, req("Nope", null)));

        assertEquals(PPT_AVATAR_MODEL_NOT_FOUND.getCode(), error.getCode());
        verify(preferenceMapper, never()).insert(any());
    }

    @Test
    @DisplayName("别人的复刻音色：对本用户不可见，拒绝保存")
    void rejectsInvisibleVoice() {
        when(avatarModelMapper.selectEnabledByCode("Haru")).thenReturn(model("Haru"));
        when(avatarVoiceMapper.selectByCode("other-clone")).thenReturn(voice("other-clone", 2L));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.savePreference(USER_ID, req("Haru", "other-clone")));

        assertEquals(PPT_VOICE_NOT_FOUND.getCode(), error.getCode());
    }

    @Test
    @DisplayName("首次保存：插入新行，编码 trim 后落库")
    void insertsFirstPreference() {
        when(avatarModelMapper.selectEnabledByCode("Haru")).thenReturn(model("Haru"));
        when(avatarVoiceMapper.selectByCode("longanyang")).thenReturn(voice("longanyang", null));
        when(preferenceMapper.selectByCreator(USER_ID)).thenReturn(null);

        AvatarPreferenceVO saved = service.savePreference(USER_ID, req("  Haru  ", " longanyang "));

        assertEquals("Haru", saved.getAvatarModelCode());
        assertEquals("longanyang", saved.getVoiceCode());
        verify(preferenceMapper).insert(any(LectureAvatarPreferenceDO.class));
    }

    @Test
    @DisplayName("已有偏好：原行更新；空编码表示回退默认")
    void updatesExistingPreference() {
        LectureAvatarPreferenceDO existing = new LectureAvatarPreferenceDO();
        existing.setId(9L);
        existing.setCreator(USER_ID);
        when(preferenceMapper.selectByCreator(USER_ID)).thenReturn(existing);

        AvatarPreferenceVO saved = service.savePreference(USER_ID, req("", ""));

        assertNull(saved.getAvatarModelCode());
        assertNull(saved.getVoiceCode());
        verify(preferenceMapper).update(existing);
        assertNull(existing.getAvatarModelCode());
        assertNull(existing.getVoiceCode());
    }

}
