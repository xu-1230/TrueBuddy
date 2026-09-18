package org.example.backend.module.test.service.Impl;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.IdUtil;
import jakarta.annotation.Resource;
import org.example.backend.framework.common.biz.OAuth2AccessTokenCheckRespDTO;
import org.example.backend.framework.common.exception.enums.GlobalErrorCodeConstants;
import org.example.backend.framework.common.util.data.DateUtils;
import org.example.backend.framework.common.util.object.BeanUtils;
import org.example.backend.module.test.mapper.OAuth2AccessTokenMapper;
import org.example.backend.module.test.mapper.OAuth2RefreshTokenMapper;
import org.example.backend.module.test.model.AdminUserDO;
import org.example.backend.module.test.model.OAuth2AccessTokenDO;
import org.example.backend.module.test.model.OAuth2RefreshTokenDO;
import org.example.backend.module.test.service.AdminUserService;
import org.example.backend.module.test.service.OAuth2TokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.example.backend.framework.common.exception.util.ServiceExceptionUtil.exception0;

@Service
public class OAuth2TokenServiceImpl implements OAuth2TokenService {

    /** 访问令牌有效时长（秒），默认 2 小时 */
    @Value("${test.oauth2.access-token-validity-seconds:7200}")
    private Integer accessTokenValiditySeconds;

    /** 刷新令牌有效时长（秒），默认 7 天 */
    @Value("${test.oauth2.refresh-token-validity-seconds:604800}")
    private Integer refreshTokenValiditySeconds;

    @Resource
    private OAuth2AccessTokenMapper oauth2AccessTokenMapper;

    @Resource
    private OAuth2RefreshTokenMapper oauth2RefreshTokenMapper;

    @Resource
    @Lazy // 懒加载，避免循环依赖
    private AdminUserService adminUserService;

    @Override
    public OAuth2AccessTokenDO createAccessToken(Long userId, Integer userType, List<String> scopes) {
        // 创建刷新令牌
        OAuth2RefreshTokenDO refreshTokenDO = createOAuth2RefreshToken(userId, userType, scopes);
        // 创建访问令牌
        return createOAuth2AccessToken(refreshTokenDO);
    }

    @Override
    public OAuth2AccessTokenDO getAccessToken(String accessToken) {
        OAuth2AccessTokenDO accessTokenDO = oauth2AccessTokenMapper.selectByAccessToken(accessToken);
        if (accessTokenDO == null) {
            OAuth2RefreshTokenDO refreshTokenDO = oauth2RefreshTokenMapper.selectByRefreshToken(accessToken);
            if (refreshTokenDO != null && !DateUtils.isExpired(refreshTokenDO.getExpiresTime())) {
                accessTokenDO = convertToAccessToken(refreshTokenDO);
            }
        }

//        // 如果在 MySQL 存在，则往 Redis 中写入
//        if (accessTokenDO != null && !DateUtils.isExpired(accessTokenDO.getExpiresTime())) {
//            oauth2AccessTokenRedisDAO.set(accessTokenDO);
//        }
        return accessTokenDO;
    }

    private OAuth2AccessTokenDO convertToAccessToken(OAuth2RefreshTokenDO refreshTokenDO) {
        OAuth2AccessTokenDO accessTokenDO = BeanUtils.toBean(refreshTokenDO, OAuth2AccessTokenDO.class)
                .setAccessToken(refreshTokenDO.getRefreshToken());
        return accessTokenDO;
    }

    @Override
    public OAuth2AccessTokenDO checkAccessToken(String accessToken) {
        OAuth2AccessTokenDO accessTokenDO = getAccessToken(accessToken);
        if (accessTokenDO == null) {
            throw exception0(GlobalErrorCodeConstants.UNAUTHORIZED.getCode(), "访问令牌不存在");
        }
        if (DateUtils.isExpired(accessTokenDO.getExpiresTime())) {
            throw exception0(GlobalErrorCodeConstants.UNAUTHORIZED.getCode(), "访问令牌已过期");
        }
        return accessTokenDO;
    }

    private OAuth2AccessTokenDO createOAuth2AccessToken(OAuth2RefreshTokenDO refreshTokenDO) {
        OAuth2AccessTokenDO accessTokenDO = new OAuth2AccessTokenDO().setAccessToken(generateAccessToken())
                .setUserId(refreshTokenDO.getUserId()).setUserType(refreshTokenDO.getUserType())
                .setScopes(refreshTokenDO.getScopes())
                .setUserInfo(buildUserInfo(refreshTokenDO.getUserId(), refreshTokenDO.getUserType()))
                .setRefreshToken(refreshTokenDO.getRefreshToken())
                .setExpiresTime(LocalDateTime.now().plusSeconds(accessTokenValiditySeconds));

        oauth2AccessTokenMapper.insert(accessTokenDO);
        // 记录到 Redis 中
//        oauth2AccessTokenRedisDAO.set(accessTokenDO);
        return accessTokenDO;
    }

    private OAuth2RefreshTokenDO createOAuth2RefreshToken(Long userId, Integer userType, List<String> scopes) {
        OAuth2RefreshTokenDO refreshToken = new OAuth2RefreshTokenDO().setRefreshToken(generateRefreshToken())
                .setUserId(userId).setUserType(userType)
                .setScopes(scopes)
                .setExpiresTime(LocalDateTime.now().plusSeconds(refreshTokenValiditySeconds));

        oauth2RefreshTokenMapper.insert(refreshToken);
        return refreshToken;
    }

    private Map<String, String> buildUserInfo(Long userId, Integer userType) {
        if (userId == null || userId <= 0) {
            return Collections.emptyMap();
        }
        AdminUserDO user = adminUserService.getUser(userId);
        return MapUtil.builder("nickname", user.getNickname()).build();
    }

    private static String generateAccessToken() {
        return IdUtil.fastSimpleUUID();
    }

    private static String generateRefreshToken() {
        return IdUtil.fastSimpleUUID();
    }

}