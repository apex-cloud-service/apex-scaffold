package com.apex.scaffold.auth.infrastructure.persistence;

import static com.apex.scaffold.auth.infrastructure.persistence.RepositoryTestSupport.condition;
import static com.apex.scaffold.auth.infrastructure.persistence.RepositoryTestSupport.entity;
import static com.apex.scaffold.auth.infrastructure.persistence.RepositoryTestSupport.value;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.apex.scaffold.auth.domain.model.entity.TotpEntity;
import com.apex.scaffold.auth.infrastructure.persistence.converter.TotpConverter;
import com.apex.scaffold.auth.infrastructure.persistence.mapper.TotpMapper;
import com.apex.scaffold.auth.infrastructure.persistence.po.TotpPO;
import com.apex.scaffold.auth.infrastructure.persistence.repository.TotpRepositoryImpl;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mapstruct.factory.Mappers;
import org.springframework.dao.DataAccessResourceFailureException;
import reactor.core.publisher.Mono;

class TotpRepositoryImplTest {
    private final TotpMapper mapper = mock(TotpMapper.class);
    private final TotpConverter converter = Mappers.getMapper(TotpConverter.class);
    private final TotpRepositoryImpl repository = new TotpRepositoryImpl(
            mapper,
            converter,
            new com.apex.scaffold.infrastructure.security.DefaultEncryptionService(
                    "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="));

    @org.junit.jupiter.api.Test
    void credentialsAreEncryptedAtRestAndRestoredOnRead() {
        TotpEntity input = TotpEntity.builder()
                .tenantId("tenant-a")
                .userId(9L)
                .secret("TOTP-SECRET")
                .backupCodes("12345678,87654321")
                .usedBackupCodes("12345678")
                .build();
        java.util.concurrent.atomic.AtomicReference<TotpPO> stored =
                new java.util.concurrent.atomic.AtomicReference<>();
        when(mapper.insert(any(TotpPO.class))).thenAnswer(invocation -> {
            TotpPO po = invocation.getArgument(0);
            assertThat(po.getSecret()).isNotEqualTo(input.getSecret());
            assertThat(po.getBackupCodes()).doesNotContain("12345678", "87654321");
            assertThat(po.getUsedBackupCodes()).doesNotContain("12345678");
            stored.set(po);
            return 1;
        });
        value(repository.save(input), input);
        when(mapper.selectByUserIdAndTenantId(9L, "tenant-a")).thenAnswer(invocation -> stored.get());
        value(repository.findByUserId(9L, "tenant-a"), input);
        assertThat(input.toString()).doesNotContain("TOTP-SECRET", "12345678", "87654321");
        assertThat(stored.get().toString()).doesNotContain(stored.get().getSecret());
    }

    @BeforeAll
    static void initializeTableMetadata() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "TotpRepositoryImplTest");
        assistant.setCurrentNamespace(TotpMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, TotpPO.class);
    }

    @ParameterizedTest
    @CsvSource({
        "tenant-a, success",
        "tenant-a, zero",
        "tenant-a, error",
        "tenant-b, success",
        "tenant-b, zero",
        "tenant-b, error"
    })
    void savePreservesTenantAndPropagatesWriteResult(String tenantId, String outcome) {
        TotpEntity input =
                TotpEntity.builder().id(null).tenantId(tenantId).userId(9L).build();
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("save failed");
        when(mapper.insert(any(TotpPO.class))).thenAnswer(invocation -> {
            TotpPO persisted = invocation.getArgument(0);
            assertThat(persisted.getTenantId()).isEqualTo(tenantId);
            assertThat(persisted.getUserId()).isEqualTo(9L);
            assertThat(persisted.getId()).isNull();
            if (outcome.equals("error")) {
                throw failure;
            }
            if (outcome.equals("success")) {
                persisted.setId(7L);
            }
            return outcome.equals("zero") ? 0 : 1;
        });

        Mono<TotpEntity> result = repository.save(input);
        verifyNoInteractions(mapper);
        if (outcome.equals("error")) {
            entity(result, outcome, failure);
        } else {
            TotpEntity expected = TotpEntity.builder()
                    .id(outcome.equals("zero") ? null : 7L)
                    .tenantId(tenantId)
                    .userId(9L)
                    .build();
            value(result, expected);
        }
        verify(mapper).insert(any(TotpPO.class));
        verifyNoMoreInteractions(mapper);
    }

    @ParameterizedTest
    @CsvSource({
        "tenant-a, success",
        "tenant-a, zero",
        "tenant-a, error",
        "tenant-b, success",
        "tenant-b, zero",
        "tenant-b, error"
    })
    void updatePreservesTenantAndPropagatesWriteResult(String tenantId, String outcome) {
        TotpEntity input =
                TotpEntity.builder().id(7L).tenantId(tenantId).userId(9L).build();
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("update failed");
        when(mapper.updateById(any(TotpPO.class))).thenAnswer(invocation -> {
            TotpPO persisted = invocation.getArgument(0);
            assertThat(persisted.getTenantId()).isEqualTo(tenantId);
            assertThat(persisted.getUserId()).isEqualTo(9L);
            assertThat(persisted.getId()).isEqualTo(7L);
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("zero") ? 0 : 1;
        });

        Mono<TotpEntity> result = repository.update(input);
        verifyNoInteractions(mapper);
        if (outcome.equals("error")) {
            entity(result, outcome, failure);
        } else if (outcome.equals("zero")) {
            reactor.test.StepVerifier.create(result)
                    .expectError(org.springframework.dao.OptimisticLockingFailureException.class)
                    .verify();
        } else {
            TotpEntity expected =
                    TotpEntity.builder().id(7L).tenantId(tenantId).userId(9L).build();
            value(result, expected);
        }
        verify(mapper).updateById(any(TotpPO.class));
        verifyNoMoreInteractions(mapper);
    }

    @ParameterizedTest
    @CsvSource({
        "tenant-a, found",
        "tenant-a, empty",
        "tenant-a, error",
        "tenant-b, found",
        "tenant-b, empty",
        "tenant-b, error"
    })
    void findByIdScopesTenantAndPropagatesResult(String tenantId, String outcome) {
        TotpPO persisted = TotpPO.builder().id(7L).tenantId(tenantId).userId(9L).build();
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("query failed");
        when(mapper.selectOne(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            condition(query, "id", "=", 7L);
            condition(query, "tenant_id", "=", tenantId);
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("empty") ? null : persisted;
        });

        Mono<TotpEntity> result = repository.findById(7L, tenantId);
        verifyNoInteractions(mapper);
        if (outcome.equals("error") || outcome.equals("empty")) {
            entity(result, outcome, failure);
        } else {
            value(result, converter.toEntity(persisted));
        }
        verify(mapper).selectOne(any(Wrapper.class));
        verifyNoMoreInteractions(mapper);
    }

    @ParameterizedTest
    @CsvSource({
        "tenant-a, found",
        "tenant-a, empty",
        "tenant-a, error",
        "tenant-b, found",
        "tenant-b, empty",
        "tenant-b, error"
    })
    void findByUserIdScopesTenantAndPropagatesResult(String tenantId, String outcome) {
        TotpPO persisted = TotpPO.builder().id(7L).tenantId(tenantId).userId(9L).build();
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("query failed");
        when(mapper.selectByUserIdAndTenantId(9L, tenantId)).thenAnswer(invocation -> {
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("empty") ? null : persisted;
        });

        Mono<TotpEntity> result = repository.findByUserId(9L, tenantId);
        verifyNoInteractions(mapper);
        if (outcome.equals("error") || outcome.equals("empty")) {
            entity(result, outcome, failure);
        } else {
            value(result, converter.toEntity(persisted));
        }
        verify(mapper).selectByUserIdAndTenantId(9L, tenantId);
        verifyNoMoreInteractions(mapper);
    }

    @ParameterizedTest
    @CsvSource({
        "tenant-a, success",
        "tenant-a, zero",
        "tenant-a, error",
        "tenant-b, success",
        "tenant-b, zero",
        "tenant-b, error"
    })
    void deleteByIdScopesTenantAndReportsAffectedRows(String tenantId, String outcome) {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("deleteById failed");
        when(mapper.delete(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            condition(query, "id", "=", 7L);
            condition(query, "tenant_id", "=", tenantId);
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("zero") ? 0 : 2;
        });

        Mono<Boolean> result = repository.deleteById(7L, tenantId);
        verifyNoInteractions(mapper);
        if (outcome.equals("error")) {
            entity(result, outcome, failure);
        } else {
            value(result, outcome.equals("success"));
        }
        verify(mapper).delete(any(Wrapper.class));
        verifyNoMoreInteractions(mapper);
    }

    @ParameterizedTest
    @CsvSource({
        "tenant-a, success",
        "tenant-a, zero",
        "tenant-a, error",
        "tenant-b, success",
        "tenant-b, zero",
        "tenant-b, error"
    })
    void deleteByUserIdScopesTenantAndReportsAffectedRows(String tenantId, String outcome) {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("deleteByUserId failed");
        when(mapper.deleteByUserIdAndTenantId(9L, tenantId)).thenAnswer(invocation -> {
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("zero") ? 0 : 2;
        });

        Mono<Boolean> result = repository.deleteByUserId(9L, tenantId);
        verifyNoInteractions(mapper);
        if (outcome.equals("error")) {
            entity(result, outcome, failure);
        } else {
            value(result, outcome.equals("success"));
        }
        verify(mapper).deleteByUserIdAndTenantId(9L, tenantId);
        verifyNoMoreInteractions(mapper);
    }

    @ParameterizedTest
    @CsvSource({
        "tenant-a, success",
        "tenant-a, zero",
        "tenant-a, error",
        "tenant-b, success",
        "tenant-b, zero",
        "tenant-b, error"
    })
    void existsByUserIdScopesTenantAndReportsAffectedRows(String tenantId, String outcome) {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("existsByUserId failed");
        when(mapper.existsByUserIdAndTenantId(9L, tenantId)).thenAnswer(invocation -> {
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("zero") ? 0 : 2;
        });

        Mono<Boolean> result = repository.existsByUserId(9L, tenantId);
        verifyNoInteractions(mapper);
        if (outcome.equals("error")) {
            entity(result, outcome, failure);
        } else {
            value(result, outcome.equals("success"));
        }
        verify(mapper).existsByUserIdAndTenantId(9L, tenantId);
        verifyNoMoreInteractions(mapper);
    }
}
