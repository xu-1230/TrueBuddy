package org.example.backend.module.test.service;

import org.example.backend.module.test.model.AdminUserDO;
import org.example.backend.module.test.model.vo.AuthLoginReqVO;
import org.example.backend.module.test.model.vo.AuthLoginRespVO;

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


}
