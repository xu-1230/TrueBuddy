package org.example.backend.module.test.service.Impl;

import jakarta.annotation.Resource;
import org.example.backend.framework.common.biz.OAuth2AccessTokenCheckRespDTO;
import org.example.backend.framework.common.biz.OAuth2TokenCommonApi;
import org.example.backend.framework.common.util.object.BeanUtils;
import org.example.backend.module.test.model.OAuth2AccessTokenDO;
import org.example.backend.module.test.service.OAuth2TokenService;
import org.springframework.stereotype.Service;

@Service
public class OAuth2TokenApiImpl implements OAuth2TokenCommonApi {

    @Resource
    private OAuth2TokenService oauth2TokenService;


    @Override
    public OAuth2AccessTokenCheckRespDTO checkAccessToken(String accessToken) {
        OAuth2AccessTokenDO accessTokenDO = oauth2TokenService.checkAccessToken(accessToken);
        return BeanUtils.toBean(accessTokenDO, OAuth2AccessTokenCheckRespDTO.class);
    }


}