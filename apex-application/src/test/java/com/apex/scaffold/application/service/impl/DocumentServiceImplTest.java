package com.apex.scaffold.application.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.apex.scaffold.application.dto.PageResponse;
import com.apex.scaffold.application.dto.data.DocumentQueryRequest;
import com.apex.scaffold.application.dto.data.DocumentRequest;
import com.apex.scaffold.application.dto.data.DocumentResponse;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.domain.model.entity.DocumentEntity;
import com.apex.scaffold.domain.repository.DocumentRepository;
import org.junit.jupiter.api.AfterEach;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentServiceImpl 单元测试")
class DocumentServiceImplTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private com.apex.scaffold.application.assembler.DocumentAssembler documentAssembler;

    @InjectMocks
    private DocumentServiceImpl documentService;

    private DocumentEntity mockEntity;
    private DocumentRequest mockRequest;
    private DocumentResponse mockResponse;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId("tenant-123");

        mockEntity = DocumentEntity.create("测试文档", "report", "tech", "文档内容", 1L, "张三", "tenant-123");
        mockEntity.setId(1L);

        mockRequest = DocumentRequest.builder()
                .title("新文档")
                .docType("report")
                .category("tech")
                .content("新文档内容")
                .build();

        mockResponse = DocumentResponse.builder()
                .id(1L)
                .title("测试文档")
                .docType("report")
                .category("tech")
                .content("文档内容")
                .authorId(1L)
                .authorName("张三")
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("创建文档")
    void testCreate() {
        when(documentRepository.save(any(DocumentEntity.class))).thenReturn(Mono.just(mockEntity));
        when(documentAssembler.toResponse(any(DocumentEntity.class))).thenReturn(mockResponse);

        Mono<DocumentResponse> result = documentService
                .create(mockRequest)
                .contextWrite(com.apex.scaffold.common.context.TenantContext.withTenant("tenant-001"));

        StepVerifier.create(result)
                .expectNextMatches(response -> response.getTitle().equals("测试文档")
                        && response.getDocType().equals("report"))
                .verifyComplete();

        verify(documentRepository, times(1)).save(any(DocumentEntity.class));
        verify(documentAssembler, times(1)).toResponse(any(DocumentEntity.class));
    }

    @Test
    @DisplayName("根据 ID 查询文档")
    void testGetById() {
        when(documentRepository.findById(1L)).thenReturn(Mono.just(mockEntity));
        when(documentAssembler.toResponse(any(DocumentEntity.class))).thenReturn(mockResponse);

        Mono<DocumentResponse> result = documentService.getById(1L);

        StepVerifier.create(result)
                .expectNextMatches(response ->
                        response.getId().equals(1L) && response.getTitle().equals("测试文档"))
                .verifyComplete();

        verify(documentRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("查询不存在的文档应抛出异常")
    void testGetByIdNotFound() {
        when(documentRepository.findById(999L)).thenReturn(Mono.empty());

        Mono<DocumentResponse> result = documentService.getById(999L);

        StepVerifier.create(result).expectError(BusinessException.class).verify();
    }

    @Test
    @DisplayName("更新文档")
    void testUpdate() {
        mockRequest.setTitle("更新后的标题");

        when(documentRepository.findById(1L)).thenReturn(Mono.just(mockEntity));
        when(documentRepository.save(any(DocumentEntity.class))).thenReturn(Mono.just(mockEntity));
        when(documentAssembler.toResponse(any(DocumentEntity.class))).thenReturn(mockResponse);

        Mono<DocumentResponse> result = documentService.update(1L, mockRequest);

        StepVerifier.create(result)
                .expectNextMatches(response -> response.getId().equals(1L))
                .verifyComplete();

        verify(documentRepository, times(1)).findById(1L);
        verify(documentRepository, times(1)).save(any(DocumentEntity.class));
        verify(documentAssembler, times(1)).toResponse(any(DocumentEntity.class));
    }

    @Test
    @DisplayName("删除文档")
    void testDelete() {
        when(documentRepository.deleteById(1L, "tenant-123")).thenReturn(Mono.empty());

        Mono<Void> result = documentService.delete(1L);

        StepVerifier.create(result).verifyComplete();

        verify(documentRepository, times(1)).deleteById(eq(1L), eq("tenant-123"));
    }

    @Test
    @DisplayName("分页查询文档")
    void testQuery() {
        DocumentQueryRequest queryRequest = DocumentQueryRequest.builder()
                .keyword("测试")
                .pageNum(1)
                .pageSize(10)
                .build();

        when(documentRepository.findAll(anyString())).thenReturn(Flux.just(mockEntity));
        when(documentAssembler.toResponse(any(DocumentEntity.class))).thenReturn(mockResponse);

        Mono<PageResponse<DocumentResponse>> result = documentService.query(queryRequest);

        StepVerifier.create(result)
                .expectNextMatches(page -> page.getTotal() > 0 && page.getList().size() > 0)
                .verifyComplete();
    }

    @Test
    @DisplayName("更新文档状态")
    void testUpdateStatus() {
        when(documentRepository.findById(1L)).thenReturn(Mono.just(mockEntity));
        when(documentRepository.save(any(DocumentEntity.class))).thenReturn(Mono.just(mockEntity));

        Mono<Void> result = documentService.updateStatus(1L, 1);

        StepVerifier.create(result).verifyComplete();

        verify(documentRepository, times(1)).findById(1L);
        verify(documentRepository, times(1)).save(any(DocumentEntity.class));
    }

    @Test
    @DisplayName("增加查看次数")
    void testIncrementViewCount() {
        when(documentRepository.findById(1L)).thenReturn(Mono.just(mockEntity));
        when(documentRepository.save(any(DocumentEntity.class))).thenReturn(Mono.just(mockEntity));

        Mono<Void> result = documentService.incrementViewCount(1L);

        StepVerifier.create(result).verifyComplete();

        verify(documentRepository, times(1)).findById(1L);
        verify(documentRepository, times(1)).save(any(DocumentEntity.class));
    }

    @Test
    @DisplayName("增加下载次数")
    void testIncrementDownloadCount() {
        when(documentRepository.findById(1L)).thenReturn(Mono.just(mockEntity));
        when(documentRepository.save(any(DocumentEntity.class))).thenReturn(Mono.just(mockEntity));

        Mono<Void> result = documentService.incrementDownloadCount(1L);

        StepVerifier.create(result).verifyComplete();

        verify(documentRepository, times(1)).findById(1L);
        verify(documentRepository, times(1)).save(any(DocumentEntity.class));
    }
}
