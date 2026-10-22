package org.example.backend.module.test.model.vo;

import lombok.Data;

/**
 * 邮箱验证码注册请求。
 */
@Data
public class RegisterReqVO {

    private String email;

    private String password;

    private String code;
}
