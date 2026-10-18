package org.example.backend.module.test.service.Impl;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.IdUtil;
import jakarta.annotation.Resource;
import org.example.backend.framework.common.exception.enums.GlobalErrorCodeConstants;
import org.example.backend.framework.common.util.data.DateUtils;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
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
    public OAuth2AccessTokenDO createAccessToken(Long userId, List<String> scopes) {
        // 创建刷新令牌
        OAuth2RefreshTokenDO refreshTokenDO = createOAuth2RefreshToken(userId, scopes);
        // 创建访问令牌
        return createOAuth2AccessToken(refreshTokenDO);
    }

    /**
     * 刷新令牌换取新令牌：旧 refreshToken 一次性消费，新 refreshToken 同时下发。
     *
     * <p>删除旧 token 用影响行数做并发闸门：两个请求同时拿同一个 refreshToken 来时，
     * 只有一个能删除成功，另一个会得到 0 行并被拒绝。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OAuth2AccessTokenDO refreshAccessToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw exception0(GlobalErrorCodeConstants.UNAUTHORIZED.getCode(), "刷新令牌不能为空");
        }
        OAuth2RefreshTokenDO existing = oauth2RefreshTokenMapper.selectByRefreshToken(refreshToken.trim());
        if (existing == null) {
            throw exception0(GlobalErrorCodeConstants.UNAUTHORIZED.getCode(), "刷新令牌不存在或已失效");
        }
        if (DateUtils.isExpired(existing.getExpiresTime())) {
            oauth2RefreshTokenMapper.deleteById(existing.getId());
            throw exception0(GlobalErrorCodeConstants.UNAUTHORIZED.getCode(), "刷新令牌已过期，请重新登录");
        }

        int consumed = oauth2RefreshTokenMapper.deleteById(existing.getId());
        if (consumed == 0) {
            throw exception0(GlobalErrorCodeConstants.UNAUTHORIZED.getCode(), "刷新令牌已被使用，请重新登录");
        }

        // 轮换：旧刷新令牌已删除，签发新的 refreshToken + accessToken
        OAuth2RefreshTokenDO rotated = createOAuth2RefreshToken(existing.getUserId(), existing.getScopes());
        return createOAuth2AccessToken(rotated);
    }

    /** 只认访问令牌表；refreshToken 不再允许直接作为 Bearer 使用 */
    @Override
    public OAuth2AccessTokenDO getAccessToken(String accessToken) {
        return oauth2AccessTokenMapper.selectByAccessToken(accessToken);
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
                .setUserId(refreshTokenDO.getUserId())
                .setScopes(refreshTokenDO.getScopes())
                .setUserInfo(buildUserInfo(refreshTokenDO.getUserId()))
                .setRefreshToken(refreshTokenDO.getRefreshToken())
                .setExpiresTime(LocalDateTime.now().plusSeconds(accessTokenValiditySeconds));

        oauth2AccessTokenMapper.insert(accessTokenDO);
        // 记录到 Redis 中
//        oauth2AccessTokenRedisDAO.set(accessTokenDO);
        return accessTokenDO;
    }

    private OAuth2RefreshTokenDO createOAuth2RefreshToken(Long userId, List<String> scopes) {
        OAuth2RefreshTokenDO refreshToken = new OAuth2RefreshTokenDO().setRefreshToken(generateRefreshToken())
                .setUserId(userId)
                .setScopes(scopes)
                .setExpiresTime(LocalDateTime.now().plusSeconds(refreshTokenValiditySeconds));

        oauth2RefreshTokenMapper.insert(refreshToken);
        return refreshToken;
    }

    private Map<String, String> buildUserInfo(Long userId) {
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