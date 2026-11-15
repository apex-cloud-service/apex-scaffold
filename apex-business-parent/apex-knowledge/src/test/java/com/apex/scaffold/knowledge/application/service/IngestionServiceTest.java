package com.apex.scaffold.knowledge.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.knowledge.config.KnowledgeProperties;
import com.apex.scaffold.knowledge.domain.gateway.*;
import com.apex.scaffold.knowledge.domain.model.*;
import com.apex.scaffold.knowledge.domain.service.ChunkingService;
import com.apex.scaffold.knowledge.support.EmbeddingCache;
import com.apex.scaffold.storage.StorageRegistry;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {
    @Mock
    KnowledgeDocumentRepository documents;

    @Mock
    KnowledgeSpaceRepository spaces;

    @Mock
    DocumentParserPort parser;

    @Mock
    EmbeddingPort embeddings;

    @Mock
    KnowledgeVectorIndexPort index;

    @Mock
    StorageRegistry storage;

    @Mock
    KnowledgeDocumentLifecyclePort lifecycle;

    KnowledgeProperties properties;
    IngestionService service;
    KnowledgeDocument document;
    final KnowledgeDocumentLease lease = new KnowledgeDocumentLease(
            "trusted-tenant", 1L, "owner", 1, "INGEST", LocalDateTime.now().plusMinutes(2));

    @BeforeEach
    void setUp() {
        properties = new KnowledgeProperties();
        properties.getMeilisearch().setEnabled(true);
        service = new IngestionService(
                documents,
                spaces,
                parser,
                embeddings,
                index,
                new ChunkingService(100, 10),
                properties,
                storage,
                new EmbeddingCache(64),
                lifecycle,
                new KnowledgeDocumentLeaseRunner(lifecycle, properties));
        document = KnowledgeDocument.builder()
                .id(1L)
                .spaceId(2L)
                .title("文档")
                .sourceType(KnowledgeDocument.SourceType.MANUAL)
                .contentText("知识正文内容")
                .contentType("text/plain")
                .parseStatus(KnowledgeDocument.ParseStatus.PARSED)
                .indexStatus(KnowledgeDocument.IndexStatus.PENDING)
                .chunkCount(0)
                .build();
    }

    private void stub() {
        when(lifecycle.claimIngestion(1L)).thenReturn(Mono.just(lease));
        when(documents.findById(1L)).thenReturn(Mono.just(document));
        when(lifecycle.persistDocument(eq(lease), any())).thenAnswer(i -> Mono.just(i.getArgument(1)));
        when(lifecycle.replaceChunks(eq(lease), anyList())).thenAnswer(i -> {
            List<KnowledgeChunk> chunks = i.getArgument(1);
            chunks.forEach(c -> c.setId(101L));
            return Mono.just(chunks.size());
        });
        lenient()
                .when(spaces.findById(2L))
                .thenReturn(Mono.just(KnowledgeSpace.builder().id(2L).name("空间").build()));
        lenient()
                .when(lifecycle.recordIndexTarget(eq(lease), anyString(), anyString()))
                .thenReturn(Mono.empty());
        lenient().when(index.ensureIndex(anyBoolean(), anyInt())).thenReturn(Mono.empty());
        lenient()
                .when(index.deleteDocumentChunks(anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(Mono.empty());
        lenient().when(index.upsertChunks(anyList())).thenReturn(Mono.empty());
        when(lifecycle.release(lease)).thenReturn(Mono.empty());
    }

    @Test
    void trustedReactiveTenantIsUsedAndTaskConfirmationPrecedesIndexedState() {
        stub();
        StepVerifier.create(service.ingest(1L).contextWrite(TenantContext.withTenant("trusted-tenant")))
                .verifyComplete();
        ArgumentCaptor<List<IndexedChunk>> captor = ArgumentCaptor.forClass(List.class);
        verify(index).upsertChunks(captor.capture());
        assertThat(captor.getValue()).allSatisfy(c -> {
            assertThat(c.getTenantId()).isEqualTo("trusted-tenant");
            assertThat(c.getChunkId()).isEqualTo(101L);
        });
        assertThat(document.getIndexStatus()).isEqualTo(KnowledgeDocument.IndexStatus.INDEXED_LEXICAL);
        verifyNoInteractions(parser);
    }

    @Test
    void missingTenantRejectsBeforeClaimWithoutZeroFallback() {
        TenantContext.clear();
        StepVerifier.create(service.ingest(1L))
                .expectError(com.apex.scaffold.common.exception.BusinessException.class)
                .verify();
        verifyNoInteractions(lifecycle, index, documents);
    }

    @Test
    void disabledIndexOnlyPersistsChunksNotFalseIndexed() {
        properties.getMeilisearch().setEnabled(false);
        stub();
        StepVerifier.create(service.ingest(1L).contextWrite(TenantContext.withTenant("trusted-tenant")))
                .verifyComplete();
        assertThat(document.getIndexStatus()).isEqualTo(KnowledgeDocument.IndexStatus.NONE);
        verifyNoInteractions(index);
    }

    @Test
    void deleteDuringEmbeddingRejectsLateIndexAndDatabaseWrites() {
        properties.getEmbedding().setEnabled(true);
        stub();
        AtomicBoolean deleted = new AtomicBoolean();
        Sinks.One<List<float[]>> waiting = Sinks.one();
        when(embeddings.embed(anyList())).thenReturn(waiting.asMono());
        when(lifecycle.recordIndexTarget(eq(lease), anyString(), anyString()))
                .thenAnswer(i -> deleted.get() ? Mono.error(new IllegalStateException("文档已删除")) : Mono.empty());
        when(documents.findById(1L)).thenAnswer(i -> deleted.get() ? Mono.empty() : Mono.just(document));
        StepVerifier.create(service.ingest(1L).contextWrite(TenantContext.withTenant("trusted-tenant")))
                .then(() -> {
                    deleted.set(true);
                    waiting.tryEmitValue(List.of(new float[] {1, 2}));
                })
                .verifyComplete();
        verify(index, never()).upsertChunks(anyList());
        verify(lifecycle, times(1)).persistDocument(eq(lease), any());
    }

    @Test
    void unknownIndexTaskCannotMarkIndexed() {
        stub();
        when(index.upsertChunks(anyList())).thenReturn(Mono.error(new IllegalStateException("task状态未知")));
        StepVerifier.create(service.ingest(1L).contextWrite(TenantContext.withTenant("trusted-tenant")))
                .verifyComplete();
        assertThat(document.getIndexStatus()).isEqualTo(KnowledgeDocument.IndexStatus.FAILED);
        verify(lifecycle, times(3))
                .recordIndexTarget(lease, properties.getMeilisearch().getUrl(), "apex_kb_chunks");
    }
}
