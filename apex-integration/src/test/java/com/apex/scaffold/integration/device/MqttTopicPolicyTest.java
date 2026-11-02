package com.apex.scaffold.integration.device;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.integration.device.domain.MqttTopicPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MQTT topic 命名空间策略测试（方案 M5 / 批次 E3）。
 *
 * <p>验证租户命名空间 ACL 矩阵：本租户本设备放行，跨租户/跨设备/非命名空间/
 * 通配符注入一律拒绝。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("MQTT topic 命名空间策略测试")
class MqttTopicPolicyTest {

    @Test
    @DisplayName("本租户本设备的上下行 topic 放行（含子路径）")
    void allowsOwnNamespace() {
        assertThat(MqttTopicPolicy.isAllowed("t1", "dev-1", "apex/t1/dev-1/up")).isTrue();
        assertThat(MqttTopicPolicy.isAllowed("t1", "dev-1", "apex/t1/dev-1/up/sensor/temp"))
                .isTrue();
        assertThat(MqttTopicPolicy.isAllowed("t1", "dev-1", "apex/t1/dev-1/down"))
                .isTrue();
        assertThat(MqttTopicPolicy.isAllowed("t1", "dev-1", "apex/t1/dev-1/down/cmd-1"))
                .isTrue();
    }

    @Test
    @DisplayName("跨租户/跨设备/未知命名空间拒绝")
    void rejectsCrossTenantAndDevice() {
        assertThat(MqttTopicPolicy.isAllowed("t1", "dev-1", "apex/t2/dev-1/up")).isFalse();
        assertThat(MqttTopicPolicy.isAllowed("t1", "dev-1", "apex/t1/dev-2/up")).isFalse();
        assertThat(MqttTopicPolicy.isAllowed("t1", "dev-1", "other/t1/dev-1/up"))
                .isFalse();
        assertThat(MqttTopicPolicy.isAllowed("t1", "dev-1", "apex/t1/dev-1/other"))
                .isFalse();
    }

    @Test
    @DisplayName("通配符注入与空段拒绝")
    void rejectsWildcardsAndEmptySegments() {
        assertThat(MqttTopicPolicy.isAllowed("t1", "dev-1", "apex/+/dev-1/up")).isFalse();
        assertThat(MqttTopicPolicy.isAllowed("t1", "dev-1", "apex/t1/#/up")).isFalse();
        assertThat(MqttTopicPolicy.isAllowed("t1", "dev-1", "apex/t1/dev-1/up//x"))
                .isFalse();
        assertThat(MqttTopicPolicy.isAllowed("t1", "dev-1", "apex/t1/dev-1/up/+"))
                .isFalse();
    }

    @Test
    @DisplayName("空参数拒绝")
    void rejectsBlankInputs() {
        assertThat(MqttTopicPolicy.isAllowed(null, "dev-1", "apex/t1/dev-1/up")).isFalse();
        assertThat(MqttTopicPolicy.isAllowed("t1", null, "apex/t1/dev-1/up")).isFalse();
        assertThat(MqttTopicPolicy.isAllowed("t1", "dev-1", null)).isFalse();
        assertThat(MqttTopicPolicy.isAllowed("t1", "dev-1", "")).isFalse();
    }

    @Test
    @DisplayName("topic 构造器与共享订阅过滤器")
    void topicBuilders() {
        assertThat(MqttTopicPolicy.upTopic("t1", "dev-1")).isEqualTo("apex/t1/dev-1/up");
        assertThat(MqttTopicPolicy.downTopic("t1", "dev-1", "cmd-1")).isEqualTo("apex/t1/dev-1/down/cmd-1");
        assertThat(MqttTopicPolicy.downTopic("t1", "dev-1", null)).isEqualTo("apex/t1/dev-1/down");
        assertThat(MqttTopicPolicy.sharedUpFilter("group-1")).isEqualTo("$share/group-1/apex/+/+/up/#");
    }
}
