package org.example.backend.module.test.model.vo;

import lombok.Data;

/**
 * 发送邮箱验证码请求。
 */
@Data
public class EmailCodeSendReqVO {

    private String email;

    /** register / reset / login */
    private String scene;
}
