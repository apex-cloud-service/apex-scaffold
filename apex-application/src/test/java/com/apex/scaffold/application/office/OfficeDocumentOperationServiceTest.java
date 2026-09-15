package com.apex.scaffold.application.office;

import com.apex.scaffold.domain.office.document.Document;
import com.apex.scaffold.domain.office.document.DocumentRepository;
import com.apex.scaffold.domain.office.document.DocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 文档操作服务测试
 *
 * @author Apex Team
 * @since 1.3.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("文档操作服务测试")
class OfficeDocumentOperationServiceTest {

    @Mock
    private DocumentService feishuService;

    @Mock
    private DocumentService dingtalkService;

    @Mock
    private DocumentService wecomService;

    @Mock
    private DocumentRepository documentRepository;

    @InjectMocks
    private OfficeDocumentOperationService documentService;

    private Document mockDocument;

    @BeforeEach
    void setUp() {
        mockDocument = Document.builder()
                .id(1L)
                .tenantId(1L)
                .platform("FEISHU")
                .platformDocumentId("doc_001")
                .title("产品需求文档")
                .documentType("DOC")
                .folderId("folder_001")
                .url("https://feishu.cn/docs/doc_001")
                .ownerUserId("user_001")
                .status("ACTIVE")
                .build();
    }

    @Test
    @DisplayName("创建文档 - 飞书 - 成功")
    void testCreateDocument_Feishu_Success() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "FEISHU";

        when(feishuService.createDocument(eq(tenantId), eq(appId), any(Document.class)))
                .thenReturn(Mono.just("doc_001"));
        when(documentRepository.save(any(Document.class)))
                .thenReturn(Mono.just(mockDocument));

        // When & Then
        StepVerifier.create(documentService.createDocument(tenantId, appId, platform, mockDocument))
                .expectNext("doc_001")
                .verifyComplete();

        verify(feishuService, times(1)).createDocument(eq(tenantId), eq(appId), any(Document.class));
        verify(documentRepository, times(1)).save(any(Document.class));
    }

    @Test
    @DisplayName("创建文档 - 钉钉 - 成功")
    void testCreateDocument_Dingtalk_Success() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "DINGTALK";
        Document dingtalkDoc = mockDocument.toBuilder()
                .platform("DINGTALK")
                .url("https://dingtalk.com/docs/doc_001")
                .build();

        when(dingtalkService.createDocument(eq(tenantId), eq(appId), any(Document.class)))
                .thenReturn(Mono.just("doc_002"));
        when(documentRepository.save(any(Document.class)))
                .thenReturn(Mono.just(dingtalkDoc));

        // When & Then
        StepVerifier.create(documentService.createDocument(tenantId, appId, platform, dingtalkDoc))
                .expectNext("doc_002")
                .verifyComplete();

        verify(dingtalkService, times(1)).createDocument(eq(tenantId), eq(appId), any(Document.class));
    }

    @Test
    @DisplayName("创建文档 - 企业微信 - 成功")
    void testCreateDocument_Wecom_Success() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "WECOM";
        Document wecomDoc = mockDocument.toBuilder()
                .platform("WECOM")
                .url("https://work.weixin.qq.com/docs/doc_001")
                .build();

        when(wecomService.createDocument(eq(tenantId), eq(appId), any(Document.class)))
                .thenReturn(Mono.just("doc_003"));
        when(documentRepository.save(any(Document.class)))
                .thenReturn(Mono.just(wecomDoc));

        // When & Then
        StepVerifier.create(documentService.createDocument(tenantId, appId, platform, wecomDoc))
                .expectNext("doc_003")
                .verifyComplete();

        verify(wecomService, times(1)).createDocument(eq(tenantId), eq(appId), any(Document.class));
    }

    @Test
    @DisplayName("创建表格文档 - 成功")
    void testCreateSheetDocument_Success() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "FEISHU";
        Document sheetDoc = mockDocument.toBuilder()
                .title("数据统计表")
                .documentType("SHEET")
                .platformDocumentId("sheet_001")
                .build();

        when(feishuService.createDocument(eq(tenantId), eq(appId), any(Document.class)))
                .thenReturn(Mono.just("sheet_001"));
        when(documentRepository.save(any(Document.class)))
                .thenReturn(Mono.just(sheetDoc));

        // When & Then
        StepVerifier.create(documentService.createDocument(tenantId, appId, platform, sheetDoc))
                .expectNext("sheet_001")
                .verifyComplete();
    }

    @Test
    @DisplayName("创建演示文稿 - 成功")
    void testCreateSlideDocument_Success() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "FEISHU";
        Document slideDoc = mockDocument.toBuilder()
                .title("季度总结")
                .documentType("SLIDE")
                .platformDocumentId("slide_001")
                .build();

        when(feishuService.createDocument(eq(tenantId), eq(appId), any(Document.class)))
                .thenReturn(Mono.just("slide_001"));
        when(documentRepository.save(any(Document.class)))
                .thenReturn(Mono.just(slideDoc));

        // When & Then
        StepVerifier.create(documentService.createDocument(tenantId, appId, platform, slideDoc))
                .expectNext("slide_001")
                .verifyComplete();
    }

    @Test
    @DisplayName("查询文档 - 成功")
    void testGetDocument_Success() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "FEISHU";
        String documentId = "doc_001";

        when(feishuService.getDocument(eq(tenantId), eq(appId), eq(documentId)))
                .thenReturn(Mono.just(mockDocument));

        // When & Then
        StepVerifier.create(documentService.getDocument(tenantId, appId, platform, documentId))
                .expectNext(mockDocument)
                .verifyComplete();

        verify(feishuService, times(1)).getDocument(eq(tenantId), eq(appId), eq(documentId));
    }

    @Test
    @DisplayName("查询文档列表 - 成功")
    void testListDocuments_Success() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "FEISHU";
        String folderId = "folder_001";

        Document doc2 = mockDocument.toBuilder()
                .id(2L)
                .platformDocumentId("doc_002")
                .title("技术方案")
                .build();

        when(feishuService.listDocuments(eq(tenantId), eq(appId), eq(folderId)))
                .thenReturn(Flux.just(mockDocument, doc2));

        // When & Then
        StepVerifier.create(documentService.listDocuments(tenantId, appId, platform, folderId))
                .expectNext(mockDocument)
                .expectNext(doc2)
                .verifyComplete();

        verify(feishuService, times(1)).listDocuments(eq(tenantId), eq(appId), eq(folderId));
    }

    @Test
    @DisplayName("查询根目录文档列表 - 成功")
    void testListRootDocuments_Success() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "FEISHU";

        when(feishuService.listDocuments(eq(tenantId), eq(appId), isNull()))
                .thenReturn(Flux.just(mockDocument));

        // When & Then
        StepVerifier.create(documentService.listDocuments(tenantId, appId, platform, null))
                .expectNext(mockDocument)
                .verifyComplete();

        verify(feishuService, times(1)).listDocuments(eq(tenantId), eq(appId), isNull());
    }

    @Test
    @DisplayName("更新文档 - 成功")
    void testUpdateDocument_Success() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "FEISHU";
        String documentId = "doc_001";
        Document updatedDoc = mockDocument.toBuilder()
                .title("产品需求文档 v2.0")
                .build();

        when(feishuService.updateDocument(eq(tenantId), eq(appId), eq(documentId), any(Document.class)))
                .thenReturn(Mono.just(true));
        when(documentRepository.save(any(Document.class)))
                .thenReturn(Mono.just(updatedDoc));

        // When & Then
        StepVerifier.create(documentService.updateDocument(tenantId, appId, platform, documentId, updatedDoc))
                .expectNext(true)
                .verifyComplete();

        verify(feishuService, times(1)).updateDocument(eq(tenantId), eq(appId), eq(documentId), any(Document.class));
    }

    @Test
    @DisplayName("删除文档 - 成功")
    void testDeleteDocument_Success() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "FEISHU";
        String documentId = "doc_001";

        when(feishuService.deleteDocument(eq(tenantId), eq(appId), eq(documentId)))
                .thenReturn(Mono.just(true));
        when(documentRepository.deleteByPlatformDocumentId(eq(documentId)))
                .thenReturn(Mono.just(true));

        // When & Then
        StepVerifier.create(documentService.deleteDocument(tenantId, appId, platform, documentId))
                .expectNext(true)
                .verifyComplete();

        verify(feishuService, times(1)).deleteDocument(eq(tenantId), eq(appId), eq(documentId));
        verify(documentRepository, times(1)).deleteByPlatformDocumentId(eq(documentId));
    }

    @Test
    @DisplayName("创建文档 - 平台服务异常")
    void testCreateDocument_PlatformServiceError() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "FEISHU";

        when(feishuService.createDocument(eq(tenantId), eq(appId), any(Document.class)))
                .thenReturn(Mono.error(new RuntimeException("平台服务异常")));

        // When & Then
        StepVerifier.create(documentService.createDocument(tenantId, appId, platform, mockDocument))
                .expectError(RuntimeException.class)
                .verify();

        verify(documentRepository, never()).save(any(Document.class));
    }

    @Test
    @DisplayName("查询文档 - 文档不存在")
    void testGetDocument_NotFound() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "FEISHU";
        String documentId = "doc_not_exist";

        when(feishuService.getDocument(eq(tenantId), eq(appId), eq(documentId)))
                .thenReturn(Mono.empty());

        // When & Then
        StepVerifier.create(documentService.getDocument(tenantId, appId, platform, documentId))
                .expectComplete()
                .verify();
    }

    @Test
    @DisplayName("查询空文档列表 - 返回空")
    void testListEmptyDocuments() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "FEISHU";
        String folderId = "empty_folder";

        when(feishuService.listDocuments(eq(tenantId), eq(appId), eq(folderId)))
                .thenReturn(Flux.empty());

        // When & Then
        StepVerifier.create(documentService.listDocuments(tenantId, appId, platform, folderId))
                .expectComplete()
                .verify();
    }

    @Test
    @DisplayName("创建带初始内容的文档 - 成功")
    void testCreateDocumentWithContent_Success() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "FEISHU";
        Document docWithContent = mockDocument.toBuilder()
                .content("# 产品需求文档\n\n## 背景\n...")
                .build();

        when(feishuService.createDocument(eq(tenantId), eq(appId), any(Document.class)))
                .thenReturn(Mono.just("doc_content_001"));
        when(documentRepository.save(any(Document.class)))
                .thenReturn(Mono.just(docWithContent));

        // When & Then
        StepVerifier.create(documentService.createDocument(tenantId, appId, platform, docWithContent))
                .expectNext("doc_content_001")
                .verifyComplete();
    }

    @Test
    @DisplayName("批量查询不同类型文档 - 成功")
    void testListMultiTypeDocuments_Success() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "FEISHU";

        Document docType = mockDocument;
        Document sheetType = mockDocument.toBuilder()
                .id(2L)
                .platformDocumentId("sheet_001")
                .documentType("SHEET")
                .title("数据统计表")
                .build();
        Document slideType = mockDocument.toBuilder()
                .id(3L)
                .platformDocumentId("slide_001")
                .documentType("SLIDE")
                .title("产品介绍")
                .build();

        when(feishuService.listDocuments(eq(tenantId), eq(appId), isNull()))
                .thenReturn(Flux.just(docType, sheetType, slideType));

        // When & Then
        StepVerifier.create(documentService.listDocuments(tenantId, appId, platform, null))
                .expectNext(docType)
                .expectNext(sheetType)
                .expectNext(slideType)
                .verifyComplete();
    }
}
