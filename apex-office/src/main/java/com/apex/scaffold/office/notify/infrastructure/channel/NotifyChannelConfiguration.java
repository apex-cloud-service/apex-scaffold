package com.apex.scaffold.office.notify.infrastructure.channel;

import com.apex.scaffold.office.infrastructure.spi.OfficePlatformMessageClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
}
