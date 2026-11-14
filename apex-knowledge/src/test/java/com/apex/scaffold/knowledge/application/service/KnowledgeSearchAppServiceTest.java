package com.apex.scaffold.knowledge.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.knowledge.application.assembler.KnowledgeAssembler;
import com.apex.scaffold.knowledge.application.dto.KbSearchRequest;
import com.apex.scaffold.knowledge.application.dto.KbSearchResultDTO;
import com.apex.scaffold.knowledge.config.KnowledgeProperties;
import com.apex.scaffold.knowledge.domain.gateway.EmbeddingPort;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeVectorIndexPort;
import com.apex.scaffold.knowledge.domain.model.VectorSearchHit;
import com.apex.scaffold.knowledge.domain.model.VectorSearchQuery;
import com.apex.scaffold.knowledge.domain.model.VectorSearchResult;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 知识检索应用服务单元测试：降级链与租户 fail-closed。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeSearchAppServiceTest {

    @Mock
    private KnowledgeVectorIndexPort vectorIndexPort;

    @Mock
    private EmbeddingPort embeddingPort;

    /**
     * 构建被测服务（embedding 开关可配）。
     *
     * @param embeddingEnabled 是否启用向量化
     * @return 检索服务
     */
    private KnowledgeSearchAppService service(boolean embeddingEnabled) {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.getEmbedding().setEnabled(embeddingEnabled);
        return new KnowledgeSearchAppService(vectorIndexPort, embeddingPort, properties, new KnowledgeAssembler());
    }

    @BeforeEach
    void setUpTenant() {
        TenantContext.setTenantId("t1");
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void lexicalSearchWhenEmbeddingDisabled() {
        when(vectorIndexPort.search(any(VectorSearchQuery.class)))
                .thenReturn(Mono.just(new VectorSearchResult(
                        List.of(VectorSearchHit.builder()
                                .chunkId(9L)
                                .documentId(1L)
                                .spaceId(2L)
                                .documentTitle("文档")
                                .spaceName("空间")
                                .content("内容")
                                .score(0.5d)
                                .build()),
                        1L,
                        false)));

        StepVerifier.create(service(false).search(new KbSearchRequest("关键词", null, 1, 10)))
                .assertNext(result -> {
                    assertThat(result.total()).isEqualTo(1L);
                    assertThat(result.hybridUsed()).isFalse();
                    assertThat(result.items().get(0).documentTitle()).isEqualTo("文档");
                })
                .verifyComplete();

        verifyNoInteractions(embeddingPort);
    }

    @Test
    void embeddingFailureFallsBackToLexical() {
        when(embeddingPort.embed(any(List.class))).thenReturn(Mono.error(new IllegalStateException("供应商不可用")));
        when(vectorIndexPort.search(any(VectorSearchQuery.class)))
                .thenReturn(Mono.just(new VectorSearchResult(List.of(), 0L, false)));

        StepVerifier.create(service(true).search(new KbSearchRequest("关键词", null, 1, 10)))
                .assertNext(result -> {
                    assertThat(result.total()).isZero();
                    assertThat(result.hybridUsed()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    void missingTenantContextIsRejected() {
        TenantContext.clear();
        StepVerifier.create(service(false).search(new KbSearchRequest("关键词", null, 1, 10)))
                .expectError(BusinessException.class)
                .verify();

        verifyNoInteractions(vectorIndexPort);
    }

    @Test
    void indexDisabledPortReturnsEmptyResult() {
        when(vectorIndexPort.search(any(VectorSearchQuery.class)))
                .thenReturn(Mono.just(new VectorSearchResult(List.of(), 0L, false)));

        KbSearchResultDTO result = service(false)
                .search(new KbSearchRequest("关键词", List.of(2L), 1, 10))
                .block();
        assertThat(result).isNotNull();
        assertThat(result.items()).isEmpty();
    }
}
