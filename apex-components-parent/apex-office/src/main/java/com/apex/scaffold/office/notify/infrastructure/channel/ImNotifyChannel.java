package com.apex.scaffold.office.notify.infrastructure.channel;

import com.apex.scaffold.office.domain.message.MarkdownMessageContent;
import com.apex.scaffold.office.domain.message.MessageTarget;
import com.apex.scaffold.office.domain.message.OfficeMessage;
import com.apex.scaffold.office.infrastructure.spi.OfficePlatformMessageClient;
import com.apex.scaffold.office.notify.domain.NotifyChannelPort;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Mono;

/**
 * 办公平台 IM 通知通道（FEISHU / DINGTALK / WECOM，方案 M4 / 批次 D1）。
 *
 * <p>复用 office 既有 {@link OfficePlatformMessageClient} SPI：按平台类型匹配客户端，
 * 组装 {@link OfficeMessage}（USER 目标 + Markdown 内容）后投递。通道标识由构造参数决定，
 * 同一实现以多个 Bean 注册（一个通道一个适配器）。
 *
 * <p>客户端未装配（未启用对应办公平台）时返回失败而非静默成功——调用方据此感知
 * 通道不可用；阻塞 SDK 调用由各平台客户端内部隔离到 remote 池。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
public class ImNotifyChannel implements NotifyChannelPort {

    private final String channel;
    private final ObjectProvider<OfficePlatformMessageClient> clients;

    /**
     * 构造 IM 通道适配器。
     *
     * @param channel 通道标识（FEISHU / DINGTALK / WECOM）
     * @param clients 办公平台消息客户端集合（可能为空）
     */
    public ImNotifyChannel(String channel, ObjectProvider<OfficePlatformMessageClient> clients) {
        this.channel = channel.toUpperCase(Locale.ROOT);
        this.clients = clients;
    }

    @Override
    public String channel() {
        return channel;
    }

    @Override
    public Mono<String> deliver(String target, String subject, String content, Map<String, Object> params) {
        OfficePlatformMessageClient client = clients.stream()
                .filter(candidate -> channel.equalsIgnoreCase(candidate.getPlatform()))
                .findFirst()
                .orElse(null);
        if (client == null) {
            return Mono.error(new IllegalStateException("办公平台通道未启用: " + channel));
        }
        OfficeMessage message = OfficeMessage.builder()
                .platform(com.apex.scaffold.office.domain.OfficePlatform.valueOf(channel))
                .target(MessageTarget.builder()
                        .targetType("USER")
                        .userIds(List.of(target))
                        .build())
                .content(MarkdownMessageContent.builder()
                        .markdown(content)
                        .title(subject)
                        .build())
                .build();
        return client.sendMessage(message);
    }
}
