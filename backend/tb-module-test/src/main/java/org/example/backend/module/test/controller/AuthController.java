package org.example.backend.module.test.controller;

import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import org.example.backend.framework.common.exception.ServiceException;
import org.example.backend.framework.common.exception.enums.GlobalErrorCodeConstants;
import org.example.backend.framework.common.pojo.CommonResult;
import org.example.backend.framework.security.LoginUser;
import org.example.backend.framework.security.util.SecurityFrameworkUtils;
import org.example.backend.module.test.model.AdminUserDO;
import org.example.backend.module.test.model.vo.AuthLoginReqVO;
import org.example.backend.module.test.model.vo.AuthLoginRespVO;
import org.example.backend.module.test.model.vo.AuthRefreshReqVO;
import org.example.backend.module.test.model.vo.EmailCodeSendReqVO;
import org.example.backend.module.test.model.vo.LoginByCodeReqVO;
import org.example.backend.module.test.model.vo.RegisterReqVO;
import org.example.backend.module.test.model.vo.ResetPasswordReqVO;
import org.example.backend.module.test.model.vo.ResetPasswordRespVO;
import org.example.backend.module.test.model.vo.UserProfileRespVO;
import org.example.backend.module.test.service.AdminAuthService;
import org.example.backend.module.test.service.AdminUserService;
import org.example.backend.module.test.service.PermissionService;
import org.springframework.web.bind.annotation.*;

import static org.example.backend.framework.common.exception.util.ServiceExceptionUtil.exception;
import static org.example.backend.framework.common.pojo.CommonResult.success;
import static org.example.backend.module.test.enums.ErrorCodeConstants.USER_NOT_EXISTS;

@RestController
@RequestMapping("/system/auth")
public class AuthController {

    @Resource
    private AdminAuthService authService;

    @Resource
    private AdminUserService userService;

    @Resource
    private PermissionService permissionService;

    /**
     * 当前登录用户资料：用户端页脚等场景共用，登录即可访问。
     *
     * <p>与管理端 {@code GET /admin/auth/profile} 分开：这里不要求 admin:dashboard，
     * 也不下发权限码，普通用户 / 会员同样可读自己的昵称、头像与角色。
     */
    @GetMapping("/profile")
    public CommonResult<UserProfileRespVO> profile() {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        if (loginUser == null) {
            throw new ServiceException(GlobalErrorCodeConstants.UNAUTHORIZED.getCode(),
                    GlobalErrorCodeConstants.UNAUTHORIZED.getMsg());
        }
        AdminUserDO user = userService.getUser(loginUser.getId());
        if (user == null) {
            throw exception(USER_NOT_EXISTS);
        }

        UserProfileRespVO respVO = new UserProfileRespVO();
        respVO.setUserId(user.getId());
        respVO.setUsername(user.getUsername());
        respVO.setNickname(user.getNickname());
        respVO.setAvatar(user.getAvatar());
        respVO.setRoles(permissionService.getRoleCodes(user.getId()));
        return success(respVO);
    }

    @PostMapping("/login")
    @PermitAll
    public CommonResult<AuthLoginRespVO> login(@RequestBody AuthLoginReqVO reqVO) {
//        AuthLoginReqVO reqVO   = new AuthLoginReqVO();
//        reqVO.setUsername("admin");
//        reqVO.setPassword("admin12344");
        return success( authService.login(reqVO));
    }

    /**
     * 刷新令牌：用 refreshToken 换取新的 accessToken + refreshToken。
     *
     * <p>必须免登录：调用时 accessToken 往往已经过期。
     */
    @PostMapping("/refresh")
    @PermitAll
    public CommonResult<AuthLoginRespVO> refresh(@RequestBody AuthRefreshReqVO reqVO) {
        return success(authService.refreshToken(reqVO.getRefreshToken()));
    }

    /** 发送邮箱验证码：scene = register / reset / login */
    @PostMapping("/send-code")
    @PermitAll
    public CommonResult<Boolean> sendCode(@RequestBody EmailCodeSendReqVO reqVO) {
        authService.sendCode(reqVO);
        return success(true);
    }

    /** 邮箱验证码注册，成功后直接返回登录令牌 */
    @PostMapping("/register")
    @PermitAll
    public CommonResult<AuthLoginRespVO> register(@RequestBody RegisterReqVO reqVO) {
        return success(authService.register(reqVO));
    }

    /** 邮箱验证码登录 */
    @PostMapping("/login/code")
    @PermitAll
    public CommonResult<AuthLoginRespVO> loginByCode(@RequestBody LoginByCodeReqVO reqVO) {
        return success(authService.loginByCode(reqVO));
    }

    /** 邮箱验证码重置密码 */
    @PostMapping("/reset-password")
    @PermitAll
    public CommonResult<ResetPasswordRespVO> resetPassword(@RequestBody ResetPasswordReqVO reqVO) {
        return success(authService.resetPassword(reqVO));
    }

}
