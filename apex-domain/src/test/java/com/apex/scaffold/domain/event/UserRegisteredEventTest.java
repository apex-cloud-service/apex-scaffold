package com.apex.scaffold.domain.event;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link UserRegisteredEvent} 的单元测试类
 *
 * <p>测试用户注册成功领域事件的构造函数、getter 方法以及不可变性
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
class UserRegisteredEventTest {

    @Nested
    @DisplayName("构造函数")
    class ConstructorTest {

        /**
         * 执行 {@code withValidParamsShouldSetAllFields} 操作。
         */
        @Test
        @DisplayName("使用有效参数应正确设置所有字段")
        void withValidParamsShouldSetAllFields() {
            Long userId = 1L;
            String nickName = "测试用户";
            String phone = "13800138000";

            UserRegisteredEvent event = new UserRegisteredEvent(userId, nickName, phone);

            assertThat(event.getUserId()).isEqualTo(userId);
            assertThat(event.getNickName()).isEqualTo(nickName);
            assertThat(event.getPhone()).isEqualTo(phone);
        }

        /**
         * 执行 {@code nullUserIdShouldBeAllowed} 操作。
         */
        @Test
        @DisplayName("userId 为 null 时应允许创建")
        void nullUserIdShouldBeAllowed() {
            UserRegisteredEvent event = new UserRegisteredEvent(null, "nick", "phone");
            assertThat(event.getUserId()).isNull();
        }

        /**
         * 执行 {@code nullNickNameShouldBeAllowed} 操作。
         */
        @Test
        @DisplayName("nickName 为 null 时应允许创建")
        void nullNickNameShouldBeAllowed() {
            UserRegisteredEvent event = new UserRegisteredEvent(1L, null, "phone");
            assertThat(event.getNickName()).isNull();
        }

        /**
         * 执行 {@code nullPhoneShouldBeAllowed} 操作。
         */
        @Test
        @DisplayName("phone 为 null 时应允许创建")
        void nullPhoneShouldBeAllowed() {
            UserRegisteredEvent event = new UserRegisteredEvent(1L, "nick", null);
            assertThat(event.getPhone()).isNull();
        }
    }

    @Nested
    @DisplayName("继承自 AbstractDomainEvent 的行为")
    class AbstractDomainEventInheritanceTest {

        /**
         * 执行 {@code shouldAutoGenerateEventId} 操作。
         */
        @Test
        @DisplayName("创建事件时应自动生成 eventId")
        void shouldAutoGenerateEventId() {
            UserRegisteredEvent event = new UserRegisteredEvent(1L, "nick", "phone");
            assertThat(event.getEventId()).isNotNull();
        }

        /**
         * 执行 {@code eachEventShouldHaveUniqueEventId} 操作。
         */
        @Test
        @DisplayName("每次创建事件应生成不同的 eventId")
        void eachEventShouldHaveUniqueEventId() {
            UserRegisteredEvent event1 = new UserRegisteredEvent(1L, "nick", "phone");
            UserRegisteredEvent event2 = new UserRegisteredEvent(1L, "nick", "phone");
            assertThat(event1.getEventId()).isNotEqualTo(event2.getEventId());
        }

        /**
         * 执行 {@code shouldAutoSetCreatedAt} 操作。
         */
        @Test
        @DisplayName("创建事件时应自动设置 createdAt")
        void shouldAutoSetCreatedAt() {
            UserRegisteredEvent event = new UserRegisteredEvent(1L, "nick", "phone");
            assertThat(event.getCreatedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("不可变性")
    class ImmutabilityTest {

        /**
         * 执行 {@code userIdShouldBeFinal} 操作。
         */
        @Test
        @DisplayName("userId 字段应为 final")
        void userIdShouldBeFinal() {
            assertThat(java.lang.reflect.Modifier.isFinal(
                            UserRegisteredEvent.class.getDeclaredFields()[0].getModifiers()))
                    .isTrue();
        }

        /**
         * 执行 {@code fieldsShouldBeSetViaConstructor} 操作。
         */
        @Test
        @DisplayName("所有字段应通过构造函数设置后不可修改")
        void fieldsShouldBeSetViaConstructor() {
            UserRegisteredEvent event = new UserRegisteredEvent(10L, "name", "phone");
            assertThat(event.getUserId()).isEqualTo(10L);
            assertThat(event.getNickName()).isEqualTo("name");
            assertThat(event.getPhone()).isEqualTo("phone");
        }
    }
}
