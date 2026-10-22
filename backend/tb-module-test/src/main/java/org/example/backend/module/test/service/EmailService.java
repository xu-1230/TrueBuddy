package org.example.backend.module.test.service;

import org.example.backend.module.test.model.enums.EmailCodeScene;

/**
 * 邮件发送服务。
 */
public interface EmailService {

    /**
     * 发送验证码邮件。
     *
     * @param email 收件邮箱（已规范化）
     * @param code  6 位数字验证码
     * @param scene 使用场景，决定标题与正文文案
     */
    void sendVerificationCode(String email, String code, EmailCodeScene scene);
}
