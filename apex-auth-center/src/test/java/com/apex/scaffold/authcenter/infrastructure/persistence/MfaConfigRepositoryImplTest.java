package com.apex.scaffold.authcenter.infrastructure.persistence;

import static com.apex.scaffold.authcenter.infrastructure.persistence.RepositoryTestSupport.condition;
import static com.apex.scaffold.authcenter.infrastructure.persistence.RepositoryTestSupport.entity;
import static com.apex.scaffold.authcenter.infrastructure.persistence.RepositoryTestSupport.value;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.apex.scaffold.authcenter.domain.model.entity.MfaConfigEntity;
import com.apex.scaffold.authcenter.infrastructure.persistence.converter.MfaConfigConverter;
import com.apex.scaffold.authcenter.infrastructure.persistence.mapper.MfaConfigMapper;
import com.apex.scaffold.authcenter.infrastructure.persistence.po.MfaConfigPO;
import com.apex.scaffold.authcenter.infrastructure.persistence.repository.MfaConfigRepositoryImpl;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mapstruct.factory.Mappers;
import org.springframework.dao.DataAccessResourceFailureException;
import reactor.core.publisher.Mono;

class MfaConfigRepositoryImplTest {
    private final MfaConfigMapper mapper = mock(MfaConfigMapper.class);
    private final MfaConfigConverter converter = Mappers.getMapper(MfaConfigConverter.class);
    private final MfaConfigRepositoryImpl repository = new MfaConfigRepositoryImpl(mapper, converter);

    @BeforeAll
    static void initializeTableMetadata() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "MfaConfigRepositoryImplTest");
        assistant.setCurrentNamespace(MfaConfigMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, MfaConfigPO.class);
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
        MfaConfigEntity input =
                MfaConfigEntity.builder().id(null).tenantId(tenantId).userId(9L).build();
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("save failed");
        when(mapper.insert(any(MfaConfigPO.class))).thenAnswer(invocation -> {
            MfaConfigPO persisted = invocation.getArgument(0);
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

        Mono<MfaConfigEntity> result = repository.save(input);
        verifyNoInteractions(mapper);
        if (outcome.equals("error")) {
            entity(result, outcome, failure);
        } else {
            MfaConfigEntity expected = MfaConfigEntity.builder()
                    .id(outcome.equals("zero") ? null : 7L)
                    .tenantId(tenantId)
                    .userId(9L)
                    .build();
            value(result, expected);
        }
        verify(mapper).insert(any(MfaConfigPO.class));
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
        MfaConfigEntity input =
                MfaConfigEntity.builder().id(7L).tenantId(tenantId).userId(9L).build();
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("update failed");
        when(mapper.updateById(any(MfaConfigPO.class))).thenAnswer(invocation -> {
            MfaConfigPO persisted = invocation.getArgument(0);
            assertThat(persisted.getTenantId()).isEqualTo(tenantId);
            assertThat(persisted.getUserId()).isEqualTo(9L);
            assertThat(persisted.getId()).isEqualTo(7L);
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("zero") ? 0 : 1;
        });

        Mono<MfaConfigEntity> result = repository.update(input);
        verifyNoInteractions(mapper);
        if (outcome.equals("error")) {
            entity(result, outcome, failure);
        } else {
            MfaConfigEntity expected = MfaConfigEntity.builder()
                    .id(7L)
                    .tenantId(tenantId)
                    .userId(9L)
                    .build();
            value(result, expected);
        }
        verify(mapper).updateById(any(MfaConfigPO.class));
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
        MfaConfigPO persisted =
                MfaConfigPO.builder().id(7L).tenantId(tenantId).userId(9L).build();
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

        Mono<MfaConfigEntity> result = repository.findById(7L, tenantId);
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
        MfaConfigPO persisted =
                MfaConfigPO.builder().id(7L).tenantId(tenantId).userId(9L).build();
        MfaConfigPO second =
                MfaConfigPO.builder().id(8L).tenantId(tenantId).userId(9L).build();
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("query failed");
        when(mapper.selectByUserIdAndTenantId(9L, tenantId)).thenAnswer(invocation -> {
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("empty") ? List.of() : List.of(persisted, second);
        });

        Mono<List<MfaConfigEntity>> result =
                repository.findByUserId(9L, tenantId).collectList();
        verifyNoInteractions(mapper);
        if (outcome.equals("error")) {
            entity(result, outcome, failure);
        } else {
            value(
                    result,
                    outcome.equals("empty")
                            ? List.of()
                            : List.of(converter.toEntity(persisted), converter.toEntity(second)));
        }
        verify(mapper).selectByUserIdAndTenantId(9L, tenantId);
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
    void findByUserIdAndTypeScopesTenantAndPropagatesResult(String tenantId, String outcome) {
        MfaConfigPO persisted =
                MfaConfigPO.builder().id(7L).tenantId(tenantId).userId(9L).build();
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("query failed");
        when(mapper.selectByUserIdAndTypeAndTenantId(9L, "TOTP", tenantId)).thenAnswer(invocation -> {
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("empty") ? null : persisted;
        });

        Mono<MfaConfigEntity> result = repository.findByUserIdAndType(9L, "TOTP", tenantId);
        verifyNoInteractions(mapper);
        if (outcome.equals("error") || outcome.equals("empty")) {
            entity(result, outcome, failure);
        } else {
            value(result, converter.toEntity(persisted));
        }
        verify(mapper).selectByUserIdAndTypeAndTenantId(9L, "TOTP", tenantId);
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
    void findEnabledByUserIdScopesTenantAndPropagatesResult(String tenantId, String outcome) {
        MfaConfigPO persisted =
                MfaConfigPO.builder().id(7L).tenantId(tenantId).userId(9L).build();
        MfaConfigPO second =
                MfaConfigPO.builder().id(8L).tenantId(tenantId).userId(9L).build();
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("query failed");
        when(mapper.selectEnabledByUserIdAndTenantId(9L, tenantId)).thenAnswer(invocation -> {
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("empty") ? List.of() : List.of(persisted, second);
        });

        Mono<List<MfaConfigEntity>> result =
                repository.findEnabledByUserId(9L, tenantId).collectList();
        verifyNoInteractions(mapper);
        if (outcome.equals("error")) {
            entity(result, outcome, failure);
        } else {
            value(
                    result,
                    outcome.equals("empty")
                            ? List.of()
                            : List.of(converter.toEntity(persisted), converter.toEntity(second)));
        }
        verify(mapper).selectEnabledByUserIdAndTenantId(9L, tenantId);
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
    void findRequiredByUserIdScopesTenantAndPropagatesResult(String tenantId, String outcome) {
        MfaConfigPO persisted =
                MfaConfigPO.builder().id(7L).tenantId(tenantId).userId(9L).build();
        MfaConfigPO second =
                MfaConfigPO.builder().id(8L).tenantId(tenantId).userId(9L).build();
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("query failed");
        when(mapper.selectRequiredByUserIdAndTenantId(9L, tenantId)).thenAnswer(invocation -> {
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("empty") ? List.of() : List.of(persisted, second);
        });

        Mono<List<MfaConfigEntity>> result =
                repository.findRequiredByUserId(9L, tenantId).collectList();
        verifyNoInteractions(mapper);
        if (outcome.equals("error")) {
            entity(result, outcome, failure);
        } else {
            value(
                    result,
                    outcome.equals("empty")
                            ? List.of()
                            : List.of(converter.toEntity(persisted), converter.toEntity(second)));
        }
        verify(mapper).selectRequiredByUserIdAndTenantId(9L, tenantId);
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
    void existsByUserIdAndTypeScopesTenantAndReportsAffectedRows(String tenantId, String outcome) {
        DataAccessResourceFailureException failure =
                new DataAccessResourceFailureException("existsByUserIdAndType failed");
        when(mapper.existsByUserIdAndTypeAndTenantId(9L, "TOTP", tenantId)).thenAnswer(invocation -> {
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("zero") ? 0 : 2;
        });

        Mono<Boolean> result = repository.existsByUserIdAndType(9L, "TOTP", tenantId);
        verifyNoInteractions(mapper);
        if (outcome.equals("error")) {
            entity(result, outcome, failure);
        } else {
            value(result, outcome.equals("success"));
        }
        verify(mapper).existsByUserIdAndTypeAndTenantId(9L, "TOTP", tenantId);
        verifyNoMoreInteractions(mapper);
    }
}
