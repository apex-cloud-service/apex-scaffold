package com.apex.scaffold.auth.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.apex.scaffold.auth.infrastructure.persistence.converter.UserConverter;
import com.apex.scaffold.auth.infrastructure.persistence.mapper.UserMapper;
import com.apex.scaffold.auth.infrastructure.persistence.repository.UserRepositoryImpl;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import reactor.test.StepVerifier;

class RepositoryIsolationTest {
    private final UserMapper mapper = mock(UserMapper.class);
    private final UserRepositoryImpl repository =
            new UserRepositoryImpl(mapper, Mappers.getMapper(UserConverter.class));

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void rejectsMissingTenantBeforeDatabaseAccess() {
        StepVerifier.create(repository.findById(1L))
                .expectError(BusinessException.class)
                .verify();
        verifyNoInteractions(mapper);
    }

    @Test
    void rejectsDifferentTenantBeforeDatabaseAccess() {
        StepVerifier.create(repository
                        .findByUsername("alice", "tenant-b")
                        .contextWrite(TenantContext.withTenant("tenant-a")))
                .expectError(BusinessException.class)
                .verify();
        verifyNoInteractions(mapper);
    }

    @Test
    void defersDatabaseAccessAndScopesOnJdbcThread() {
        AtomicReference<String> thread = new AtomicReference<>();
        when(mapper.selectOne(any(Wrapper.class))).thenAnswer(invocation -> {
            thread.set(Thread.currentThread().getName());
            Wrapper<?> query = invocation.getArgument(0);
            assertThat(query.getSqlSegment()).contains("tenant_id", "id");
            assertThat(TenantContext.currentTenantId()).contains("tenant-a");
            return null;
        });
        reactor.core.publisher.Mono<com.apex.scaffold.auth.domain.model.entity.UserEntity> result =
                repository.findById(1L);
        verifyNoInteractions(mapper);
        StepVerifier.create(result.contextWrite(TenantContext.withTenant("tenant-a")))
                .verifyComplete();
        assertThat(thread.get()).isNotEqualTo(Thread.currentThread().getName());
        verify(mapper).selectOne(any(Wrapper.class));
    }

    @Test
    void updatesAuditTimeWithoutOverwritingCreationTime() {
        java.time.LocalDateTime old = java.time.LocalDateTime.of(2020, 1, 1, 0, 0);
        com.apex.scaffold.auth.domain.model.entity.UserEntity entity =
                com.apex.scaffold.auth.domain.model.entity.UserEntity.builder()
                        .id(1L)
                        .tenantId("tenant-a")
                        .createdAt(old)
                        .updatedAt(old)
                        .build();
        when(mapper.update(any(com.apex.scaffold.auth.infrastructure.persistence.po.UserPO.class), any(Wrapper.class)))
                .thenAnswer(invocation -> {
                    com.apex.scaffold.auth.infrastructure.persistence.po.UserPO po = invocation.getArgument(0);
                    assertThat(po.getCreatedAt()).isNull();
                    assertThat(po.getUpdatedAt()).isAfter(old);
                    return 1;
                });
        StepVerifier.create(repository.update(entity).contextWrite(TenantContext.withTenant("tenant-a")))
                .verifyComplete();
    }
}
