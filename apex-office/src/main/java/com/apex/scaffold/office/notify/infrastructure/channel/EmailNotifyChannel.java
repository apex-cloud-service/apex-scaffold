package com.apex.scaffold.office.notify.infrastructure.channel;

import com.apex.scaffold.office.notify.domain.NotifyChannelPort;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * 邮件通知通道（方案 M4 / 批次 D1）。
 *
 * <p>与 {@link SmsNotifyChannel} 同构：持有"发送函数"（收件人, 主题, 正文 → 结果），
 * 由邮件适配器注册。消费方引入 {@code apex-infrastructure-integration} 的
 * {@code MailService} 后以 Bean 形式提供函数即可启用。
 *
 * <p>正文以 HTML 发送（通知模板常含富文本）；纯文本通知由模板自身控制。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
public class EmailNotifyChannel implements NotifyChannelPort {

    /** 通道标识（与 off_message_delivery 白名单一致）。 */
    public static final String CHANNEL = "EMAIL";

    /** 邮件发送函数：(收件人, 主题, 正文) → 结果（无消息 ID 时返回空串）。 */
    @FunctionalInterface
    public interface MailSender {
        /**
         * 发送邮件。
         *
         * @param to      收件人地址
         * @param subject 主题
         * @param body    正文（HTML）
         * @return 结果信号
         */
        Mono<String> send(String to, String subject, String body);
    }

    private final MailSender sender;

    /**
     * 构造邮件通道。
     *
     * @param sender 邮件发送函数
     */
    public EmailNotifyChannel(MailSender sender) {
        this.sender = sender;
    }

    @Override
    public String channel() {
        return CHANNEL;
    }

    @Override
    public Mono<String> deliver(String target, String subject, String content, Map<String, Object> params) {
        if (target == null || target.isBlank()) {
            return Mono.error(new IllegalArgumentException("邮件收件人不能为空"));
        }
        String safeSubject = subject == null || subject.isBlank() ? "通知" : subject;
        log.debug("邮件通道投递: to={}, subject={}", maskEmail(target), safeSubject);
        return sender.send(target, safeSubject, content == null ? "" : content);
    }

    /** 邮箱脱敏（保留首字符与域名）。 */
    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
