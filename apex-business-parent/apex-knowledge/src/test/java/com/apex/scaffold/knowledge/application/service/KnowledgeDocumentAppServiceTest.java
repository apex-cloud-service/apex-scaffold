package com.apex.scaffold.knowledge.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.knowledge.application.assembler.KnowledgeAssembler;
import com.apex.scaffold.knowledge.config.KnowledgeProperties;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeChunkRepository;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeDocumentRepository;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeSpaceRepository;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeVectorIndexPort;
import com.apex.scaffold.knowledge.domain.model.KnowledgeDocument;
import com.apex.scaffold.knowledge.domain.model.KnowledgeSpace;
import com.apex.scaffold.storage.ObjectStorage;
import com.apex.scaffold.storage.StorageRegistry;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 知识文档应用服务测试：登记/校验/删除/重建与桥接触发边界。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeDocumentAppServiceTest {

    @Mock
    private KnowledgeDocumentRepository documentRepository;

    @Mock
    private KnowledgeSpaceRepository spaceRepository;

    @Mock
    private KnowledgeChunkRepository chunkRepository;

    @Mock
    private KnowledgeVectorIndexPort vectorIndexPort;

    @Mock
    private IngestionService ingestionService;

    @Mock
    private StorageRegistry storageRegistry;

    @Mock
    private ObjectStorage objectStorage;

    private KnowledgeDocumentAppService service;

    @BeforeEach
    void setUpService() {
        KnowledgeProperties properties = new KnowledgeProperties();
        service = new KnowledgeDocumentAppService(
                documentRepository,
                spaceRepository,
                chunkRepository,
                vectorIndexPort,
                properties,
                storageRegistry,
                ingestionService,
                new KnowledgeAssembler());
        lenient().when(storageRegistry.defaultStorage()).thenReturn(objectStorage);
        lenient()
                .when(objectStorage.upload(anyString(), anyString(), any(), anyString()))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(1)));
        lenient().when(ingestionService.ingest(anyLong())).thenReturn(Mono.empty());
        lenient()
                .when(spaceRepository.findById(anyLong()))
                .thenReturn(Mono.just(
                        KnowledgeSpace.builder().id(2L).name("空间").status(1).build()));
        lenient()
                .when(documentRepository.update(any(KnowledgeDocument.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        TenantContext.setTenantId("t1");
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    /**
     * 模拟仓储 insert 的落库回读语义：为入参文档回填雪花 ID。
     *
     * @param invocation Mockito 调用
     * @return 带 ID 的文档
     */
    private Mono<KnowledgeDocument> copyWithGeneratedId(org.mockito.invocation.InvocationOnMock invocation) {
        KnowledgeDocument source = invocation.getArgument(0);
        return Mono.just(KnowledgeDocument.builder()
                .id(5L)
                .spaceId(source.getSpaceId())
                .title(source.getTitle())
                .sourceType(source.getSourceType())
                .contentType(source.getContentType())
                .objectKey(source.getObjectKey())
                .fileSize(source.getFileSize())
                .contentText(source.getContentText())
                .parseStatus(source.getParseStatus())
                .parseError(source.getParseError())
                .chunkCount(source.getChunkCount())
                .indexStatus(source.getIndexStatus())
                .build());
    }

    /**
     * 构建已解析文档。
     *
     * @return 文档模型
     */
    private KnowledgeDocument parsedDocument() {
        return KnowledgeDocument.builder()
                .id(1L)
                .spaceId(2L)
                .title("文档")
                .sourceType(KnowledgeDocument.SourceType.UPLOAD)
                .contentType("application/pdf")
                .objectKey("kb/x")
                .contentText("文档正文内容")
                .parseStatus(KnowledgeDocument.ParseStatus.PARSED)
                .indexStatus(KnowledgeDocument.IndexStatus.INDEXED)
                .chunkCount(2)
                .build();
    }

    @Test
    void registerManualRegistersParsedDocumentAndTriggersIngestion() {
        when(documentRepository.insert(any(KnowledgeDocument.class))).thenAnswer(this::copyWithGeneratedId);

        StepVerifier.create(service.registerManual(
                        new com.apex.scaffold.knowledge.application.dto.KnowledgeManualDocumentRequest(
                                2L, "在线文档", "正文内容")))
                .assertNext(dto -> {
                    assertThat(dto.sourceType()).isEqualTo("MANUAL");
                    assertThat(dto.parseStatus()).isEqualTo("PARSED");
                })
                .verifyComplete();

        verify(ingestionService).ingest(anyLong());
    }

    @Test
    void registerUploadPersistsObjectKey() {
        when(documentRepository.insert(any(KnowledgeDocument.class))).thenAnswer(this::copyWithGeneratedId);

        StepVerifier.create(service.registerUpload(2L, "手册.pdf", "application/pdf", "内容".getBytes()))
                .assertNext(dto -> assertThat(dto.parseStatus()).isEqualTo("PENDING"))
                .verifyComplete();

        ArgumentCaptor<KnowledgeDocument> inserted = ArgumentCaptor.forClass(KnowledgeDocument.class);
        verify(documentRepository).insert(inserted.capture());
        assertThat(inserted.getValue().getObjectKey()).startsWith("kb/");
        assertThat(inserted.getValue().getFileSize()).isEqualTo("内容".getBytes().length);
    }

    @Test
    void registerUploadRejectsUnsupportedTypeAndOversize() {
        StepVerifier.create(Mono.defer(
                        () -> service.registerUpload(2L, "恶意.exe", "application/octet-stream", new byte[] {1})))
                .expectError(BusinessException.class)
                .verify();

        StepVerifier.create(Mono.defer(
                        () -> service.registerUpload(2L, "大文件.pdf", "application/pdf", new byte[51 * 1024 * 1024])))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    void registerUploadRejectsInactiveSpace() {
        when(spaceRepository.findById(anyLong()))
                .thenReturn(Mono.just(
                        KnowledgeSpace.builder().id(2L).name("停用空间").status(0).build()));

        StepVerifier.create(service.registerUpload(2L, "手册.pdf", "application/pdf", new byte[] {1}))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    void deleteRemovesChunksThenIndexThenDocument() {
        KnowledgeDocument document = parsedDocument();
        when(documentRepository.findById(1L)).thenReturn(Mono.just(document));
        when(chunkRepository.deleteByDocument(1L)).thenReturn(Mono.just(true));
        when(vectorIndexPort.deleteDocumentChunks("t1", 1L)).thenReturn(Mono.empty());
        when(documentRepository.deleteById(1L)).thenReturn(Mono.just(true));

        StepVerifier.create(service.delete(1L)).expectNext(true).verifyComplete();

        verify(chunkRepository).deleteByDocument(1L);
        verify(vectorIndexPort).deleteDocumentChunks("t1", 1L);
    }

    @Test
    void deleteProceedsWhenIndexCleanupFails() {
        KnowledgeDocument document = parsedDocument();
        when(documentRepository.findById(1L)).thenReturn(Mono.just(document));
        when(chunkRepository.deleteByDocument(1L)).thenReturn(Mono.just(true));
        when(vectorIndexPort.deleteDocumentChunks("t1", 1L)).thenReturn(Mono.error(new IllegalStateException("索引不可用")));
        when(documentRepository.deleteById(1L)).thenReturn(Mono.just(true));

        StepVerifier.create(service.delete(1L)).expectNext(true).verifyComplete();
    }

    @Test
    void reindexResetsStatusesAndTriggersIngestion() {
        KnowledgeDocument document = parsedDocument();
        when(documentRepository.findById(1L)).thenReturn(Mono.just(document));

        StepVerifier.create(service.reindex(1L))
                .assertNext(dto -> assertThat(dto.parseStatus()).isEqualTo("PARSED"))
                .verifyComplete();

        ArgumentCaptor<KnowledgeDocument> updated = ArgumentCaptor.forClass(KnowledgeDocument.class);
        verify(documentRepository).update(updated.capture());
        assertThat(updated.getValue().getIndexStatus()).isEqualTo(KnowledgeDocument.IndexStatus.PENDING);
        verify(ingestionService).ingest(1L);
    }

    @Test
    void findPageAndDetailMapThroughAssembler() {
        when(documentRepository.findPage(anyLong(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(
                        Mono.just(com.apex.scaffold.common.dto.PageResponse.of(List.of(parsedDocument()), 1L, 1, 20)));
        StepVerifier.create(service.findPage(2L, "PARSED", "文档", 1, 20))
                .assertNext(
                        page -> assertThat(page.getList().getFirst().title()).isEqualTo("文档"))
                .verifyComplete();

        when(documentRepository.findById(1L)).thenReturn(Mono.just(parsedDocument()));
        when(chunkRepository.findByDocument(1L))
                .thenReturn(Mono.just(List.of(com.apex.scaffold.knowledge.domain.model.KnowledgeChunk.builder()
                        .id(3L)
                        .seq(0)
                        .content("分块")
                        .tokenEstimate(2)
                        .build())));
        StepVerifier.create(service.findById(1L))
                .assertNext(detail -> {
                    assertThat(detail.document().id()).isEqualTo(1L);
                    assertThat(detail.chunks()).hasSize(1);
                })
                .verifyComplete();

        when(documentRepository.findById(99L)).thenReturn(Mono.empty());
        StepVerifier.create(service.findById(99L))
                .expectError(BusinessException.class)
                .verify();
    }
}
