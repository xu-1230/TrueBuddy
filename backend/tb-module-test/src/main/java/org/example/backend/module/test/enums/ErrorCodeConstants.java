package org.example.backend.module.test.enums;

import org.example.backend.framework.common.exception.ErrorCode;

/**
 * 错误码枚举：模块 test
 */
public interface ErrorCodeConstants {

    // ========== 用户模块 1-002-000-000 ==========
    ErrorCode USER_NOT_EXISTS = new ErrorCode(1_002_000_000, "用户不存在");
    ErrorCode USER_USERNAME_EXISTS = new ErrorCode(1_002_000_001, "用户账号已经存在");
    ErrorCode USER_EMAIL_EXISTS = new ErrorCode(1_002_000_002, "邮箱已经存在");

    // ========== 认证模块 1-002-002-000 ==========
    ErrorCode AUTH_LOGIN_BAD_CREDENTIALS = new ErrorCode(1_002_002_000, "登录失败，账号密码不正确");
    ErrorCode AUTH_LOGIN_USER_DISABLED = new ErrorCode(1_002_002_001, "登录失败，账号被禁用");
    ErrorCode AUTH_PERMISSION_DENIED = new ErrorCode(1_002_002_002, "当前用户无该身份权限");
}