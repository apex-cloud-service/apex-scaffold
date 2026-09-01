package com.apex.scaffold.common.annotation;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * OperationLog 注解测试
 *
 * <p>测试操作日志注解的默认值和自定义属性
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("OperationLog 注解测试")
class OperationLogTest {
    @OperationLog
    public void defaultMethod() {}

    @OperationLog(
            module = "用户管理",
            type = OperationLog.OperationType.INSERT,
            description = "新增用户",
            saveRequest = false,
            saveResponse = false,
            excludeRequestFields = {"password"},
            excludeResponseFields = {"token"})
    public void customMethod() {}

    /**
     * 验证 {@code testDefaultAttributes} 场景。
     */
    @Test
    @DisplayName("测试默认属性值")
    void testDefaultAttributes() throws Exception {
        Method method = OperationLogTest.class.getMethod("defaultMethod");
        OperationLog annotation = method.getAnnotation(OperationLog.class);

        assertNotNull(annotation);
        assertEquals("", annotation.module());
        assertEquals(OperationLog.OperationType.OTHER, annotation.type());
        assertEquals("", annotation.description());
        assertTrue(annotation.saveRequest());
        assertTrue(annotation.saveResponse());
        assertArrayEquals(
                new String[] {"password", "oldPassword", "newPassword", "token"}, annotation.excludeRequestFields());
        assertArrayEquals(new String[] {}, annotation.excludeResponseFields());
    }

    /**
     * 验证 {@code testCustomAttributes} 场景。
     */
    @Test
    @DisplayName("测试自定义属性值")
    void testCustomAttributes() throws Exception {
        Method method = OperationLogTest.class.getMethod("customMethod");
        OperationLog annotation = method.getAnnotation(OperationLog.class);

        assertNotNull(annotation);
        assertEquals("用户管理", annotation.module());
        assertEquals(OperationLog.OperationType.INSERT, annotation.type());
        assertEquals("新增用户", annotation.description());
        assertFalse(annotation.saveRequest());
        assertFalse(annotation.saveResponse());
        assertArrayEquals(new String[] {"password"}, annotation.excludeRequestFields());
        assertArrayEquals(new String[] {"token"}, annotation.excludeResponseFields());
    }

    /**
     * 验证 {@code testOperationTypeEnum} 场景。
     */
    @Test
    @DisplayName("测试 OperationType 枚举")
    void testOperationTypeEnum() {
        assertEquals(11, OperationLog.OperationType.values().length);

        assertEquals("新增", OperationLog.OperationType.INSERT.getName());
        assertEquals("INSERT", OperationLog.OperationType.INSERT.getCode());

        assertEquals("修改", OperationLog.OperationType.UPDATE.getName());
        assertEquals("UPDATE", OperationLog.OperationType.UPDATE.getCode());

        assertEquals("删除", OperationLog.OperationType.DELETE.getName());
        assertEquals("DELETE", OperationLog.OperationType.DELETE.getCode());

        assertEquals("查询", OperationLog.OperationType.SELECT.getName());
        assertEquals("SELECT", OperationLog.OperationType.SELECT.getCode());

        assertEquals("登录", OperationLog.OperationType.LOGIN.getName());
        assertEquals("LOGIN", OperationLog.OperationType.LOGIN.getCode());

        assertEquals("登出", OperationLog.OperationType.LOGOUT.getName());
        assertEquals("LOGOUT", OperationLog.OperationType.LOGOUT.getCode());

        assertEquals("上传", OperationLog.OperationType.UPLOAD.getName());
        assertEquals("UPLOAD", OperationLog.OperationType.UPLOAD.getCode());

        assertEquals("下载", OperationLog.OperationType.DOWNLOAD.getName());
        assertEquals("DOWNLOAD", OperationLog.OperationType.DOWNLOAD.getCode());

        assertEquals("导出", OperationLog.OperationType.EXPORT.getName());
        assertEquals("EXPORT", OperationLog.OperationType.EXPORT.getCode());

        assertEquals("导入", OperationLog.OperationType.IMPORT.getName());
        assertEquals("IMPORT", OperationLog.OperationType.IMPORT.getCode());

        assertEquals("其他", OperationLog.OperationType.OTHER.getName());
        assertEquals("OTHER", OperationLog.OperationType.OTHER.getCode());
    }
}
