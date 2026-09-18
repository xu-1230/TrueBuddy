package org.example.backend.module.test.controller;

import org.example.backend.framework.common.enums.UserTypeEnum;
import org.example.backend.framework.common.pojo.CommonResult;
import org.example.backend.framework.security.LoginUser;
import org.example.backend.framework.security.util.SecurityFrameworkUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.example.backend.framework.common.exception.util.ServiceExceptionUtil.exception;
import static org.example.backend.framework.common.pojo.CommonResult.success;
import static org.example.backend.module.test.enums.ErrorCodeConstants.AUTH_PERMISSION_DENIED;

@RestController
@RequestMapping("/identity")
public class IdentityController {

    @GetMapping("/normal")
    public CommonResult<String> normal() {
        return success(checkIdentity(UserTypeEnum.NORMAL));
    }

    @GetMapping("/member")
    public CommonResult<String> member() {
        return success(checkIdentity(UserTypeEnum.MEMBER));
    }

    @GetMapping("/admin")
    public CommonResult<String> admin() {
        return success(checkIdentity(UserTypeEnum.ADMIN));
    }

    private String checkIdentity(UserTypeEnum expected) {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        System.out.println(loginUser);
        if (loginUser == null || !expected.getValue().equals(loginUser.getUserType())) {
            throw exception(AUTH_PERMISSION_DENIED);
        }
        return "当前用户是：" + expected.getName();
    }
}