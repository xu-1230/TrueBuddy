package org.example.backend.module.test.service.Impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.example.backend.framework.common.util.object.BeanUtils;
import org.example.backend.module.test.mapper.UserRoleMapper;
import org.example.backend.module.test.model.AdminUserDO;
import org.example.backend.module.test.model.OAuth2AccessTokenDO;
import org.example.backend.module.test.model.email.EmailVerificationData;
import org.example.backend.module.test.model.enums.EmailCodeScene;
import org.example.backend.module.test.model.vo.AuthLoginReqVO;
import org.example.backend.module.test.model.vo.AuthLoginRespVO;
import org.example.backend.module.test.model.vo.EmailCodeSendReqVO;
import org.example.backend.module.test.model.vo.LoginByCodeReqVO;
import org.example.backend.module.test.model.vo.RegisterReqVO;
import org.example.backend.module.test.model.vo.ResetPasswordReqVO;
import org.example.backend.module.test.model.vo.ResetPasswordRespVO;
import org.example.backend.module.test.model.vo.UserSaveReqVO;
import org.example.backend.module.test.service.AdminAuthService;
import org.example.backend.module.test.service.AdminUserService;
import org.example.backend.module.test.service.EmailCodeStore;
import org.example.backend.module.test.service.EmailService;
import org.example.backend.module.test.service.OAuth2TokenService;
import org.example.backend.module.test.service.PermissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import static org.example.backend.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static org.example.backend.framework.common.exception.util.ServiceExceptionUtil.exception;
import static org.example.backend.framework.common.exception.util.ServiceExceptionUtil.exception0;
import static org.example.backend.module.test.enums.ErrorCodeConstants.*;

@Service
public class AdminAuthServiceImpl implements AdminAuthService {

    /** 注册后的默认角色编码，见 rbac_seed.sql */
    private static final String DEFAULT_ROLE_CODE = "normal";

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    @Resource
    private AdminUserService userService;

    @Resource
    private OAuth2TokenService oauth2TokenService;

    @Resource
    private PermissionService permissionService;

    @Resource
    private EmailCodeStore emailCodeStore;

    @Resource
    private EmailService emailService;

    @Resource
    private UserRoleMapper userRoleMapper;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public AdminUserDO authenticate(String username, String password) {
        String account = StrUtil.trim(username);

        // 教师以邮箱为登录标识；管理员等旧账号仍可用 username 登录
        AdminUserDO user;
        if (StrUtil.contains(account, '@')) {
            user = userService.getUserByEmail(account.toLowerCase(Locale.ROOT));
        } else {
            user = userService.getUserByUsername(account);
        }
        if (user == null) {
//            createLoginLog(null, username, logTypeEnum, LoginResultEnum.BAD_CREDENTIALS);
            throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
        }
        if (!userService.isPasswordMatch(password, user.getPassword())) {
//            createLoginLog(user.getId(), username, logTypeEnum, LoginResultEnum.BAD_CREDENTIALS);
            throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
        }
        // 校验是否禁用
        validateUserStatus(user, account);
        return user;
    }

    @Override
    public AuthLoginRespVO login(AuthLoginReqVO reqVO) {

        // 使用账号密码，进行登录
        AdminUserDO user = authenticate(reqVO.getUsername(), reqVO.getPassword());

        return createTokenAfterLoginSuccess(user, reqVO.getUsername());
    }

    @Override
    public void sendCode(EmailCodeSendReqVO reqVO) {
        String email = normalizeEmail(reqVO.getEmail());
        EmailCodeScene scene = EmailCodeScene.from(reqVO.getScene());
        AdminUserDO user = userService.getUserByEmail(email);

        // 注册要求邮箱未占用；找回密码/验证码登录要求账号存在
        if (scene == EmailCodeScene.REGISTER) {
            if (user != null) {
                throw exception(USER_EMAIL_EXISTS);
            }
        } else {
            if (user == null) {
                throw exception(USER_EMAIL_NOT_EXISTS);
            }
            if (scene == EmailCodeScene.LOGIN) {
                validateUserStatus(user, user.getUsername());
            }
        }

        String code = generateCode();
        emailCodeStore.save(scene, email, code);
        try {
            emailService.sendVerificationCode(email, code, scene);
        } catch (RuntimeException ex) {
            // 发信失败时不能留下一个用户收不到的验证码
            emailCodeStore.remove(scene, email);
            throw ex;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthLoginRespVO register(RegisterReqVO reqVO) {
        String email = normalizeEmail(reqVO.getEmail());
        String password = reqVO.getPassword();
        validatePassword(password);
        verifyCode(EmailCodeScene.REGISTER, email, reqVO.getCode());

        UserSaveReqVO createReqVO = new UserSaveReqVO();
        createReqVO.setUsername(generateUsername());
        createReqVO.setNickname(buildNickname(email));
        createReqVO.setEmail(email);
        createReqVO.setPassword(password);
        Long userId = userService.createUser(createReqVO);

        // 绑定默认角色；角色种子未执行时回滚整个注册，避免出现无角色用户
        if (userRoleMapper.insertByUserIdAndRoleCode(userId, DEFAULT_ROLE_CODE) == 0) {
            throw exception(DEFAULT_ROLE_NOT_FOUND);
        }

        AdminUserDO user = userService.getUser(userId);
        AuthLoginRespVO respVO = createTokenAfterLoginSuccess(user, email);
        emailCodeStore.remove(EmailCodeScene.REGISTER, email);
        return respVO;
    }

    @Override
    public AuthLoginRespVO loginByCode(LoginByCodeReqVO reqVO) {
        String email = normalizeEmail(reqVO.getEmail());
        AdminUserDO user = userService.getUserByEmail(email);
        if (user == null) {
            throw exception(USER_EMAIL_NOT_EXISTS);
        }
        validateUserStatus(user, user.getUsername());
        verifyCode(EmailCodeScene.LOGIN, email, reqVO.getCode());

        AuthLoginRespVO respVO = createTokenAfterLoginSuccess(user, user.getUsername());
        emailCodeStore.remove(EmailCodeScene.LOGIN, email);
        return respVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResetPasswordRespVO resetPassword(ResetPasswordReqVO reqVO) {
        String email = normalizeEmail(reqVO.getEmail());
        validatePassword(reqVO.getPassword());
        AdminUserDO user = userService.getUserByEmail(email);
        if (user == null) {
            throw exception(USER_EMAIL_NOT_EXISTS);
        }
        verifyCode(EmailCodeScene.RESET, email, reqVO.getCode());

        userService.updatePassword(user.getId(), reqVO.getPassword());
        // 改密即撤销全部旧令牌，避免泄露的 token 继续可用
        oauth2TokenService.deleteByUserId(user.getId());
        emailCodeStore.remove(EmailCodeScene.RESET, email);
        return new ResetPasswordRespVO(email);
    }

    @Override
    public AuthLoginRespVO refreshToken(String refreshToken) {
        OAuth2AccessTokenDO accessTokenDO = oauth2TokenService.refreshAccessToken(refreshToken);
        return buildLoginResp(accessTokenDO);
    }

    private AuthLoginRespVO createTokenAfterLoginSuccess(AdminUserDO user, String username) {
        // 统一校验用户状态，避免短信、社交等登录方式遗漏
        validateUserStatus(user, username);

        OAuth2AccessTokenDO accessTokenDO = oauth2TokenService.createAccessToken(user.getId(), null);
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

    private void verifyCode(EmailCodeScene scene, String email, String code) {
        EmailVerificationData data = emailCodeStore.get(scene, email);
        if (data == null) {
            throw exception(EMAIL_CODE_NOT_FOUND);
        }
        if (data.getExpiresTime().isBefore(LocalDateTime.now())) {
            emailCodeStore.remove(scene, email);
            throw exception(EMAIL_CODE_EXPIRED);
        }
        if (!data.getCode().equals(StrUtil.trim(code))) {
            throw exception(EMAIL_CODE_ERROR);
        }
    }

    private String normalizeEmail(String email) {
        if (StrUtil.isBlank(email)) {
            throw exception(EMAIL_FORMAT_INVALID);
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 50 || !EMAIL_PATTERN.matcher(normalized).matches()) {
            throw exception(EMAIL_FORMAT_INVALID);
        }
        return normalized;
    }

    /**
     * 生成内部登录账号：教师不感知，邮箱才是对外身份；长度 18，满足 varchar(30)。
     */
    private String generateUsername() {
        for (int i = 0; i < 5; i++) {
            String candidate = "t_" + IdUtil.fastSimpleUUID().substring(0, 16);
            if (userService.getUserByUsername(candidate) == null) {
                return candidate;
            }
        }
        throw exception0(BAD_REQUEST.getCode(), "生成账号失败，请重试");
    }

    /** 教师昵称取邮箱 @ 前部分，超过 nickname 列宽时截断 */
    private String buildNickname(String email) {
        int at = email.indexOf('@');
        String localPart = at > 0 ? email.substring(0, at) : "";
        localPart = StrUtil.trim(localPart);
        if (StrUtil.isBlank(localPart)) {
            return "老师";
        }
        return StrUtil.subPre(localPart, 30);
    }

    /** 不限制密码格式，仅要求非空 */
    private void validatePassword(String password) {
        if (StrUtil.isBlank(password)) {
            throw exception0(BAD_REQUEST.getCode(), "密码不能为空");
        }
    }

    private String generateCode() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }
}
