package org.example.backend.module.lecture.ppt.controller;

import jakarta.annotation.Resource;
import org.example.backend.framework.common.pojo.CommonResult;
import org.example.backend.module.lecture.ppt.model.vo.AvatarModelVO;
import org.example.backend.module.lecture.ppt.service.PptAvatarService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.example.backend.framework.common.pojo.CommonResult.success;

/**
 * 数字人形象目录（设置弹窗「数字人」Tab）。
 *
 * <p>与其它 `/ppt/**` 接口一样要求登录（`/ppt/speech/speak` 除外）。
 */
@RestController
@RequestMapping("/ppt/avatar")
public class PptAvatarController {

    @Resource
    private PptAvatarService pptAvatarService;

    /** 启用中的形象目录，按 sort、id 升序 */
    @GetMapping("/list")
    public CommonResult<List<AvatarModelVO>> list() {
        return success(pptAvatarService.listEnabled());
    }

}
