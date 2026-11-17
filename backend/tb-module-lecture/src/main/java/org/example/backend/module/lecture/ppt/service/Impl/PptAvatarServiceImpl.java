package org.example.backend.module.lecture.ppt.service.Impl;

import lombok.extern.slf4j.Slf4j;
import org.example.backend.module.lecture.mapper.AvatarModelMapper;
import org.example.backend.module.lecture.mapper.AvatarVoiceMapper;
import org.example.backend.module.lecture.mapper.LectureAvatarPreferenceMapper;
import org.example.backend.module.lecture.model.AvatarModelDO;
import org.example.backend.module.lecture.model.AvatarVoiceDO;
import org.example.backend.module.lecture.model.LectureAvatarPreferenceDO;
import org.example.backend.module.lecture.ppt.model.vo.AvatarModelVO;
import org.example.backend.module.lecture.ppt.model.vo.AvatarPreferenceSaveReqVO;
import org.example.backend.module.lecture.ppt.model.vo.AvatarPreferenceVO;
import org.example.backend.module.lecture.ppt.service.PptAvatarService;
import org.springframework.stereotype.Service;

import java.util.List;

import static org.example.backend.framework.common.exception.util.ServiceExceptionUtil.exception;
import static org.example.backend.module.lecture.ppt.enums.PptErrorCodeConstants.PPT_AVATAR_MODEL_NOT_FOUND;
import static org.example.backend.module.lecture.ppt.enums.PptErrorCodeConstants.PPT_PREFERENCE_LOGIN_REQUIRED;
import static org.example.backend.module.lecture.ppt.enums.PptErrorCodeConstants.PPT_VOICE_NOT_FOUND;

/**
 * 数字人目录与偏好实现。
 *
 * <p>目录是全局的（`avatar_model` 只存可展示清单与缩略图），偏好按 creator 一人一行。
 * 保存偏好时做两件事：形象编码必须在启用目录里；音色必须对当前用户可见（内置或自己的复刻）。
 */
@Slf4j
@Service
public class PptAvatarServiceImpl implements PptAvatarService {

    private final AvatarModelMapper avatarModelMapper;
    private final AvatarVoiceMapper avatarVoiceMapper;
    private final LectureAvatarPreferenceMapper preferenceMapper;

    public PptAvatarServiceImpl(AvatarModelMapper avatarModelMapper,
                                AvatarVoiceMapper avatarVoiceMapper,
                                LectureAvatarPreferenceMapper preferenceMapper) {
        this.avatarModelMapper = avatarModelMapper;
        this.avatarVoiceMapper = avatarVoiceMapper;
        this.preferenceMapper = preferenceMapper;
    }

    @Override
    public List<AvatarModelVO> listEnabled() {
        return avatarModelMapper.selectEnabled().stream().map(this::toModelVO).toList();
    }

    @Override
    public AvatarPreferenceVO getPreference(Long userId) {
        requireUser(userId);
        LectureAvatarPreferenceDO preference = preferenceMapper.selectByCreator(userId);
        AvatarPreferenceVO vo = new AvatarPreferenceVO();
        if (preference != null) {
            vo.setAvatarModelCode(preference.getAvatarModelCode());
            vo.setVoiceCode(preference.getVoiceCode());
        }
        return vo;
    }

    @Override
    public AvatarPreferenceVO savePreference(Long userId, AvatarPreferenceSaveReqVO reqVO) {
        requireUser(userId);
        String avatarCode = trimToNull(reqVO == null ? null : reqVO.getAvatarModelCode());
        String voiceCode = trimToNull(reqVO == null ? null : reqVO.getVoiceCode());

        if (avatarCode != null && avatarModelMapper.selectEnabledByCode(avatarCode) == null) {
            throw exception(PPT_AVATAR_MODEL_NOT_FOUND);
        }
        if (voiceCode != null) {
            AvatarVoiceDO voice = avatarVoiceMapper.selectByCode(voiceCode);
            boolean visible = voice != null
                    && (voice.getCreator() == null || voice.getCreator().equals(userId));
            if (!visible) {
                throw exception(PPT_VOICE_NOT_FOUND);
            }
        }

        LectureAvatarPreferenceDO existing = preferenceMapper.selectByCreator(userId);
        if (existing == null) {
            LectureAvatarPreferenceDO created = new LectureAvatarPreferenceDO();
            created.setCreator(userId);
            created.setAvatarModelCode(avatarCode);
            created.setVoiceCode(voiceCode);
            created.setUpdater(userId);
            preferenceMapper.insert(created);
        } else {
            existing.setAvatarModelCode(avatarCode);
            existing.setVoiceCode(voiceCode);
            existing.setUpdater(userId);
            preferenceMapper.update(existing);
        }

        AvatarPreferenceVO vo = new AvatarPreferenceVO();
        vo.setAvatarModelCode(avatarCode);
        vo.setVoiceCode(voiceCode);
        return vo;
    }

    private void requireUser(Long userId) {
        if (userId == null) {
            throw exception(PPT_PREFERENCE_LOGIN_REQUIRED);
        }
    }

    private AvatarModelVO toModelVO(AvatarModelDO model) {
        AvatarModelVO vo = new AvatarModelVO();
        vo.setModelCode(model.getModelCode());
        vo.setName(model.getName());
        vo.setKind(model.getKind());
        vo.setPreviewImage(model.getPreviewImage());
        vo.setSort(model.getSort());
        return vo;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

}
