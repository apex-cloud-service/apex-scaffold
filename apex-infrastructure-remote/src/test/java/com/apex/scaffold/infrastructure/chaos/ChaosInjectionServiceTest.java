package com.apex.scaffold.infrastructure.chaos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * 混沌工程故障注入服务单元测试。
 *
 * <p>验证 {@link ChaosInjectionService} 的各类故障注入触发、自动恢复和紧急停止逻辑。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("ChaosInjectionService 混沌工程注入服务测试")
class ChaosInjectionServiceTest {

    /** 被测服务 */
    private ChaosInjectionService service;

    /** 测试用配置属性 */
    private ChaosInjectionProperties properties;

    /** Spring 环境 mock，用于验证生产环境护栏 */
    private Environment environment;

    /**
     * 初始化测试环境。
     */
    @BeforeEach
    void setUp() {
        properties = new ChaosInjectionProperties();
        properties.setEnabled(true);
        properties.getRedis().setDisconnectEnabled(true);
        properties.getRedis().setDisconnectDurationSeconds(1);
        properties.getDatabase().setLatencyEnabled(true);
        properties.getDatabase().setLatencyMillis(500);
        properties.getRemoteApi().setFailureRateEnabled(true);
        properties.getRemoteApi().setFailureRate(0.5);
        // Mockito 默认 acceptsProfiles 返回 false，模拟非生产环境
        environment = mock(Environment.class);
        service = new ChaosInjectionService(properties, environment);
    }

    /**
     * 测试全局开关关闭时所有注入不生效。
     */
    @Test
    @DisplayName("全局开关关闭时应返回false")
    void shouldReturnFalseWhenGlobalDisabled() {
        properties.setEnabled(false);
        assertThat(service.triggerRedisDisconnect()).isFalse();
        assertThat(service.triggerDatabaseLatency()).isFalse();
        assertThat(service.triggerRemoteApiFailure()).isFalse();
        assertThat(service.isRedisDisconnectActive()).isFalse();
        assertThat(service.isDatabaseLatencyActive()).isFalse();
        assertThat(service.shouldRemoteApiFail()).isFalse();
    }

    /**
     * 测试 Redis 断连注入的触发和自动恢复。
     */
    @Test
    @DisplayName("Redis断连注入应触发并在超时后自动恢复")
    void shouldTriggerAndAutoRecoverRedisDisconnect() throws InterruptedException {
        boolean triggered = service.triggerRedisDisconnect();
        assertThat(triggered).isTrue();
        assertThat(service.isRedisDisconnectActive()).isTrue();

        // 等待超过持续时间（1秒）后应自动恢复
        Thread.sleep(1200);
        assertThat(service.isRedisDisconnectActive()).isFalse();
    }

    /**
     * 测试数据库延迟注入的触发。
     */
    @Test
    @DisplayName("数据库延迟注入应正确触发")
    void shouldTriggerDatabaseLatency() {
        assertThat(service.isDatabaseLatencyActive()).isFalse();
        service.triggerDatabaseLatency();
        assertThat(service.isDatabaseLatencyActive()).isTrue();
        assertThat(service.getDatabaseLatencyMillis()).isEqualTo(500);
    }

    /**
     * 测试外部 API 失败率注入的按比例计算。
     */
    @Test
    @DisplayName("外部API失败率应按配置比例注入失败")
    void shouldInjectFailureByRate() {
        service.triggerRemoteApiFailure();
        int failCount = 0;
        int totalCalls = 10;
        for (int i = 0; i < totalCalls; i++) {
            if (service.shouldRemoteApiFail()) {
                failCount++;
            }
        }
        // 50% 失败率，10 次调用应有约 5 次失败
        assertThat(failCount).isBetween(3, 7);
    }

    /**
     * 测试按配置自动激活所有故障注入。
     */
    @Test
    @DisplayName("配置驱动激活应按各子开关自动触发注入")
    void shouldActivateAllInjectionsByConfig() {
        service.activateByConfig();
        assertThat(service.isRedisDisconnectActive()).isTrue();
        assertThat(service.isDatabaseLatencyActive()).isTrue();
        // 失败率按计数取模注入，首次调用不必然失败，多次调用后应出现失败
        boolean failedAtLeastOnce = false;
        for (int i = 0; i < 4; i++) {
            failedAtLeastOnce |= service.shouldRemoteApiFail();
        }
        assertThat(failedAtLeastOnce).isTrue();
    }

    /**
     * 测试全局开关关闭时配置驱动激活不生效。
     */
    @Test
    @DisplayName("全局开关关闭时配置驱动激活应跳过所有注入")
    void shouldSkipActivationWhenGlobalDisabled() {
        properties.setEnabled(false);
        service.activateByConfig();
        assertThat(service.isRedisDisconnectActive()).isFalse();
        assertThat(service.isDatabaseLatencyActive()).isFalse();
        assertThat(service.shouldRemoteApiFail()).isFalse();
    }

    /**
     * 测试生产环境下开启混沌注入应被安全护栏拒绝。
     */
    @Test
    @DisplayName("生产环境开启混沌注入应抛异常拒绝激活")
    void shouldRejectActivationOnProdProfile() {
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true);
        assertThatThrownBy(() -> service.activateByConfig())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("生产环境禁止开启混沌注入");
        assertThat(service.isRedisDisconnectActive()).isFalse();
        assertThat(service.isDatabaseLatencyActive()).isFalse();
        assertThat(service.shouldRemoteApiFail()).isFalse();
    }

    /**
     * 测试生产环境但全局开关关闭时应正常跳过，不触发护栏异常。
     */
    @Test
    @DisplayName("生产环境且全局开关关闭时应正常跳过激活")
    void shouldSkipActivationOnProdProfileWhenGlobalDisabled() {
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true);
        properties.setEnabled(false);
        service.activateByConfig();
        assertThat(service.isRedisDisconnectActive()).isFalse();
        assertThat(service.isDatabaseLatencyActive()).isFalse();
        assertThat(service.shouldRemoteApiFail()).isFalse();
    }

    /**
     * 测试生产环境下手动触发各类注入应被安全护栏拒绝。
     */
    @Test
    @DisplayName("生产环境手动触发注入应抛异常拒绝")
    void shouldRejectManualTriggerOnProdProfile() {
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true);
        assertThatThrownBy(() -> service.triggerRedisDisconnect())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("生产环境禁止开启混沌注入");
        assertThatThrownBy(() -> service.triggerDatabaseLatency())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("生产环境禁止开启混沌注入");
        assertThatThrownBy(() -> service.triggerRemoteApiFailure())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("生产环境禁止开启混沌注入");
        assertThat(service.isRedisDisconnectActive()).isFalse();
        assertThat(service.isDatabaseLatencyActive()).isFalse();
        assertThat(service.shouldRemoteApiFail()).isFalse();
    }

    /**
     * 测试生产环境但全局开关关闭时手动触发应返回 false，不抛护栏异常。
     */
    @Test
    @DisplayName("生产环境且全局开关关闭时手动触发应返回false")
    void shouldSkipManualTriggerOnProdProfileWhenGlobalDisabled() {
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true);
        properties.setEnabled(false);
        assertThat(service.triggerRedisDisconnect()).isFalse();
        assertThat(service.triggerDatabaseLatency()).isFalse();
        assertThat(service.triggerRemoteApiFailure()).isFalse();
    }

    /**
     * 测试紧急停止应关闭所有注入。
     */
    @Test
    @DisplayName("紧急停止应关闭所有注入状态")
    void shouldEmergencyStopAllInjections() {
        service.triggerRedisDisconnect();
        service.triggerDatabaseLatency();
        service.triggerRemoteApiFailure();
        service.emergencyStop();
        assertThat(service.isRedisDisconnectActive()).isFalse();
        assertThat(service.isDatabaseLatencyActive()).isFalse();
        assertThat(service.shouldRemoteApiFail()).isFalse();
    }

    /**
     * 测试状态摘要应包含所有关键信息。
     */
    @Test
    @DisplayName("状态摘要应包含全局开关和各注入状态")
    void shouldReturnStatusSummary() {
        String summary = service.getStatusSummary();
        assertThat(summary).contains("[CHAOS]");
        assertThat(summary).contains("全局: true");
        assertThat(summary).contains("Redis断连: false");
    }
}
