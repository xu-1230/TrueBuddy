package org.example.backend.module.test.service.Impl;

import jakarta.annotation.Resource;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.module.test.model.enums.EmailCodeScene;
import org.example.backend.module.test.service.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import static org.example.backend.framework.common.exception.util.ServiceExceptionUtil.exception;
import static org.example.backend.module.test.enums.ErrorCodeConstants.EMAIL_SEND_FAILURE;

/**
 * QQ 邮箱 SMTP 发信实现。
 *
 * <p>同步发送：失败直接抛业务异常，调用方负责把已写入内存的验证码删掉。
 */
@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    @Resource
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String from;

    @Override
    public void sendVerificationCode(String email, String code, EmailCodeScene scene) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from, "TrueBuddy");
            helper.setTo(email);
            helper.setSubject(subject(scene));
            helper.setText(content(code, scene), true);
            mailSender.send(message);
        } catch (Exception ex) {
            log.error("[sendVerificationCode] 邮件发送失败 email={} scene={}", email, scene.getValue(), ex);
            throw exception(EMAIL_SEND_FAILURE);
        }
    }

    private String subject(EmailCodeScene scene) {
        return switch (scene) {
            case REGISTER -> "【TrueBuddy】注册验证码";
            case RESET -> "【TrueBuddy】重置密码验证码";
            case LOGIN -> "【TrueBuddy】登录验证码";
        };
    }

    private String content(String code, EmailCodeScene scene) {
        String action = switch (scene) {
            case REGISTER -> "注册 TrueBuddy 账号";
            case RESET -> "重置 TrueBuddy 登录密码";
            case LOGIN -> "使用邮箱验证码登录 TrueBuddy";
        };
        return """
                <div style="margin:0;padding:32px;background:#f6f7fb;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Arial,sans-serif;color:#1f2937;">
                  <div style="max-width:520px;margin:0 auto;padding:32px;border-radius:16px;background:#ffffff;border:1px solid #e5e7eb;">
                    <p style="margin:0 0 8px;font-size:20px;font-weight:700;color:#111827;">TrueBuddy</p>
                    <p style="margin:0 0 24px;font-size:14px;color:#6b7280;">教学智能体工作台</p>
                    <p style="margin:0 0 12px;font-size:15px;line-height:1.7;">你正在%s，验证码为：</p>
                    <p style="margin:0 0 16px;font-size:32px;font-weight:700;letter-spacing:8px;color:#2563eb;">%s</p>
                    <p style="margin:0;font-size:14px;line-height:1.7;color:#6b7280;">验证码 5 分钟内有效。若非本人操作，请忽略本邮件。</p>
                  </div>
                </div>
                """.formatted(action, code);
    }
}
