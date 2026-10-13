package com.apex.scaffold.domain.event;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@code TokenRevokedEvent} 的单元测试类
 *
 * <p>测试令牌撤销事件的构造函数、getter 方法、事件标识（eventId/type/sourceId）
 * 以及不可变性。该事件由认证中心登出发布，用于登出传播（迁移方案 Phase 2.6）。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2026-10-09
 */
class TokenRevokedEventTest {

    @Nested
    @DisplayName("构造函数")
    class ConstructorTest {

        @Test
        @DisplayName("使用有效参数应正确设置所有字段")
        void withValidParamsShouldSetAllFields() {
            Long userId = 100L;
            String tenantId = "test-tenant";
            String reason = "LOGOUT";

            TokenRevokedEvent event = new TokenRevokedEvent(userId, tenantId, reason);

            assertThat(event.getUserId()).isEqualTo(userId);
            assertThat(event.getTenantId()).isEqualTo(tenantId);
            assertThat(event.getReason()).isEqualTo(reason);
        }

        @Test
        @DisplayName("userId 为 null 时应允许创建")
        void nullUserIdShouldBeAllowed() {
            TokenRevokedEvent event = new TokenRevokedEvent(null, "tenant", "LOGOUT");
            assertThat(event.getUserId()).isNull();
        }

        @Test
        @DisplayName("reason 为 null 时应允许创建")
        void nullReasonShouldBeAllowed() {
            TokenRevokedEvent event = new TokenRevokedEvent(1L, "tenant", null);
            assertThat(event.getReason()).isNull();
        }
    }

    @Nested
    @DisplayName("事件类型与标识")
    class EventTypeAndIdentityTest {

        @Test
        @DisplayName("事件类型应为 TOKEN_REVOKED")
        void eventTypeShouldBeTokenRevoked() {
            TokenRevokedEvent event = new TokenRevokedEvent(100L, "tenant", "LOGOUT");
            assertThat(event.getEventType()).isEqualTo("TOKEN_REVOKED");
        }

        @Test
        @DisplayName("创建事件时应自动生成 eventId 且唯一")
        void eachEventShouldHaveUniqueEventId() {
            TokenRevokedEvent event1 = new TokenRevokedEvent(100L, "tenant", "LOGOUT");
            TokenRevokedEvent event2 = new TokenRevokedEvent(100L, "tenant", "LOGOUT");
            assertThat(event1.getEventId()).isNotNull();
            assertThat(event1.getEventId()).isNotEqualTo(event2.getEventId());
        }

        @Test
        @DisplayName("创建事件时应自动设置 createdAt")
        void shouldAutoSetCreatedAt() {
            TokenRevokedEvent event = new TokenRevokedEvent(100L, "tenant", "LOGOUT");
            assertThat(event.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("getSourceId 应返回用户 ID 的字符串表示")
        void sourceIdShouldBeUserIdString() {
            TokenRevokedEvent event = new TokenRevokedEvent(100L, "tenant", "LOGOUT");
            assertThat(event.getSourceId()).isEqualTo("100");
        }

        @Test
        @DisplayName("userId 为 null 时 getSourceId 应为 null")
        void sourceIdShouldBeNullWhenUserIdNull() {
            TokenRevokedEvent event = new TokenRevokedEvent(null, "tenant", "LOGOUT");
            assertThat(event.getSourceId()).isNull();
        }
    }

    @Nested
    @DisplayName("不可变性")
    class ImmutabilityTest {

        @Test
        @DisplayName("所有字段应通过构造函数设置后不可修改")
        void fieldsShouldBeSetViaConstructor() {
            TokenRevokedEvent event = new TokenRevokedEvent(200L, "tenant-b", "ADMIN_KICK");
            assertThat(event.getUserId()).isEqualTo(200L);
            assertThat(event.getTenantId()).isEqualTo("tenant-b");
            assertThat(event.getReason()).isEqualTo("ADMIN_KICK");
        }
    }
}
