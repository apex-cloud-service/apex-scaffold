package com.apex.scaffold.common.annotation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Logical 枚举测试
 *
 * <p>测试逻辑关系枚举的常量和基本属性
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("Logical 枚举测试")
class LogicalTest {

    /**
     * 验证 {@code testEnumConstants} 场景。
     */
    @Test
    @DisplayName("测试枚举常量存在")
    void testEnumConstants() {
        Logical[] values = Logical.values();
        assertEquals(2, values.length);
        assertEquals(Logical.AND, Logical.valueOf("AND"));
        assertEquals(Logical.OR, Logical.valueOf("OR"));
    }

    /**
     * 验证 {@code testAnd} 场景。
     */
    @Test
    @DisplayName("测试 AND 枚举")
    void testAnd() {
        assertEquals("AND", Logical.AND.name());
        assertEquals(0, Logical.AND.ordinal());
    }

    /**
     * 验证 {@code testOr} 场景。
     */
    @Test
    @DisplayName("测试 OR 枚举")
    void testOr() {
        assertEquals("OR", Logical.OR.name());
        assertEquals(1, Logical.OR.ordinal());
    }
}
