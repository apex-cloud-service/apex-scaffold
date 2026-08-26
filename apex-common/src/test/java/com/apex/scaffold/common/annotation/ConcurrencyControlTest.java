package com.apex.scaffold.common.annotation;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ConcurrencyControl 注解测试
 *
 * <p>测试并发控制注解的默认值和自定义属性
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("ConcurrencyControl 注解测试")
class ConcurrencyControlTest {

    /**
     * 执行 {@code defaultMethod} 操作。
     */
    @ConcurrencyControl
    public void defaultMethod() {}

    /**
     * 执行 {@code customMethod} 操作。
     */
    @ConcurrencyControl(maxConcurrent = 5, timeout = 3000, message = "自定义消息")
    public void customMethod() {}

    /**
     * 验证 {@code testDefaultAttributes} 场景。
     * @throws Exception 当操作无法完成时
     */
    @Test
    @DisplayName("测试默认属性值")
    void testDefaultAttributes() throws Exception {
        Method method = ConcurrencyControlTest.class.getMethod("defaultMethod");
        ConcurrencyControl annotation = method.getAnnotation(ConcurrencyControl.class);

        assertNotNull(annotation);
        assertEquals(10, annotation.maxConcurrent());
        assertEquals(5000L, annotation.timeout());
        assertEquals("系统繁忙，请稍后再试", annotation.message());
    }

    /**
     * 验证 {@code testCustomAttributes} 场景。
     * @throws Exception 当操作无法完成时
     */
    @Test
    @DisplayName("测试自定义属性值")
    void testCustomAttributes() throws Exception {
        Method method = ConcurrencyControlTest.class.getMethod("customMethod");
        ConcurrencyControl annotation = method.getAnnotation(ConcurrencyControl.class);

        assertNotNull(annotation);
        assertEquals(5, annotation.maxConcurrent());
        assertEquals(3000L, annotation.timeout());
        assertEquals("自定义消息", annotation.message());
    }

    /**
     * 验证 {@code testTargetElementType} 场景。
     */
    @Test
    @DisplayName("测试注解目标元素类型为方法")
    void testTargetElementType() {
        assertNotNull(ConcurrencyControl.class.getAnnotation(java.lang.annotation.Target.class));
    }

    /**
     * 验证 {@code testRetentionPolicy} 场景。
     */
    @Test
    @DisplayName("测试注解保留策略为运行时")
    void testRetentionPolicy() {
        java.lang.annotation.Retention retention =
                ConcurrencyControl.class.getAnnotation(java.lang.annotation.Retention.class);
        assertNotNull(retention);
        assertEquals(java.lang.annotation.RetentionPolicy.RUNTIME, retention.value());
    }
}
