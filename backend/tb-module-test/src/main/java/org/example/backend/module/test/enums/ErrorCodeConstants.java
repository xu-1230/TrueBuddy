package org.example.backend.module.test.enums;

import org.example.backend.framework.common.exception.ErrorCode;

/**
 * 错误码枚举：模块 test
 */
public interface ErrorCodeConstants {

    // ========== 用户模块 1-002-000-000 ==========
    ErrorCode USER_NOT_EXISTS = new ErrorCode(1_002_000_000, "用户不存在");
    ErrorCode USER_USERNAME_EXISTS = new ErrorCode(1_002_000_001, "用户账号已经存在");
    ErrorCode USER_EMAIL_EXISTS = new ErrorCode(1_002_000_002, "该邮箱已注册");
    ErrorCode USER_EMAIL_NOT_EXISTS = new ErrorCode(1_002_000_003, "该邮箱未注册");

    // ========== 认证模块 1-002-002-000 ==========
    ErrorCode AUTH_LOGIN_BAD_CREDENTIALS = new ErrorCode(1_002_002_000, "登录失败，账号密码不正确");
    ErrorCode AUTH_LOGIN_USER_DISABLED = new ErrorCode(1_002_002_001, "登录失败，账号被禁用");

    // ========== 邮箱验证码 1-002-003-000 ==========
    ErrorCode EMAIL_CODE_NOT_FOUND = new ErrorCode(1_002_003_000, "验证码不存在，请先获取");
    ErrorCode EMAIL_CODE_EXPIRED = new ErrorCode(1_002_003_001, "验证码已过期，请重新获取");
    ErrorCode EMAIL_CODE_SCENE_INVALID = new ErrorCode(1_002_003_002, "验证码场景不合法");
    ErrorCode EMAIL_SEND_FAILURE = new ErrorCode(1_002_003_003, "验证码发送失败，请稍后重试");
    ErrorCode EMAIL_FORMAT_INVALID = new ErrorCode(1_002_003_004, "邮箱格式不正确");
    ErrorCode DEFAULT_ROLE_NOT_FOUND = new ErrorCode(1_002_003_005, "默认角色不存在，请先执行 rbac_seed.sql");
    ErrorCode EMAIL_CODE_ERROR = new ErrorCode(1_002_003_006, "验证码不正确");
}