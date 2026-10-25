package org.example.backend.module.test.service.Impl;

import jakarta.annotation.Resource;
import org.example.backend.framework.common.util.object.BeanUtils;
import org.example.backend.module.test.model.AdminUserDO;
import org.example.backend.module.test.model.OAuth2AccessTokenDO;
import org.example.backend.module.test.model.vo.AuthLoginReqVO;
import org.example.backend.module.test.model.vo.AuthLoginRespVO;
import org.example.backend.module.test.service.AdminAuthService;
import org.example.backend.module.test.service.AdminUserService;
import org.example.backend.module.test.service.OAuth2TokenService;
import org.example.backend.module.test.service.PermissionService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

import static org.example.backend.framework.common.exception.util.ServiceExceptionUtil.exception;
import static org.example.backend.module.test.enums.ErrorCodeConstants.*;

@Service
public class AdminAuthServiceImpl implements AdminAuthService {

    @Resource
    private AdminUserService userService;

    @Resource
    private OAuth2TokenService oauth2TokenService;

    @Resource
    private PermissionService permissionService;

    @Override
    public AdminUserDO authenticate(String username, String password) {

        // 校验账号是否存在
        AdminUserDO user = userService.getUserByUsername(username);
        if (user == null) {
//            createLoginLog(null, username, logTypeEnum, LoginResultEnum.BAD_CREDENTIALS);
            throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
        }
        if (!userService.isPasswordMatch(password, user.getPassword())) {
//            createLoginLog(user.getId(), username, logTypeEnum, LoginResultEnum.BAD_CREDENTIALS);
            throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
        }
        // 校验是否禁用
        validateUserStatus(user, username);
        return user;
    }

    @Override
    public AuthLoginRespVO login(AuthLoginReqVO reqVO) {

        // 使用账号密码，进行登录
        AdminUserDO user = authenticate(reqVO.getUsername(), reqVO.getPassword());

        return createTokenAfterLoginSuccess(user, reqVO.getUsername());
    }

    private AuthLoginRespVO createTokenAfterLoginSuccess(AdminUserDO user, String username) {
        // 统一校验用户状态，避免短信、社交等登录方式遗漏
        validateUserStatus(user, username);

        OAuth2AccessTokenDO accessTokenDO = oauth2TokenService.createAccessToken(user.getId(), null);
        return buildLoginResp(accessTokenDO);
    }

    @Override
    public AuthLoginRespVO refreshToken(String refreshToken) {
        OAuth2AccessTokenDO accessTokenDO = oauth2TokenService.refreshAccessToken(refreshToken);
        return buildLoginResp(accessTokenDO);
    }

    /** 登录与刷新共用：把 Token 转成登录响应，并回填最新角色与权限 */
    private AuthLoginRespVO buildLoginResp(OAuth2AccessTokenDO accessTokenDO) {
        AuthLoginRespVO respVO = BeanUtils.toBean(accessTokenDO, AuthLoginRespVO.class);
        List<String> roles = permissionService.getRoleCodes(accessTokenDO.getUserId());
        respVO.setRoles(roles);
        respVO.setPermissions(permissionService.getPermissions(accessTokenDO.getUserId()));
        return respVO;
    }

    private void validateUserStatus(AdminUserDO user, String username) {
        if (Objects.equals(user.getStatus(), 1)) { // 状态：1 = 禁用
            throw exception(AUTH_LOGIN_USER_DISABLED);
        }
    }
}