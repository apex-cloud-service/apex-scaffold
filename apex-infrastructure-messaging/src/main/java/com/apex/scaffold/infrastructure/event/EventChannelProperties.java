package com.apex.scaffold.infrastructure.event;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 领域事件通道配置（{@code apex.events.*}，方案 M3 / 批次 C8）。
 *
 * <p>与既有 {@code apex.events.outbox.enabled} 开关共存：通道模式决定"用哪条路"，
 * outbox 开关决定"是否持久化投递"。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "apex.events")
public class EventChannelProperties {

    /** 通道模式：auto（默认）/ local / remote */
    private String channel = "auto";

    /** 解析通道模式。 */
    public EventChannelMode channelMode() {
        return EventChannelMode.parse(channel);
    }
}
