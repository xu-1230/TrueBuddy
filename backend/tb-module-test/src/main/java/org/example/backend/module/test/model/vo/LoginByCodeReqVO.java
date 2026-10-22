package org.example.backend.module.test.model.vo;

import lombok.Data;

/**
 * 邮箱验证码登录请求。
 */
@Data
public class LoginByCodeReqVO {

    private String email;

    private String code;
}
