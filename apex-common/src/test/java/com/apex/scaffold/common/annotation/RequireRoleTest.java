package com.apex.scaffold.common.annotation;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RequireRole 注解测试
 *
 * <p>测试角色控制注解的属性
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("RequireRole 注解测试")
class RequireRoleTest {

    /**
     * 执行 {@code defaultMethod} 操作。
     */
    @RequireRole("ADMIN")
    public void defaultMethod() {}

    /**
     * 执行 {@code customMethod} 操作。
     */
    @RequireRole(
            value = {"ADMIN", "USER"},
            requireAll = true)
    public void customMethod() {}

    /**
     * 验证 {@code testDefaultAttributes} 场景。
     * @throws Exception 当操作无法完成时
     */
    @Test
    @DisplayName("测试单个角色的默认值")
    void testDefaultAttributes() throws Exception {
        Method method = RequireRoleTest.class.getMethod("defaultMethod");
        RequireRole annotation = method.getAnnotation(RequireRole.class);

        assertNotNull(annotation);
        assertArrayEquals(new String[] {"ADMIN"}, annotation.value());
        assertFalse(annotation.requireAll());
    }

    /**
     * 验证 {@code testCustomAttributes} 场景。
     * @throws Exception 当操作无法完成时
     */
    @Test
    @DisplayName("测试多角色和 requireAll 为 true")
    void testCustomAttributes() throws Exception {
        Method method = RequireRoleTest.class.getMethod("customMethod");
        RequireRole annotation = method.getAnnotation(RequireRole.class);

        assertNotNull(annotation);
        assertArrayEquals(new String[] {"ADMIN", "USER"}, annotation.value());
        assertTrue(annotation.requireAll());
    }

    /**
     * 验证 {@code testTarget} 场景。
     */
    @Test
    @DisplayName("测试注解可作用于方法和类")
    void testTarget() {
        java.lang.annotation.Target target = RequireRole.class.getAnnotation(java.lang.annotation.Target.class);
        assertNotNull(target);
        assertArrayEquals(
                new java.lang.annotation.ElementType[] {
                    java.lang.annotation.ElementType.METHOD, java.lang.annotation.ElementType.TYPE
                },
                target.value());
    }

    /**
     * 验证 {@code testRetention} 场景。
     */
    @Test
    @DisplayName("测试注解保留策略为运行时")
    void testRetention() {
        java.lang.annotation.Retention retention =
                RequireRole.class.getAnnotation(java.lang.annotation.Retention.class);
        assertNotNull(retention);
        assertEquals(java.lang.annotation.RetentionPolicy.RUNTIME, retention.value());
    }
}
