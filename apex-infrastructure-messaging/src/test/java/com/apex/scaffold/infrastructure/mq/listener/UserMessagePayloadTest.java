package com.apex.scaffold.infrastructure.mq.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("用户消息载荷")
class UserMessagePayloadTest {

    @Test
    @DisplayName("动态 Map 应转换为类型化不可变载荷")
    void shouldNormalizeDynamicMessage() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("messageId", "message-1");
        source.put("tag", "USER_REGISTER");
        source.put("tenantId", "tenant-a");
        source.put("userId", 42);

        UserMessagePayload payload = UserMessagePayload.from(source);
        source.put("userId", 99L);

        assertThat(payload.userId()).isEqualTo(42L);
        assertThat(payload.tenantId()).isEqualTo("tenant-a");
        assertThat(payload.nickName()).isEqualTo("用户");
        assertThat(payload.deviceId()).isEqualTo("unknown");
        assertThat(payload.raw()).containsEntry("userId", 42);
        assertThatThrownBy(() -> payload.raw().put("extra", true)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("数字字符串用户ID应转换为Long")
    void shouldNormalizeNumericStringUserId() {
        UserMessagePayload payload = UserMessagePayload.from(Map.of("tenantId", "tenant-a", "userId", "42"));

        assertThat(payload.userId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("非法或空白用户ID应转换为空")
    void shouldRejectInvalidStringUserId() {
        assertThat(UserMessagePayload.from(Map.of("tenantId", "tenant-a", "userId", "invalid"))
                        .userId())
                .isNull();
        assertThat(UserMessagePayload.from(Map.of("tenantId", "tenant-a", "userId", " "))
                        .userId())
                .isNull();
    }

    @Test
    @DisplayName("缺少租户ID应拒绝消息")
    void shouldRejectMissingTenantId() {
        assertThatThrownBy(() -> UserMessagePayload.from(Map.of("messageId", "message-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("用户MQ消息缺少合法租户ID");
    }

    @Test
    @DisplayName("非法租户ID应拒绝消息")
    void shouldRejectInvalidTenantId() {
        assertThatThrownBy(() -> UserMessagePayload.from(Map.of("tenantId", "tenant/a")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("用户MQ消息缺少合法租户ID");
    }
}
