package org.example.backend.module.lecture.ppt.service;

import org.example.backend.module.lecture.ppt.model.vo.AvatarModelVO;
import org.example.backend.module.lecture.ppt.model.vo.AvatarPreferenceSaveReqVO;
import org.example.backend.module.lecture.ppt.model.vo.AvatarPreferenceVO;

import java.util.List;

/**
 * 数字人形象目录与用户偏好。
 */
public interface PptAvatarService {

    /** 启用中的形象目录 */
    List<AvatarModelVO> listEnabled();

    /** 当前用户的偏好；没有记录时返回两个空字段（表示默认） */
    AvatarPreferenceVO getPreference(Long userId);

    /** 保存当前用户的偏好；两个编码都可为空（回退默认） */
    AvatarPreferenceVO savePreference(Long userId, AvatarPreferenceSaveReqVO reqVO);

}
