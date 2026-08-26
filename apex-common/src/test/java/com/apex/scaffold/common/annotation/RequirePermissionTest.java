package com.apex.scaffold.common.annotation;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RequirePermission 注解测试
 *
 * <p>测试权限校验注解的属性
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("RequirePermission 注解测试")
class RequirePermissionTest {

    /**
     * 执行 {@code defaultMethod} 操作。
     */
    @RequirePermission("user:list")
    public void defaultMethod() {}

    /**
     * 执行 {@code customMethod} 操作。
     */
    @RequirePermission(value = "user:create", logical = Logical.AND)
    public void customMethod() {}

    /**
     * 验证 {@code testDefaultAttributes} 场景。
     * @throws Exception 当操作无法完成时
     */
    @Test
    @DisplayName("测试默认属性值")
    void testDefaultAttributes() throws Exception {
        Method method = RequirePermissionTest.class.getMethod("defaultMethod");
        RequirePermission annotation = method.getAnnotation(RequirePermission.class);

        assertNotNull(annotation);
        assertEquals("user:list", annotation.value());
        assertEquals(Logical.OR, annotation.logical());
    }

    /**
     * 验证 {@code testCustomAttributes} 场景。
     * @throws Exception 当操作无法完成时
     */
    @Test
    @DisplayName("测试自定义属性值")
    void testCustomAttributes() throws Exception {
        Method method = RequirePermissionTest.class.getMethod("customMethod");
        RequirePermission annotation = method.getAnnotation(RequirePermission.class);

        assertNotNull(annotation);
        assertEquals("user:create", annotation.value());
        assertEquals(Logical.AND, annotation.logical());
    }

    /**
     * 验证 {@code testTarget} 场景。
     */
    @Test
    @DisplayName("测试注解目标为方法")
    void testTarget() {
        java.lang.annotation.Target target = RequirePermission.class.getAnnotation(java.lang.annotation.Target.class);
        assertNotNull(target);
        assertArrayEquals(
                new java.lang.annotation.ElementType[] {java.lang.annotation.ElementType.METHOD}, target.value());
    }

    /**
     * 验证 {@code testRetention} 场景。
     */
    @Test
    @DisplayName("测试注解保留策略为运行时")
    void testRetention() {
        java.lang.annotation.Retention retention =
                RequirePermission.class.getAnnotation(java.lang.annotation.Retention.class);
        assertNotNull(retention);
        assertEquals(java.lang.annotation.RetentionPolicy.RUNTIME, retention.value());
    }
}
