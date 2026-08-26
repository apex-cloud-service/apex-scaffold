package com.apex.scaffold.infrastructure.mq.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link UserMessagePayload} MQ 边界规范化测试。
 *
 * @author Apex Team
 * @since 3.3.0
 */
@DisplayName("用户消息载荷")
class UserMessagePayloadTest {

    /**
     * 验证数字类型统一、默认字段和原消息不可变快照。
     */
    @Test
    @DisplayName("动态 Map 应转换为类型化不可变载荷")
    void shouldNormalizeDynamicMessage() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("messageId", "message-1");
        source.put("tag", "USER_REGISTER");
        source.put("userId", 42);

        UserMessagePayload payload = UserMessagePayload.from(source);
        source.put("userId", 99L);

        assertThat(payload.userId()).isEqualTo(42L);
        assertThat(payload.nickName()).isEqualTo("用户");
        assertThat(payload.deviceId()).isEqualTo("unknown");
        assertThat(payload.raw()).containsEntry("userId", 42);
        assertThatThrownBy(() -> payload.raw().put("extra", true)).isInstanceOf(UnsupportedOperationException.class);
    }
}
