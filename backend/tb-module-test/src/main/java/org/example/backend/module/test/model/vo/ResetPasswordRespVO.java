package org.example.backend.module.test.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 重置密码响应：返回账号，供登录页预填。
 */
@Data
@AllArgsConstructor
public class ResetPasswordRespVO {

    private String username;
}
