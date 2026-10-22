package org.example.backend.module.test.service;

import org.example.backend.module.test.model.AdminUserDO;
import org.example.backend.module.test.model.vo.AuthLoginReqVO;
import org.example.backend.module.test.model.vo.AuthLoginRespVO;
import org.example.backend.module.test.model.vo.EmailCodeSendReqVO;
import org.example.backend.module.test.model.vo.LoginByCodeReqVO;
import org.example.backend.module.test.model.vo.RegisterReqVO;
import org.example.backend.module.test.model.vo.ResetPasswordReqVO;
import org.example.backend.module.test.model.vo.ResetPasswordRespVO;

public interface AdminAuthService {

    AdminUserDO authenticate(String username, String password);

    AuthLoginRespVO login(AuthLoginReqVO reqVO);

    /**
     * 用 refreshToken 换取新令牌（旧 refreshToken 一次性消费）。
     *
     * @param refreshToken 登录或上次刷新下发的刷新令牌
     * @return 新 accessToken + 新 refreshToken + 最新角色/权限
     */
    AuthLoginRespVO refreshToken(String refreshToken);

    /**
     * 发送邮箱验证码。
     */
    void sendCode(EmailCodeSendReqVO reqVO);

    /**
     * 邮箱验证码注册，成功后直接签发登录令牌。
     */
    AuthLoginRespVO register(RegisterReqVO reqVO);

    /**
     * 邮箱验证码登录。
     */
    AuthLoginRespVO loginByCode(LoginByCodeReqVO reqVO);

    /**
     * 邮箱验证码重置密码，并撤销该用户已有令牌。
     */
    ResetPasswordRespVO resetPassword(ResetPasswordReqVO reqVO);

}
