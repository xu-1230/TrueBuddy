package org.example.backend.module.test.model.vo;

import lombok.Data;

/**
 * 邮箱验证码重置密码请求。
 */
@Data
public class ResetPasswordReqVO {

    private String email;

    private String code;

    private String password;
}
