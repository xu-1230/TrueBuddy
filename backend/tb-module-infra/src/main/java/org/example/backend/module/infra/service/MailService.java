package org.example.backend.module.infra.service;

/**
 * 通用邮件发送能力（与具体业务无关）。
 */
public interface MailService {

    /**
     * 发送 HTML 邮件。
     *
     * @param to      收件人
     * @param subject 主题
     * @param html    正文 HTML
     */
    void sendHtml(String to, String subject, String html);
}
