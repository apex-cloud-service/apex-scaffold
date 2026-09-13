package com.apex.scaffold.domain.model.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DocumentEntity 单元测试")
class DocumentEntityTest {

    @Test
    @DisplayName("创建文档实体")
    void testCreate() {
        DocumentEntity entity = DocumentEntity.create("测试文档", "report", "tech", "文档内容", 1L, "张三", "tenant-001");

        assertNotNull(entity);
        assertEquals("测试文档", entity.getTitle());
        assertEquals("report", entity.getDocType());
        assertEquals("tech", entity.getCategory());
        assertEquals("文档内容", entity.getContent());
        assertEquals(1L, entity.getAuthorId());
        assertEquals("张三", entity.getAuthorName());
        assertEquals("tenant-001", entity.getTenantId());
        assertEquals(DocumentEntity.DocumentStatus.DRAFT.getCode(), entity.getStatus());
        assertEquals(0, entity.getViewCount());
        assertEquals(0, entity.getDownloadCount());
        assertNotNull(entity.getCreateTime());
        assertNotNull(entity.getUpdateTime());
    }

    @Test
    @DisplayName("更新文档信息")
    void testUpdateInfo() {
        DocumentEntity entity = DocumentEntity.create("原标题", "report", "tech", "原内容", 1L, "张三", "tenant-001");

        List<String> tags = Arrays.asList("标签1", "标签2");
        entity.updateInfo("新标题", "contract", "business", "新内容", tags);

        assertEquals("新标题", entity.getTitle());
        assertEquals("contract", entity.getDocType());
        assertEquals("business", entity.getCategory());
        assertEquals("新内容", entity.getContent());
        assertEquals(tags, entity.getTags());
    }

    @Test
    @DisplayName("附加文件")
    void testAttachFile() {
        DocumentEntity entity = DocumentEntity.create("测试文档", "report", "tech", "内容", 1L, "张三", "tenant-001");

        entity.attachFile("http://example.com/file.pdf", 1024L);

        assertEquals("http://example.com/file.pdf", entity.getFileUrl());
        assertEquals(1024L, entity.getFileSize());
    }

    @Test
    @DisplayName("发布文档")
    void testPublish() {
        DocumentEntity entity = DocumentEntity.create("测试文档", "report", "tech", "内容", 1L, "张三", "tenant-001");

        assertTrue(entity.isDraft());
        assertFalse(entity.isNormal());

        entity.publish();

        assertFalse(entity.isDraft());
        assertTrue(entity.isNormal());
        assertEquals(DocumentEntity.DocumentStatus.NORMAL.getCode(), entity.getStatus());
    }

    @Test
    @DisplayName("归档文档")
    void testArchive() {
        DocumentEntity entity = DocumentEntity.create("测试文档", "report", "tech", "内容", 1L, "张三", "tenant-001");

        entity.publish();
        entity.archive();

        assertFalse(entity.isDraft());
        assertFalse(entity.isNormal());
        assertTrue(entity.isArchived());
        assertEquals(DocumentEntity.DocumentStatus.ARCHIVED.getCode(), entity.getStatus());
    }

    @Test
    @DisplayName("增加查看次数")
    void testIncrementViewCount() {
        DocumentEntity entity = DocumentEntity.create("测试文档", "report", "tech", "内容", 1L, "张三", "tenant-001");

        assertEquals(0, entity.getViewCount());

        entity.incrementViewCount();
        assertEquals(1, entity.getViewCount());

        entity.incrementViewCount();
        assertEquals(2, entity.getViewCount());
    }

    @Test
    @DisplayName("增加下载次数")
    void testIncrementDownloadCount() {
        DocumentEntity entity = DocumentEntity.create("测试文档", "report", "tech", "内容", 1L, "张三", "tenant-001");

        assertEquals(0, entity.getDownloadCount());

        entity.incrementDownloadCount();
        assertEquals(1, entity.getDownloadCount());

        entity.incrementDownloadCount();
        assertEquals(2, entity.getDownloadCount());
    }

    @Test
    @DisplayName("文档状态判断")
    void testStatusCheck() {
        DocumentEntity entity = DocumentEntity.create("测试文档", "report", "tech", "内容", 1L, "张三", "tenant-001");

        assertTrue(entity.isDraft());
        assertFalse(entity.isNormal());
        assertFalse(entity.isArchived());

        entity.publish();
        assertFalse(entity.isDraft());
        assertTrue(entity.isNormal());
        assertFalse(entity.isArchived());

        entity.archive();
        assertFalse(entity.isDraft());
        assertFalse(entity.isNormal());
        assertTrue(entity.isArchived());
    }

    @Test
    @DisplayName("文档类型枚举")
    void testDocTypeEnum() {
        assertEquals("contract", DocumentEntity.DocType.CONTRACT.getCode());
        assertEquals("report", DocumentEntity.DocType.REPORT.getCode());
        assertEquals("proposal", DocumentEntity.DocType.PROPOSAL.getCode());
        assertEquals("manual", DocumentEntity.DocType.MANUAL.getCode());
        assertEquals("other", DocumentEntity.DocType.OTHER.getCode());
    }

    @Test
    @DisplayName("文档分类枚举")
    void testCategoryEnum() {
        assertEquals("tech", DocumentEntity.Category.TECH.getCode());
        assertEquals("business", DocumentEntity.Category.BUSINESS.getCode());
        assertEquals("finance", DocumentEntity.Category.FINANCE.getCode());
        assertEquals("hr", DocumentEntity.Category.HR.getCode());
    }
}
