package org.example.backend.module.infra.service.Impl;

import jakarta.annotation.Resource;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.module.infra.service.MailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import static org.example.backend.framework.common.exception.util.ServiceExceptionUtil.exception;
import static org.example.backend.module.infra.enums.ErrorCodeConstants.MAIL_SEND_FAILURE;

/**
 * 基于 Spring Mail 的通用发信实现。
 */
@Slf4j
@Service
public class MailServiceImpl implements MailService {

    @Resource
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String from;

    @Override
    public void sendHtml(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from, "TrueBuddy");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (Exception ex) {
            log.error("[sendHtml] 邮件发送失败 to={} subject={}", to, subject, ex);
            throw exception(MAIL_SEND_FAILURE);
        }
    }
}
