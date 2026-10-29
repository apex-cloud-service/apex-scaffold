package com.apex.scaffold.common.event;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * AbstractDomainEvent 抽象领域事件测试
 *
 * <p>验证领域事件的 eventId（UUID）和 createdAt（时间戳）的生成逻辑
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("AbstractDomainEvent 抽象领域事件测试")
class AbstractDomainEventTest {

    private static class TestDomainEvent extends AbstractDomainEvent {
        TestDomainEvent() {
            super("TEST_EVENT", null);
        }

        /** {@inheritDoc} */
        @Override
        public String getSourceId() {
            return "test-source";
        }
    }

    @Nested
    @DisplayName("eventId UUID 测试")
    class EventIdTest {

        @Test
        @DisplayName("eventId 不应为 null")
        void eventIdShouldNotBeNull() {
            TestDomainEvent event = new TestDomainEvent();
            assertNotNull(event.getEventId());
        }

        @Test
        @DisplayName("eventId 不应为空字符串")
        void eventIdShouldNotBeEmpty() {
            TestDomainEvent event = new TestDomainEvent();
            assertFalse(event.getEventId().isEmpty());
        }

        @Test
        @DisplayName("eventId 应为有效的 UUID 格式")
        void eventIdShouldBeValidUuidFormat() {
            TestDomainEvent event = new TestDomainEvent();
            assertDoesNotThrow(() -> UUID.fromString(event.getEventId()));
        }

        @Test
        @DisplayName("不同事件实例的 eventId 应各不相同")
        void differentEventsShouldHaveDifferentEventIds() {
            TestDomainEvent event1 = new TestDomainEvent();
            TestDomainEvent event2 = new TestDomainEvent();

            assertNotEquals(event1.getEventId(), event2.getEventId());
        }

        @Test
        @DisplayName("eventId 长度应为 36 个字符（标准 UUID 字符串长度）")
        void eventIdShouldHave36Characters() {
            TestDomainEvent event = new TestDomainEvent();
            assertEquals(36, event.getEventId().length());
        }
    }

    @Nested
    @DisplayName("createdAt 时间戳测试")
    class CreatedAtTest {
        @Test
        @DisplayName("createdAt 不应为 null")
        void createdAtShouldNotBeNull() {
            TestDomainEvent event = new TestDomainEvent();
            assertNotNull(event.getCreatedAt());
        }

        @Test
        @DisplayName("createdAt 应接近当前时间（误差在 2 秒内）")
        void createdAtShouldBeCloseToCurrentTime() {
            LocalDateTime before = LocalDateTime.now();
            TestDomainEvent event = new TestDomainEvent();
            LocalDateTime after = LocalDateTime.now();

            assertFalse(event.getCreatedAt().isBefore(before.minusSeconds(2)));
            assertFalse(event.getCreatedAt().isAfter(after.plusSeconds(2)));
        }

        @Test
        @DisplayName("相隔一段时间创建的事件，createdAt 应不同")
        void eventsCreatedWithDelayShouldHaveDifferentCreatedAt() throws Exception {
            TestDomainEvent event1 = new TestDomainEvent();
            Thread.sleep(2);
            TestDomainEvent event2 = new TestDomainEvent();

            assertNotEquals(event1.getCreatedAt(), event2.getCreatedAt());
        }
    }

    @Nested
    @DisplayName("不可变性测试")
    class ImmutabilityTest {
        @Test
        @DisplayName("eventId 每次调用 getter 应返回相同的值")
        void eventIdShouldBeImmutable() {
            TestDomainEvent event = new TestDomainEvent();
            String id1 = event.getEventId();
            String id2 = event.getEventId();

            assertEquals(id1, id2);
        }

        @Test
        @DisplayName("createdAt 每次调用 getter 应返回相同的值")
        void createdAtShouldBeImmutable() {
            TestDomainEvent event = new TestDomainEvent();
            LocalDateTime time1 = event.getCreatedAt();
            LocalDateTime time2 = event.getCreatedAt();

            assertEquals(time1, time2);
        }
    }
}
