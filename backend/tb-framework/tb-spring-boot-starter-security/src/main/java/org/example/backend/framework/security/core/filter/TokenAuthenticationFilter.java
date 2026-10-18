package org.example.backend.framework.security.core.filter;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.example.backend.framework.common.biz.OAuth2AccessTokenCheckRespDTO;
import org.example.backend.framework.common.biz.OAuth2TokenCommonApi;
import org.example.backend.framework.common.exception.ServiceException;
import org.example.backend.framework.security.LoginUser;
import org.example.backend.framework.security.config.SecurityProperties;
import org.springframework.web.filter.OncePerRequestFilter;
import org.example.backend.framework.security.util.SecurityFrameworkUtils;
import java.io.IOException;
import jakarta.servlet.http.HttpServletRequest;


public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private final SecurityProperties securityProperties;
    private final OAuth2TokenCommonApi oauth2TokenApi;


    public TokenAuthenticationFilter(SecurityProperties securityProperties, OAuth2TokenCommonApi oauth2TokenApi) {
        this.securityProperties = securityProperties;
        this.oauth2TokenApi = oauth2TokenApi;
    }


    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = SecurityFrameworkUtils.obtainAuthorization(request, securityProperties.getTokenHeader(), securityProperties.getTokenParameter());
        if (StrUtil.isNotEmpty(token)){
            LoginUser loginUser = buildLoginUserByToken(token);
            if (loginUser != null) {
                SecurityFrameworkUtils.setLoginUser(loginUser, request);
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 校验 Token 并构建登录用户；角色与权限由 Token 校验结果一并带回。
     */
    private LoginUser buildLoginUserByToken(String token) {
        try {
            OAuth2AccessTokenCheckRespDTO accessToken = oauth2TokenApi.checkAccessToken(token);
            if (accessToken == null) {
                return null;
            }
            return new LoginUser().setId(accessToken.getUserId())
                    .setRoles(accessToken.getRoles())
                    .setPermissions(accessToken.getPermissions())
                    .setInfo(accessToken.getUserInfo()) // 额外的用户信息
                    .setScopes(accessToken.getScopes())
                    .setExpiresTime(accessToken.getExpiresTime());
        } catch (ServiceException serviceException) {
            // 校验 Token 不通过时，考虑到一些接口是无需登录的，所以直接返回 null 即可
            return null;
        }
    }


}
