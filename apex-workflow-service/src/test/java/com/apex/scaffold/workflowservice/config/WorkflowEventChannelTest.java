package com.apex.scaffold.workflowservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.apex.scaffold.common.event.EventChannelProperties;
import com.apex.scaffold.workflowservice.infrastructure.event.LocalWorkflowEventPublisher;
import com.apex.scaffold.workflowservice.infrastructure.event.MqWorkflowEventPublisher;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 工作流事件通道选择测试（方案 M3 / 批次 C8 自适应落地验证）。
 *
 * <p>验证 {@link WorkflowEventConfig} 真正消费 {@code apex.events.channel}：
 * AUTO 按环境能力解析、LOCAL/REMOTE 强制、REMOTE 缺能力时快速失败。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("工作流事件通道选择测试")
class WorkflowEventChannelTest {

    private final WorkflowEventConfig config = new WorkflowEventConfig();

    private EventChannelProperties props(String channel) {
        EventChannelProperties properties = new EventChannelProperties();
        properties.setChannel(channel);
        return properties;
    }

    private org.springframework.beans.factory.ObjectProvider<RocketMQTemplate> mq(boolean available) {
        org.springframework.beans.factory.support.DefaultListableBeanFactory factory =
                new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        if (available) {
            factory.registerSingleton("rocketMQTemplate", mock(RocketMQTemplate.class));
        }
        return factory.getBeanProvider(RocketMQTemplate.class);
    }

    @Test
    @DisplayName("AUTO + 有 MQ + Outbox 启用 → MQ 实现")
    void autoWithMqResolvesRemote() {
        var publisher = config.workflowEventPublisher(props("auto"), mq(true), true);
        assertThat(publisher).isInstanceOf(MqWorkflowEventPublisher.class);
    }

    @Test
    @DisplayName("AUTO + 无 MQ → 本地实现（组合拓扑退化）")
    void autoWithoutMqResolvesLocal() {
        var publisher = config.workflowEventPublisher(props("auto"), mq(false), true);
        assertThat(publisher).isInstanceOf(LocalWorkflowEventPublisher.class);
    }

    @Test
    @DisplayName("AUTO + 有 MQ 但 Outbox 关闭 → 本地实现")
    void autoWithMqButOutboxDisabledResolvesLocal() {
        var publisher = config.workflowEventPublisher(props("auto"), mq(true), false);
        assertThat(publisher).isInstanceOf(LocalWorkflowEventPublisher.class);
    }

    @Test
    @DisplayName("LOCAL 强制：即使有 MQ 也用本地派发")
    void localForcesLocal() {
        var publisher = config.workflowEventPublisher(props("local"), mq(true), true);
        assertThat(publisher).isInstanceOf(LocalWorkflowEventPublisher.class);
    }

    @Test
    @DisplayName("REMOTE 强制：有 MQ 用 MQ 实现")
    void remoteForcesMq() {
        var publisher = config.workflowEventPublisher(props("remote"), mq(true), true);
        assertThat(publisher).isInstanceOf(MqWorkflowEventPublisher.class);
    }

    @Test
    @DisplayName("REMOTE 缺 MQ 时守卫快速失败（不静默退化）")
    void remoteWithoutMqFailsFast() {
        assertThatThrownBy(() -> config.workflowRemoteChannelGuard(mq(false), true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("需要 RocketMQTemplate");
    }

    @Test
    @DisplayName("REMOTE + MQ 但 Outbox 关闭时守卫快速失败")
    void remoteWithOutboxDisabledFailsFast() {
        assertThatThrownBy(() -> config.workflowRemoteChannelGuard(mq(true), false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outbox.enabled=true");
    }

    @Test
    @DisplayName("REMOTE 条件齐备时守卫通过")
    void remoteGuardPassesWhenReady() {
        assertThat(config.workflowRemoteChannelGuard(mq(true), true)).isNotNull();
    }
}
