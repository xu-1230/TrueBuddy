package org.example.backend.module.test.service;

import org.example.backend.framework.common.biz.OAuth2AccessTokenCheckRespDTO;
import org.example.backend.module.test.model.OAuth2AccessTokenDO;

import java.util.List;

public interface OAuth2TokenService {

    OAuth2AccessTokenDO createAccessToken(Long userId, List<String> scopes);

    /**
     * 用刷新令牌换取新的访问令牌 + 新的刷新令牌（旧刷新令牌一次性消费）。
     *
     * @param refreshToken 刷新令牌
     * @return 新的访问令牌（含新 refreshToken）
     */
    OAuth2AccessTokenDO refreshAccessToken(String refreshToken);

    OAuth2AccessTokenDO getAccessToken(String accessToken);

    OAuth2AccessTokenDO checkAccessToken(String accessToken);
}
