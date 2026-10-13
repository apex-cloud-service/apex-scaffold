package com.apex.scaffold.domain.event;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@code UserLoggedInEvent} 的单元测试类
 *
 * <p>测试用户登录事件的构造函数、getter 方法以及不可变性
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
class UserLoggedInEventTest {

    @Nested
    @DisplayName("构造函数")
    class ConstructorTest {
        @Test
        @DisplayName("使用有效参数应正确设置所有字段")
        void withValidParamsShouldSetAllFields() {
            Long userId = 1L;
            String loginIp = "192.168.1.1";

            UserLoggedInEvent event = new UserLoggedInEvent(userId, loginIp);

            assertThat(event.getUserId()).isEqualTo(userId);
            assertThat(event.getLoginIp()).isEqualTo(loginIp);
        }

        @Test
        @DisplayName("userId 为 null 时应允许创建")
        void nullUserIdShouldBeAllowed() {
            UserLoggedInEvent event = new UserLoggedInEvent(null, "127.0.0.1");
            assertThat(event.getUserId()).isNull();
        }

        @Test
        @DisplayName("loginIp 为 null 时应允许创建")
        void nullLoginIpShouldBeAllowed() {
            UserLoggedInEvent event = new UserLoggedInEvent(1L, null);
            assertThat(event.getLoginIp()).isNull();
        }
    }

    @Nested
    @DisplayName("继承自 AbstractDomainEvent 的行为")
    class AbstractDomainEventInheritanceTest {
        @Test
        @DisplayName("创建事件时应自动生成 eventId")
        void shouldAutoGenerateEventId() {
            UserLoggedInEvent event = new UserLoggedInEvent(1L, "192.168.1.1");
            assertThat(event.getEventId()).isNotNull();
        }

        @Test
        @DisplayName("每次创建事件应生成不同的 eventId")
        void eachEventShouldHaveUniqueEventId() {
            UserLoggedInEvent event1 = new UserLoggedInEvent(1L, "192.168.1.1");
            UserLoggedInEvent event2 = new UserLoggedInEvent(1L, "192.168.1.1");
            assertThat(event1.getEventId()).isNotEqualTo(event2.getEventId());
        }

        @Test
        @DisplayName("创建事件时应自动设置 createdAt")
        void shouldAutoSetCreatedAt() {
            UserLoggedInEvent event = new UserLoggedInEvent(1L, "192.168.1.1");
            assertThat(event.getCreatedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("不可变性")
    class ImmutabilityTest {
        @Test
        @DisplayName("userId 字段应为 final")
        void userIdShouldBeFinal() {
            assertThat(java.lang.reflect.Modifier.isFinal(
                            UserLoggedInEvent.class.getDeclaredFields()[0].getModifiers()))
                    .isTrue();
        }

        @Test
        @DisplayName("所有字段应通过构造函数设置后不可修改")
        void fieldsShouldBeSetViaConstructor() {
            UserLoggedInEvent event = new UserLoggedInEvent(10L, "10.0.0.1");
            assertThat(event.getUserId()).isEqualTo(10L);
            assertThat(event.getLoginIp()).isEqualTo("10.0.0.1");
        }
    }
}
