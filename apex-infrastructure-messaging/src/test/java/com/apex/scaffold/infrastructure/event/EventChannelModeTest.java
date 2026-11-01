package com.apex.scaffold.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 事件通道模式解析测试（方案 M3 / 批次 C8）。
 *
 * <p>覆盖：配置解析（大小写/非法值回退）、AUTO 按部署形态解析、LOCAL/REMOTE 强制语义。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("事件通道模式测试")
class EventChannelModeTest {

    @Test
    @DisplayName("配置解析：大小写不敏感，空/非法回退 AUTO")
    void parsesConfigValues() {
        assertThat(EventChannelMode.parse("local")).isEqualTo(EventChannelMode.LOCAL);
        assertThat(EventChannelMode.parse("REMOTE")).isEqualTo(EventChannelMode.REMOTE);
        assertThat(EventChannelMode.parse(" Auto ")).isEqualTo(EventChannelMode.AUTO);
        assertThat(EventChannelMode.parse(null)).isEqualTo(EventChannelMode.AUTO);
        assertThat(EventChannelMode.parse("")).isEqualTo(EventChannelMode.AUTO);
        assertThat(EventChannelMode.parse("broker-typo")).isEqualTo(EventChannelMode.AUTO);
    }

    @Test
    @DisplayName("AUTO 按部署形态解析：有 MQ 能力走 REMOTE，否则 LOCAL")
    void autoResolvesByDeployment() {
        assertThat(EventChannelMode.AUTO.resolve(true)).isEqualTo(EventChannelMode.REMOTE);
        assertThat(EventChannelMode.AUTO.resolve(false)).isEqualTo(EventChannelMode.LOCAL);
    }

    @Test
    @DisplayName("显式 LOCAL/REMOTE 强制生效，忽略环境能力")
    void explicitModesOverride() {
        assertThat(EventChannelMode.LOCAL.resolve(true)).isEqualTo(EventChannelMode.LOCAL);
        assertThat(EventChannelMode.REMOTE.resolve(false)).isEqualTo(EventChannelMode.REMOTE);
    }

    @Test
    @DisplayName("配置属性解析通道模式")
    void propertiesResolveMode() {
        EventChannelProperties properties = new EventChannelProperties();
        assertThat(properties.channelMode()).isEqualTo(EventChannelMode.AUTO);
        properties.setChannel("local");
        assertThat(properties.channelMode()).isEqualTo(EventChannelMode.LOCAL);
    }
}
