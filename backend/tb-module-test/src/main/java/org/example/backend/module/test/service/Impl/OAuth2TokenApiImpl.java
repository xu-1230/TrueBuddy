package org.example.backend.module.test.service.Impl;

import jakarta.annotation.Resource;
import org.example.backend.framework.common.biz.OAuth2AccessTokenCheckRespDTO;
import org.example.backend.framework.common.biz.OAuth2TokenCommonApi;
import org.example.backend.framework.common.util.object.BeanUtils;
import org.example.backend.module.test.model.OAuth2AccessTokenDO;
import org.example.backend.module.test.service.OAuth2TokenService;
import org.example.backend.module.test.service.PermissionService;
import org.springframework.stereotype.Service;

@Service
public class OAuth2TokenApiImpl implements OAuth2TokenCommonApi {

    @Resource
    private OAuth2TokenService oauth2TokenService;

    @Resource
    private PermissionService permissionService;

    /**
     * Token 校验结果里带上角色与权限：过滤器据此构建 Authentication 的 authorities。
     *
     * <p>权限在此处实时解析（而非快照进 Token），角色调整后下一次请求即生效。
     */
    @Override
    public OAuth2AccessTokenCheckRespDTO checkAccessToken(String accessToken) {
        OAuth2AccessTokenDO accessTokenDO = oauth2TokenService.checkAccessToken(accessToken);
        OAuth2AccessTokenCheckRespDTO respDTO = BeanUtils.toBean(accessTokenDO, OAuth2AccessTokenCheckRespDTO.class);
        respDTO.setRoles(permissionService.getRoleCodes(accessTokenDO.getUserId()));
        respDTO.setPermissions(permissionService.getPermissions(accessTokenDO.getUserId()));
        return respDTO;
    }


}