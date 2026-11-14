package com.apex.scaffold.knowledge.application.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.knowledge.application.service.IngestionService;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeDocumentRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

/**
 * 知识摄取校准 Job 测试：滞留/失败扫描与单文档失败不中断批次。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeIndexReconciliationJobTest {

    @Mock
    private KnowledgeDocumentRepository documentRepository;

    @Mock
    private IngestionService ingestionService;

    @Test
    void reconcileScansStaleAndFailedDocuments() {
        KnowledgeIndexReconciliationJob job = new KnowledgeIndexReconciliationJob(documentRepository, ingestionService);
        when(documentRepository.findIdsByStatusAndUpdatedBefore(anyList(), any(LocalDateTime.class), anyInt()))
                .thenReturn(Mono.just(List.of(1L, 2L)));
        when(documentRepository.findIdsByIndexStatusAndUpdatedBefore(anyList(), any(LocalDateTime.class), anyInt()))
                .thenReturn(Mono.just(List.of(3L)));
        when(ingestionService.ingest(org.mockito.ArgumentMatchers.anyLong())).thenReturn(Mono.empty());

        job.reconcile();

        verify(ingestionService).ingest(1L);
        verify(ingestionService).ingest(2L);
        verify(ingestionService).ingest(3L);
    }

    @Test
    void singleIngestionFailureDoesNotAbortBatch() {
        KnowledgeIndexReconciliationJob job = new KnowledgeIndexReconciliationJob(documentRepository, ingestionService);
        when(documentRepository.findIdsByStatusAndUpdatedBefore(anyList(), any(LocalDateTime.class), anyInt()))
                .thenReturn(Mono.just(List.of(1L, 2L)));
        when(documentRepository.findIdsByIndexStatusAndUpdatedBefore(anyList(), any(LocalDateTime.class), anyInt()))
                .thenReturn(Mono.just(List.of()));
        lenient().when(ingestionService.ingest(1L)).thenReturn(Mono.error(new IllegalStateException("索引不可用")));
        lenient().when(ingestionService.ingest(2L)).thenReturn(Mono.empty());

        // 不抛异常即代表批次继续推进
        job.reconcile();
        assertThat(true).isTrue();
        verify(ingestionService).ingest(2L);
    }

    @Test
    void emptyScanYieldsZeroCounts() {
        KnowledgeIndexReconciliationJob job = new KnowledgeIndexReconciliationJob(documentRepository, ingestionService);
        when(documentRepository.findIdsByStatusAndUpdatedBefore(anyList(), any(LocalDateTime.class), anyInt()))
                .thenReturn(Mono.just(List.of()));
        when(documentRepository.findIdsByIndexStatusAndUpdatedBefore(anyList(), any(LocalDateTime.class), anyInt()))
                .thenReturn(Mono.just(List.of()));

        job.reconcile();

        verify(ingestionService, org.mockito.Mockito.never()).ingest(org.mockito.ArgumentMatchers.anyLong());
    }
}
