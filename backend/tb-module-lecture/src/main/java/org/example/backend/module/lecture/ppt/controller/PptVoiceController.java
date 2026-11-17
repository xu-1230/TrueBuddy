package org.example.backend.module.lecture.ppt.controller;

import jakarta.annotation.Resource;
import org.example.backend.framework.common.pojo.CommonResult;
import org.example.backend.framework.security.LoginUser;
import org.example.backend.framework.security.util.SecurityFrameworkUtils;
import org.example.backend.module.lecture.ppt.model.vo.AvatarVoiceListRespVO;
import org.example.backend.module.lecture.ppt.model.vo.AvatarVoiceVO;
import org.example.backend.module.lecture.ppt.model.vo.VoiceCloneCommand;
import org.example.backend.module.lecture.ppt.service.PptVoiceService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import static org.example.backend.framework.common.exception.util.ServiceExceptionUtil.exception;
import static org.example.backend.framework.common.pojo.CommonResult.success;
import static org.example.backend.module.lecture.ppt.enums.PptErrorCodeConstants.PPT_VOICE_LOGIN_REQUIRED;

/**
 * 音色库（设置弹窗「声音」Tab）：列表、复刻、删除。
 *
 * <p>全部要求登录；复刻音色只对创建者可见、可删。
 */
@RestController
@RequestMapping("/ppt/voice")
public class PptVoiceController {

    @Resource
    private PptVoiceService pptVoiceService;

    /** 内置音色 + 当前用户复刻音色 */
    @GetMapping("/list")
    public CommonResult<AvatarVoiceListRespVO> list() {
        return success(pptVoiceService.list(loginUserId()));
    }

    /**
     * 复刻音色。
     *
     * <p>`sourceType=upload` 需要配置公网存储（`tb.lecture.digital-human.public-base-url`）；
     * `sourceType=url` 直接使用粘贴的公网音频地址。
     */
    @PostMapping(value = "/clone", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResult<AvatarVoiceVO> clone(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "gender", required = false) String gender,
            @RequestParam(value = "trait", required = false) String trait,
            @RequestParam(value = "language", required = false) String language,
            @RequestParam(value = "sourceType", required = false) String sourceType,
            @RequestParam(value = "url", required = false) String url,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        VoiceCloneCommand command = new VoiceCloneCommand(name, gender, trait, language, sourceType, url, file);
        return success(pptVoiceService.clone(loginUserId(), command));
    }

    /** 删除自己的复刻音色（软删 + 尽力删云端） */
    @DeleteMapping("/{id}")
    public CommonResult<Boolean> delete(@PathVariable("id") Long id) {
        pptVoiceService.delete(loginUserId(), id);
        return success(true);
    }

    private Long loginUserId() {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        if (loginUser == null) {
            throw exception(PPT_VOICE_LOGIN_REQUIRED);
        }
        return loginUser.getId();
    }

}
