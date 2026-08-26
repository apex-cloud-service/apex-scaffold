package com.apex.scaffold.interfaces.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link FileUploadRequestDoc} 字段赋值与取值测试。
 *
 * <p>验证 usage 和 clientRequestId 的 getter/setter 行为正确，确保 OpenAPI
 * 文档模型与业务代码保持一致。</p>
 *
 * @author Apex Team
 * @since 1.0.0
 */
@DisplayName("文件上传请求文档模型测试")
class FileUploadRequestDocTest {

    /**
     * 验证 usage 字段的 getter/setter 行为。
     */
    @Test
    @DisplayName("usage 字段 getter/setter 应正常读写")
    void shouldSetAndGetUsage() {
        FileUploadRequestDoc doc = new FileUploadRequestDoc();

        assertNull(doc.getUsage(), "初始 usage 应为 null");

        doc.setUsage("GENERIC");
        assertEquals("GENERIC", doc.getUsage(), "usage 应返回设定的 GENERIC");

        doc.setUsage("VOTE_PHOTO");
        assertEquals("VOTE_PHOTO", doc.getUsage(), "usage 应返回设定的 VOTE_PHOTO");
    }

    /**
     * 验证 clientRequestId 字段的 getter/setter 行为。
     */
    @Test
    @DisplayName("clientRequestId 字段 getter/setter 应正常读写")
    void shouldSetAndGetClientRequestId() {
        FileUploadRequestDoc doc = new FileUploadRequestDoc();

        assertNull(doc.getClientRequestId(), "初始 clientRequestId 应为 null");

        doc.setClientRequestId("upload-20260720-0001");
        assertEquals("upload-20260720-0001", doc.getClientRequestId(), "clientRequestId 应返回设定的值");

        doc.setClientRequestId("idempotent-key-abc123");
        assertEquals("idempotent-key-abc123", doc.getClientRequestId(), "clientRequestId 应返回第二次设定的值");
    }

    /**
     * 验证两个字段可独立读写，互不干扰。
     */
    @Test
    @DisplayName("usage 和 clientRequestId 应可独立读写")
    void shouldSetUsageAndClientRequestIdIndependently() {
        FileUploadRequestDoc doc = new FileUploadRequestDoc();

        doc.setUsage("VOTE_PHOTO");
        doc.setClientRequestId("upload-20260720-0001");

        assertEquals("VOTE_PHOTO", doc.getUsage(), "独立设置后 usage 应保持不变");
        assertEquals("upload-20260720-0001", doc.getClientRequestId(), "独立设置后 clientRequestId 应保持不变");
    }
}
