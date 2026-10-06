package com.apex.scaffold.infrastructure.mail;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * 邮件发送服务（C7 邮件发送）。
 *
 * <p>基于 {@link JavaMailSender} 提供文本/HTML 邮件发送能力，支持
 * 简单文本邮件与富文本（HTML）邮件，供业务场景（欢迎、重置密码、
 * 告警通知等）复用。</p>
 *
 * <p><b>配置要求</b>：{@code spring.mail.*} 配置已就绪（见 application.yml），
 * 生产环境通过环境变量注入真实 SMTP 账号；未配置时发送调用将抛异常，
 * 业务方可按需捕获降级。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 * @see JavaMailSender
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class MailService {

    /** JavaMail 发送器（由 Spring Boot 自动配置提供） */
    private final JavaMailSender mailSender;

    /**
     * 发送简单文本邮件。
     *
     * @param to      收件人地址
     * @param subject 主题
     * @param text    正文（纯文本）
     */
    public void sendSimple(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
        log.debug("简单邮件已发送: to={}, subject={}", to, subject);
    }

    /**
     * 发送 HTML 富文本邮件。
     *
     * @param to      收件人地址
     * @param subject 主题
     * @param html    正文（HTML）
     * @throws jakarta.mail.MessagingException 邮件构造失败时抛出
     */
    public void sendHtml(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.debug("HTML 邮件已发送: to={}, subject={}", to, subject);
        } catch (jakarta.mail.MessagingException e) {
            throw new IllegalStateException("HTML 邮件构造失败: " + e.getMessage(), e);
        }
    }
}
