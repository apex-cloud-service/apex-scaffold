package com.apex.scaffold.file.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.apex.scaffold.file.domain.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * {@link StorageStatsService} 单元测试
 *
 * <p>验证内部统计端点的租户存储用量查询：委托 {@link DocumentRepository#sumSizeByTenant}
 * 的 file_document 聚合口径（与存储配额校验一致），不引入第二套聚合逻辑。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@DisplayName("StorageStatsService 单元测试")
class StorageStatsServiceTest {

    private final DocumentRepository documentRepository = mock(DocumentRepository.class);

    private StorageStatsService service;

    @BeforeEach
    void setUp() {
        service = new StorageStatsService(documentRepository);
    }

    @Test
    @DisplayName("tenantStorageUsage - 返回租户聚合字节数")
    void returnsAggregatedUsageFromRepository() {
        when(documentRepository.sumSizeByTenant("tenant-a")).thenReturn(Mono.just(2048L));

        service.tenantStorageUsage("tenant-a")
                .as(StepVerifier::create)
                .expectNext(2048L)
                .verifyComplete();

        verify(documentRepository).sumSizeByTenant("tenant-a");
        verifyNoMoreInteractions(documentRepository);
    }

    @Test
    @DisplayName("tenantStorageUsage - 租户无文档时返回 0")
    void returnsZeroWhenTenantHasNoDocuments() {
        when(documentRepository.sumSizeByTenant("empty-tenant")).thenReturn(Mono.just(0L));

        service.tenantStorageUsage("empty-tenant")
                .as(StepVerifier::create)
                .expectNext(0L)
                .verifyComplete();
    }

    @Test
    @DisplayName("tenantStorageUsage - 委托仓储错误并保持传播")
    void propagatesRepositoryError() {
        when(documentRepository.sumSizeByTenant("tenant-a"))
                .thenReturn(Mono.error(new IllegalStateException("jdbc unavailable")));

        service.tenantStorageUsage("tenant-a")
                .as(StepVerifier::create)
                .expectErrorMatches(error ->
                        error instanceof IllegalStateException && "jdbc unavailable".equals(error.getMessage()))
                .verify();
    }

    @Test
    @DisplayName("tenantStorageUsage - 透传仓储 Mono 不做额外包装")
    void delegatesDirectlyToRepository() {
        Mono<Long> delegate = Mono.just(4096L);
        when(documentRepository.sumSizeByTenant("tenant-a")).thenReturn(delegate);

        assertThat(service.tenantStorageUsage("tenant-a")).isSameAs(delegate);
    }
}
