package org.example.backend.module.test.model.email;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.example.backend.module.test.model.enums.EmailCodeScene;

import java.time.LocalDateTime;

/**
 * 内存中的邮箱验证码记录。
 */
@Data
@AllArgsConstructor
public class EmailVerificationData {

    private String code;

    private EmailCodeScene scene;

    private LocalDateTime expiresTime;
}
