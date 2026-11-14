package com.apex.scaffold.common.exception;

import static org.junit.jupiter.api.Assertions.*;

import com.apex.scaffold.common.exception.AlertEvent.AlertLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * AlertEvent 告警事件测试
 *
 * <p>验证 AlertEvent 的所有构造函数、Lombok 生成的 getter/setter/builder/equals/hashCode/toString。
 * level 字段已由 String 改为 AlertLevel 枚举类型。
 *
 * @author Apex Team
 * @version 1.1.0
 * @since 1.0.0
 */
@DisplayName("AlertEvent 告警事件测试")
class AlertEventTest {

    @Nested
    @DisplayName("无参构造函数测试")
    class NoArgsConstructorTest {
        @Test
        @DisplayName("无参构造应创建对象且所有字段为 null 或默认值")
        void shouldCreateObjectWithDefaultValues() {
            AlertEvent event = new AlertEvent();

            assertNull(event.getExceptionType());
            assertNull(event.getMessage());
            assertNull(event.getTraceId());
            assertEquals(0, event.getCount());
            assertNull(event.getServiceName());
            assertEquals(AlertLevel.ERROR, event.getLevel());
        }
    }

    @Nested
    @DisplayName("全参构造函数测试")
    class AllArgsConstructorTest {
        @Test
        @DisplayName("所有字段应通过构造函数正确设置")
        void shouldSetAllFieldsViaConstructor() {
            AlertEvent event =
                    new AlertEvent("NullPointerException", "空指针", "trace-123", 5, "apex-service", AlertLevel.CRITICAL);

            assertEquals("NullPointerException", event.getExceptionType());
            assertEquals("空指针", event.getMessage());
            assertEquals("trace-123", event.getTraceId());
            assertEquals(5, event.getCount());
            assertEquals("apex-service", event.getServiceName());
            assertEquals(AlertLevel.CRITICAL, event.getLevel());
        }
    }

    @Nested
    @DisplayName("Exception 构造函数测试")
    class ExceptionConstructorTest {
        @Test
        @DisplayName("传入 RuntimeException 应设置 exceptionType 为类名")
        void runtimeExceptionShouldSetExceptionTypeToClassName() {
            RuntimeException ex = new RuntimeException("运行时异常");
            AlertEvent event = new AlertEvent(ex);

            assertEquals("RuntimeException", event.getExceptionType());
        }

        @Test
        @DisplayName("应设置 message 为异常的 getMessage()")
        void shouldSetMessageFromException() {
            Exception ex = new Exception("测试异常消息");
            AlertEvent event = new AlertEvent(ex);

            assertEquals("测试异常消息", event.getMessage());
        }

        @Test
        @DisplayName("count 应设置为 1")
        void countShouldBeOne() {
            AlertEvent event = new AlertEvent(new RuntimeException("test"));

            assertEquals(1, event.getCount());
        }

        @Test
        @DisplayName("非 BusinessException 的 level 应为 AlertLevel.ERROR")
        void nonBusinessExceptionShouldHaveErrorLevel() {
            AlertEvent event = new AlertEvent(new RuntimeException("test"));

            assertEquals(AlertLevel.ERROR, event.getLevel());
        }

        @Test
        @DisplayName("BusinessException 的 level 应为 AlertLevel.WARNING")
        void businessExceptionShouldHaveWarningLevel() {
            BusinessException ex = new BusinessException("业务异常");
            AlertEvent event = new AlertEvent(ex);

            assertEquals(AlertLevel.WARNING, event.getLevel());
        }

        @Test
        @DisplayName("传入 NullPointerException 应正确处理")
        void nullPointerExceptionShouldBeHandled() {
            NullPointerException ex = new NullPointerException("空指针");
            AlertEvent event = new AlertEvent(ex);

            assertEquals("NullPointerException", event.getExceptionType());
            assertEquals("空指针", event.getMessage());
            assertEquals(1, event.getCount());
            assertEquals(AlertLevel.ERROR, event.getLevel());
        }

        @Test
        @DisplayName("传入 IllegalArgumentException 应正确处理")
        void illegalArgumentExceptionShouldBeHandled() {
            IllegalArgumentException ex = new IllegalArgumentException("非法参数");
            AlertEvent event = new AlertEvent(ex);

            assertEquals("IllegalArgumentException", event.getExceptionType());
            assertEquals("非法参数", event.getMessage());
        }
    }

    @Nested
    @DisplayName("Builder 模式测试")
    class BuilderTest {

        /**
         * 构建{@code erShouldSetAllFields}。
         */
        @Test
        @DisplayName("Builder 应正确构建所有字段")
        void builderShouldSetAllFields() {
            AlertEvent event = AlertEvent.builder()
                    .exceptionType("TimeoutException")
                    .message("超时")
                    .traceId("trace-456")
                    .count(3)
                    .serviceName("apex-gateway")
                    .level(AlertLevel.WARNING)
                    .build();

            assertEquals("TimeoutException", event.getExceptionType());
            assertEquals("超时", event.getMessage());
            assertEquals("trace-456", event.getTraceId());
            assertEquals(3, event.getCount());
            assertEquals("apex-gateway", event.getServiceName());
            assertEquals(AlertLevel.WARNING, event.getLevel());
        }

        /**
         * 构建{@code erShouldUseDefaultLevel}。
         */
        @Test
        @DisplayName("Builder 未设置 level 时应使用默认值 AlertLevel.ERROR")
        void builderShouldUseDefaultLevel() {
            AlertEvent event = AlertEvent.builder()
                    .exceptionType("TestException")
                    .message("test")
                    .build();

            assertEquals(AlertLevel.ERROR, event.getLevel());
        }

        /**
         * 构建{@code erWithPartialFieldsShouldWork}。
         */
        @Test
        @DisplayName("Builder 部分字段设置应正常工作")
        void builderWithPartialFieldsShouldWork() {
            AlertEvent event = AlertEvent.builder()
                    .exceptionType("Error")
                    .message("something went wrong")
                    .build();

            assertEquals("Error", event.getExceptionType());
            assertEquals("something went wrong", event.getMessage());
            assertNull(event.getTraceId());
            assertEquals(0, event.getCount());
            assertNull(event.getServiceName());
        }
    }

    @Nested
    @DisplayName("Setter/Getter 测试")
    class SetterGetterTest {
        @Test
        @DisplayName("所有属性应可通过 setter/getter 操作")
        void allPropertiesShouldBeSetAndGet() {
            AlertEvent event = new AlertEvent();
            event.setExceptionType("IOException");
            event.setMessage("IO错误");
            event.setTraceId("trace-789");
            event.setCount(10);
            event.setServiceName("apex-core");
            event.setLevel(AlertLevel.CRITICAL);

            assertEquals("IOException", event.getExceptionType());
            assertEquals("IO错误", event.getMessage());
            assertEquals("trace-789", event.getTraceId());
            assertEquals(10, event.getCount());
            assertEquals("apex-core", event.getServiceName());
            assertEquals(AlertLevel.CRITICAL, event.getLevel());
        }
    }

    @Nested
    @DisplayName("Lombok equals/hashCode 测试")
    class EqualsHashCodeTest {
        @Test
        @DisplayName("属性相同的两个对象应相等")
        void equalObjectsShouldBeEqual() {
            AlertEvent e1 = AlertEvent.builder()
                    .exceptionType("Test")
                    .message("m")
                    .traceId("t")
                    .count(1)
                    .serviceName("s")
                    .level(AlertLevel.ERROR)
                    .build();
            AlertEvent e2 = AlertEvent.builder()
                    .exceptionType("Test")
                    .message("m")
                    .traceId("t")
                    .count(1)
                    .serviceName("s")
                    .level(AlertLevel.ERROR)
                    .build();

            assertEquals(e1, e2);
            assertEquals(e1.hashCode(), e2.hashCode());
        }

        @Test
        @DisplayName("count 不同的两个对象应不相等")
        void differentCountShouldNotBeEqual() {
            AlertEvent e1 = AlertEvent.builder().count(1).build();
            AlertEvent e2 = AlertEvent.builder().count(2).build();

            assertNotEquals(e1, e2);
        }

        @Test
        @DisplayName("level 不同的两个对象应不相等")
        void differentLevelShouldNotBeEqual() {
            AlertEvent e1 = AlertEvent.builder().level(AlertLevel.ERROR).build();
            AlertEvent e2 = AlertEvent.builder().level(AlertLevel.WARNING).build();

            assertNotEquals(e1, e2);
        }

        @Test
        @DisplayName("与 null 比较应返回 false")
        void nullComparisonShouldReturnFalse() {
            AlertEvent event = new AlertEvent();
            assertNotEquals(null, event);
        }
    }

    @Nested
    @DisplayName("Lombok toString 测试")
    class ToStringTest {

        /**
         * 转换为{@code stringShouldContainAllFields}。
         */
        @Test
        @DisplayName("toString 应包含所有字段")
        void toStringShouldContainAllFields() {
            AlertEvent event = AlertEvent.builder()
                    .exceptionType("Test")
                    .message("m")
                    .traceId("t")
                    .count(1)
                    .serviceName("s")
                    .level(AlertLevel.ERROR)
                    .build();
            String str = event.toString();

            assertTrue(str.contains("exceptionType"));
            assertTrue(str.contains("message"));
            assertTrue(str.contains("traceId"));
            assertTrue(str.contains("count"));
            assertTrue(str.contains("serviceName"));
            assertTrue(str.contains("level"));
        }
    }
}
