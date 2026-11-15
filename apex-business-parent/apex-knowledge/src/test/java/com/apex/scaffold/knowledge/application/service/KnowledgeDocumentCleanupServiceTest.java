package com.apex.scaffold.knowledge.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

import com.apex.scaffold.knowledge.config.KnowledgeProperties;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeDocumentLifecyclePort;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeVectorIndexPort;
import com.apex.scaffold.knowledge.domain.model.KnowledgeDocumentCleanup;
import com.apex.scaffold.knowledge.domain.model.KnowledgeDocumentLease;
import com.apex.scaffold.storage.ObjectStorage;
import com.apex.scaffold.storage.StorageRegistry;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class KnowledgeDocumentCleanupServiceTest {

    @Mock
    KnowledgeDocumentLifecyclePort lifecycle;

    @Mock
    KnowledgeVectorIndexPort index;

    @Mock
    StorageRegistry registry;

    @Mock
    ObjectStorage storage;

    KnowledgeProperties properties;
    KnowledgeDocumentCleanupService service;
    final KnowledgeDocumentLease lease = new KnowledgeDocumentLease(
            "t1", 9L, "owner", 1, "CLEANUP", LocalDateTime.now().plusMinutes(2));

    @BeforeEach
    void setUp() {
        properties = new KnowledgeProperties();
        service = new KnowledgeDocumentCleanupService(
                lifecycle, index, registry, properties, new KnowledgeDocumentLeaseRunner(lifecycle, properties));
        when(lifecycle.claimCleanup("t1", 9L)).thenReturn(Mono.just(lease));
        lenient().when(lifecycle.deleteChunks(lease)).thenReturn(Mono.empty());
        lenient().when(lifecycle.confirmIndex(lease)).thenReturn(Mono.empty());
        lenient().when(lifecycle.confirmObject(lease)).thenReturn(Mono.empty());
        lenient().when(lifecycle.finishCleanup(any(), any())).thenReturn(Mono.empty());
        lenient().when(lifecycle.finishCleanup(any(), isNull())).thenReturn(Mono.empty());
    }

    private KnowledgeDocumentCleanup cleanup(String provider, boolean indexed) {
        return KnowledgeDocumentCleanup.builder()
                .documentId(9L)
                .tenantId("t1")
                .title("文档")
                .mode(KnowledgeDocumentCleanup.Mode.DELETE)
                .status(KnowledgeDocumentCleanup.Status.PROCESSING)
                .provider(provider)
                .bucket(provider == null ? null : "original")
                .objectKey("kb/key")
                .objectRequired(true)
                .indexEndpoint("http://meili")
                .indexName("old_kb_chunks")
                .indexRequired(indexed)
                .build();
    }

    @Test
    void cleanupUsesSnapshotAndConfirmsIndexBeforeCompletion() {
        properties.getMeilisearch().setEnabled(true);
        when(lifecycle.findCleanup(9L)).thenReturn(Mono.just(cleanup("local", true)));
        when(index.deleteDocumentChunks("http://meili", "old_kb_chunks", "t1", 9L))
                .thenReturn(Mono.empty());
        when(registry.require("local")).thenReturn(storage);
        when(storage.delete("original", "kb/key")).thenReturn(Mono.just(false));
        StepVerifier.create(service.process("t1", 9L)).verifyComplete();
        var ordered = inOrder(lifecycle, index, storage);
        ordered.verify(lifecycle).claimCleanup("t1", 9L);
        ordered.verify(lifecycle).findCleanup(9L);
        ordered.verify(lifecycle).deleteChunks(lease);
        ordered.verify(index).deleteDocumentChunks("http://meili", "old_kb_chunks", "t1", 9L);
        ordered.verify(lifecycle).confirmIndex(lease);
        ordered.verify(storage).delete("original", "kb/key");
        ordered.verify(lifecycle).confirmObject(lease);
        ordered.verify(lifecycle).finishCleanup(lease, null);
        verify(registry, never()).defaultStorage();
    }

    @Test
    void disabledRequiredIndexCannotCompleteButObjectStillCleans() {
        when(lifecycle.findCleanup(9L)).thenReturn(Mono.just(cleanup("local", true)));
        when(registry.require("local")).thenReturn(storage);
        when(storage.delete(anyString(), anyString())).thenReturn(Mono.just(true));
        StepVerifier.create(service.process("t1", 9L)).verifyComplete();
        verify(lifecycle).finishCleanup(eq(lease), contains("索引能力未启用"));
        verify(lifecycle, never()).confirmIndex(any());
        verify(lifecycle, never()).finishCleanup(eq(lease), isNull());
    }

    @Test
    void historicalUploadWithoutSnapshotFailsWithoutUsingDefaultStorage() {
        properties.getMeilisearch().setEnabled(true);
        when(lifecycle.findCleanup(9L)).thenReturn(Mono.just(cleanup(null, true)));
        when(index.deleteDocumentChunks(anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(Mono.empty());
        StepVerifier.create(service.process("t1", 9L)).verifyComplete();
        verify(lifecycle).confirmIndex(lease);
        verify(lifecycle).finishCleanup(eq(lease), contains("历史对象缺少定位快照"));
        verifyNoInteractions(registry, storage);
    }

    @Test
    void confirmedObjectIsNotDeletedAgainDuringTombstoneReview() {
        properties.getMeilisearch().setEnabled(true);
        when(lifecycle.findCleanup(9L))
                .thenReturn(Mono.just(KnowledgeDocumentCleanup.builder()
                        .documentId(9L)
                        .tenantId("t1")
                        .title("文档")
                        .mode(KnowledgeDocumentCleanup.Mode.DELETE)
                        .status(KnowledgeDocumentCleanup.Status.COMPLETED)
                        .objectRequired(true)
                        .objectRemoved(true)
                        .provider("local")
                        .bucket("original")
                        .objectKey("kb/key")
                        .indexRequired(true)
                        .indexEndpoint("http://meili")
                        .indexName("old_kb_chunks")
                        .build()));
        when(index.deleteDocumentChunks(anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(Mono.empty());
        StepVerifier.create(service.process("t1", 9L)).verifyComplete();
        verifyNoInteractions(registry, storage);
        verify(lifecycle).finishCleanup(lease, null);
    }

    @Test
    void unknownTaskStoresFailureCredentialAndNextPeriodicClaimRepeatsDelete() {
        properties.getMeilisearch().setEnabled(true);
        var cleanup = KnowledgeDocumentCleanup.builder()
                .documentId(9L)
                .tenantId("t1")
                .title("文档")
                .mode(KnowledgeDocumentCleanup.Mode.DELETE)
                .status(KnowledgeDocumentCleanup.Status.COMPLETED)
                .indexRequired(true)
                .indexConfirmed(true)
                .indexEndpoint("http://meili")
                .indexName("old_kb_chunks")
                .build();
        when(lifecycle.findCleanup(9L)).thenReturn(Mono.just(cleanup));
        when(index.deleteDocumentChunks(anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(Mono.error(new IllegalStateException("unknown task")), Mono.empty());
        StepVerifier.create(service.process("t1", 9L)).verifyComplete();
        StepVerifier.create(service.process("t1", 9L)).verifyComplete();
        verify(index, times(2)).deleteDocumentChunks("http://meili", "old_kb_chunks", "t1", 9L);
        verify(lifecycle).finishCleanup(eq(lease), contains("索引清理未确认"));
        verify(lifecycle).finishCleanup(lease, null);
    }
}
