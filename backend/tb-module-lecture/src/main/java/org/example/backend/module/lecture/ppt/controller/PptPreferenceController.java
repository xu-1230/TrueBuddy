package org.example.backend.module.lecture.ppt.controller;

import jakarta.annotation.Resource;
import org.example.backend.framework.common.pojo.CommonResult;
import org.example.backend.framework.security.LoginUser;
import org.example.backend.framework.security.util.SecurityFrameworkUtils;
import org.example.backend.module.lecture.ppt.model.vo.AvatarPreferenceSaveReqVO;
import org.example.backend.module.lecture.ppt.model.vo.AvatarPreferenceVO;
import org.example.backend.module.lecture.ppt.service.PptAvatarService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.example.backend.framework.common.exception.util.ServiceExceptionUtil.exception;
import static org.example.backend.framework.common.pojo.CommonResult.success;
import static org.example.backend.module.lecture.ppt.enums.PptErrorCodeConstants.PPT_PREFERENCE_LOGIN_REQUIRED;

/**
 * 当前用户的数字人与音色偏好（按 creator 一人一行）。
 */
@RestController
@RequestMapping("/ppt/preference")
public class PptPreferenceController {

    @Resource
    private PptAvatarService pptAvatarService;

    /** 读取当前用户的偏好；没有记录时返回两个空字段（表示默认） */
    @GetMapping
    public CommonResult<AvatarPreferenceVO> get() {
        return success(pptAvatarService.getPreference(loginUserId()));
    }

    /** 保存当前用户的偏好；两个编码都可为空（回退默认） */
    @PutMapping
    public CommonResult<AvatarPreferenceVO> save(@RequestBody AvatarPreferenceSaveReqVO reqVO) {
        return success(pptAvatarService.savePreference(loginUserId(), reqVO));
    }

    private Long loginUserId() {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        if (loginUser == null) {
            throw exception(PPT_PREFERENCE_LOGIN_REQUIRED);
        }
        return loginUser.getId();
    }

}
