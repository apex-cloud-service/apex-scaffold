package com.apex.scaffold.office.notify.infrastructure.channel;

import com.apex.scaffold.office.infrastructure.spi.OfficePlatformMessageClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 通知通道装配（方案 M4 / 批次 D1）。
 *
 * <p>注册三个办公平台 IM 通道（FEISHU / DINGTALK / WECOM），各自按平台类型匹配
 * {@link OfficePlatformMessageClient}；站内信通道由 {@link InboxNotifyChannel} 组件提供。
 * 短信/邮件通道由扩展模块以 {@code NotifyChannelPort} 实现接入（本模块不直接依赖
 * {@code apex-infrastructure-integration}，保持 office 依赖边界）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Configuration(proxyBeanMethods = false)
public class NotifyChannelConfiguration {

    /**
     * 飞书通道。
     *
     * @param clients 平台消息客户端集合
     * @return 飞书通知通道
     */
    @Bean
    @ConditionalOnMissingBean(name = "feishuNotifyChannel")
    public ImNotifyChannel feishuNotifyChannel(ObjectProvider<OfficePlatformMessageClient> clients) {
        return new ImNotifyChannel("FEISHU", clients);
    }

    /**
     * 钉钉通道。
     *
     * @param clients 平台消息客户端集合
     * @return 钉钉通知通道
     */
    @Bean
    @ConditionalOnMissingBean(name = "dingtalkNotifyChannel")
    public ImNotifyChannel dingtalkNotifyChannel(ObjectProvider<OfficePlatformMessageClient> clients) {
        return new ImNotifyChannel("DINGTALK", clients);
    }

    /**
     * 企业微信通道。
     *
     * @param clients 平台消息客户端集合
     * @return 企业微信通知通道
     */
    @Bean
    @ConditionalOnMissingBean(name = "wecomNotifyChannel")
    public ImNotifyChannel wecomNotifyChannel(ObjectProvider<OfficePlatformMessageClient> clients) {
        return new ImNotifyChannel("WECOM", clients);
    }

    /**
     * 短信通道（可选）：仅在 {@code apex.notify.sms.enabled=true} 时注册。
     *
     * <p>office 不直接依赖 {@code apex-infrastructure-integration}（保持模块边界）：
     * 需要短信能力的部署在装配层提供 {@code BiFunction<String, String, Mono<String>>}
     * 发送函数 Bean（入参为手机号与内容，返回发送结果）即可。
     *
     * @param sender 短信发送函数（可能不存在）
     * @return 短信通知通道
     * @throws IllegalStateException 开关已开启但装配层未提供短信发送函数 Bean
     */
    @Bean
    @ConditionalOnMissingBean(name = "smsNotifyChannel")
    @ConditionalOnProperty(prefix = "apex.notify.sms", name = "enabled", havingValue = "true")
    public SmsNotifyChannel smsNotifyChannel(
            ObjectProvider<java.util.function.BiFunction<String, String, reactor.core.publisher.Mono<String>>> sender) {
        java.util.function.BiFunction<String, String, reactor.core.publisher.Mono<String>> fn = sender.getIfAvailable();
        if (fn == null) {
            throw new IllegalStateException(
                    "apex.notify.sms.enabled=true 但未提供短信发送函数 Bean" + "（BiFunction<String,String,Mono<String>>，如创蓝适配器）");
        }
        return new SmsNotifyChannel(fn);
    }

    /**
     * 邮件通道（可选）：仅在 {@code apex.notify.email.enabled=true} 时注册。
     *
     * <p>邮件发送能力由装配层以 {@link EmailNotifyChannel.MailSender} Bean 提供，
     * 本模块不直接依赖具体邮件实现。
     *
     * @param sender 邮件发送函数（可能不存在）
     * @return 邮件通知通道
     * @throws IllegalStateException 开关已开启但装配层未提供邮件发送函数 Bean
     */
    @Bean
    @ConditionalOnMissingBean(name = "emailNotifyChannel")
    @ConditionalOnProperty(prefix = "apex.notify.email", name = "enabled", havingValue = "true")
    public EmailNotifyChannel emailNotifyChannel(ObjectProvider<EmailNotifyChannel.MailSender> sender) {
        EmailNotifyChannel.MailSender fn = sender.getIfAvailable();
        if (fn == null) {
            throw new IllegalStateException("apex.notify.email.enabled=true 但未提供邮件发送函数 Bean"
                    + "（EmailNotifyChannel.MailSender，如 MailService 适配器）");
        }
        return new EmailNotifyChannel(fn);
    }
}
