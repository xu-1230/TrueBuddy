package org.example.backend.module.test.service;

import org.example.backend.framework.common.biz.OAuth2AccessTokenCheckRespDTO;
import org.example.backend.module.test.model.OAuth2AccessTokenDO;

import java.util.List;

public interface OAuth2TokenService {

    OAuth2AccessTokenDO createAccessToken(Long userId, List<String> scopes);

    OAuth2AccessTokenDO getAccessToken(String accessToken);

    OAuth2AccessTokenDO checkAccessToken(String accessToken);
}
