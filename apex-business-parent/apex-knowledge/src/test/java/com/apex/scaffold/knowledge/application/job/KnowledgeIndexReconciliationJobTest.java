package com.apex.scaffold.knowledge.application.job;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.knowledge.application.service.IngestionService;
import com.apex.scaffold.knowledge.config.KnowledgeProperties;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeDocumentLifecyclePort;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class KnowledgeIndexReconciliationJobTest {
    @Test
    void scopedProjectionInstallsTrustedTenantAndContinuesAfterFailure() {
        var lifecycle = mock(KnowledgeDocumentLifecyclePort.class);
        var ingestion = mock(IngestionService.class);
        var properties = new KnowledgeProperties();
        properties.getCleanup().setItemDelay(Duration.ZERO);
        when(lifecycle.scanTaskTenants(1000)).thenReturn(Mono.just(List.of("t1", "t2")));
        when(lifecycle.scanIngestion(eq("t1"), any(), anyInt())).thenReturn(Mono.just(List.of(1L, 2L)));
        when(lifecycle.scanIngestion(eq("t2"), any(), anyInt())).thenReturn(Mono.just(List.of(3L)));
        when(ingestion.ingest(anyLong()))
                .thenAnswer(i -> TenantContext.currentTenantIdReactive().flatMap(tenant -> {
                    if (i.getArgument(0).equals(1L)) return Mono.error(new IllegalStateException("单文档失败"));
                    org.assertj.core.api.Assertions.assertThat(tenant)
                            .isEqualTo(i.getArgument(0).equals(3L) ? "t2" : "t1");
                    return Mono.empty();
                }));
        StepVerifier.create(new KnowledgeIndexReconciliationJob(lifecycle, ingestion, properties).runOnce())
                .verifyComplete();
        verify(ingestion).ingest(2L);
        verify(ingestion).ingest(3L);
    }
}
