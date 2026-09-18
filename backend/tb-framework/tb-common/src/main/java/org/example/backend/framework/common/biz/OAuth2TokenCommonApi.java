package org.example.backend.framework.common.biz;

public interface OAuth2TokenCommonApi {

    OAuth2AccessTokenCheckRespDTO checkAccessToken(String accessToken);

}