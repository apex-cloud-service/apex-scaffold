package com.apex.scaffold.office.infrastructure.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.office.domain.approval.ApprovalProcess;
import com.apex.scaffold.office.infrastructure.converter.ApprovalProcessConverter;
import com.apex.scaffold.office.infrastructure.mapper.ApprovalProcessMapper;
import com.apex.scaffold.office.infrastructure.po.ApprovalProcessPO;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

/**
 * 审批仓储租户传播、作用域校验与 JDBC 线程清理回归。
 *
 * @author Apex Team
 * @since 1.3.2
 */
class ApprovalProcessRepositoryImplTest {
    private final ApprovalProcessMapper mapper = mock(ApprovalProcessMapper.class);
    private final ApprovalProcessConverter converter = mock(ApprovalProcessConverter.class);
    private final ApprovalProcessRepositoryImpl repository =
            new ApprovalProcessRepositoryImpl(mapper, converter, mock(BlockingIoSchedulers.class));
    private final ApprovalProcess process =
            ApprovalProcess.builder().id(7L).tenantId(41L).build();
    private ExecutorService executor;
    private Scheduler jdbc;
    private MockedStatic<BlockingIoSchedulers> schedulers;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        TenantContext.resetGlobalHooks();
        executor = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "regression-jdbc"));
        jdbc = Schedulers.fromExecutorService(executor);
        schedulers = mockStatic(BlockingIoSchedulers.class);
        schedulers.when(BlockingIoSchedulers::jdbc).thenReturn(jdbc);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        schedulers.close();
        jdbc.dispose();
        executor.shutdownNow();
        TenantContext.initializeGlobalHooks();
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void rejectsMissingTenantBeforeMapperOrConverter(Operation operation) {
        StepVerifier.create(invoke(operation, 41L, process))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("租户上下文未设置"))
                .verify();
        verifyNoInteractions(mapper, converter);
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void installsTenantInsideEveryJdbcOperationAndClearsAfterSuccess(Operation operation) throws Exception {
        stubScopedMapper();
        StepVerifier.create(invoke(operation, 41L, process).contextWrite(TenantContext.withTenant("41")))
                .expectNextCount(1)
                .verifyComplete();
        assertWorkerCleared();
    }

    @Test
    void clearsTenantAfterMapperFailureAndAllowsNextTenant() throws Exception {
        when(mapper.selectByProcessInstanceId("instance", 41L)).thenAnswer(call -> {
            assertJdbcTenant("41");
            throw new IllegalStateException("mapper failed");
        });
        StepVerifier.create(repository.findByProcessInstanceId("instance").contextWrite(TenantContext.withTenant("41")))
                .expectErrorMessage("mapper failed")
                .verify();
        assertWorkerCleared();
        when(mapper.selectByInitiator("user", 42L)).thenAnswer(call -> {
            assertJdbcTenant("42");
            return List.of();
        });
        StepVerifier.create(repository.findByInitiator("user").contextWrite(TenantContext.withTenant("42")))
                .assertNext(value -> assertThat(value).isEmpty())
                .verifyComplete();
        assertWorkerCleared();
    }

    @Test
    void restoresExistingWorkerTenantAfterSuccessAndFailureWithoutSchedulerHooks() throws Exception {
        TenantContext.resetGlobalHooks();
        try {
            executor.submit(() -> TenantContext.setTenantId("worker-before")).get(5, TimeUnit.SECONDS);
            when(mapper.selectByInitiator("user", 41L)).thenAnswer(call -> {
                assertJdbcTenant("41");
                return List.of();
            });
            StepVerifier.create(repository.findByInitiator("user").contextWrite(TenantContext.withTenant("41")))
                    .expectNext(List.of())
                    .verifyComplete();
            assertThat(executor.submit(TenantContext::currentTenantId).get(5, TimeUnit.SECONDS))
                    .contains("worker-before");
            // 同一方法已有桩：用 doAnswer 覆盖，避免 when(mock.method()) 回放旧答案
            org.mockito.Mockito.doAnswer(call -> {
                        assertJdbcTenant("41");
                        throw new IllegalStateException("mapper failed");
                    })
                    .when(mapper)
                    .selectByInitiator("user", 41L);
            StepVerifier.create(repository.findByInitiator("user").contextWrite(TenantContext.withTenant("41")))
                    .expectErrorMessage("mapper failed")
                    .verify();
            assertThat(executor.submit(TenantContext::currentTenantId).get(5, TimeUnit.SECONDS))
                    .contains("worker-before");
        } finally {
            executor.submit(TenantContext::clear).get(5, TimeUnit.SECONDS);
            TenantContext.initializeGlobalHooks();
        }
    }

    @Test
    void restoresTenantAfterConverterFailure() throws Exception {
        ApprovalProcessPO po = new ApprovalProcessPO();
        when(mapper.selectByProcessInstanceId("instance", 41L)).thenAnswer(call -> {
            assertJdbcTenant("41");
            return po;
        });
        when(converter.toEntity(po)).thenAnswer(call -> {
            assertJdbcTenant("41");
            throw new IllegalArgumentException("conversion failed");
        });
        StepVerifier.create(repository.findByProcessInstanceId("instance").contextWrite(TenantContext.withTenant("41")))
                .expectErrorMessage("conversion failed")
                .verify();
        assertWorkerCleared();
    }

    @Test
    void reactorTenantTakesPrecedenceOverCallerThreadLocal() throws Exception {
        TenantContext.setTenantId("42");
        when(mapper.selectByInitiator("user", 41L)).thenAnswer(call -> {
            assertJdbcTenant("41");
            return List.of();
        });
        StepVerifier.create(repository.findByInitiator("user").contextWrite(TenantContext.withTenant("41")))
                .expectNext(List.of())
                .verifyComplete();
        assertThat(TenantContext.currentTenantId()).contains("42");
        TenantContext.clear();
        assertWorkerCleared();
    }

    @Test
    void supportsLegacyCallerThreadLocalAndRestoresIt() throws Exception {
        TenantContext.setTenantId("41");
        when(mapper.selectByInitiator("user", 41L)).thenAnswer(call -> {
            assertJdbcTenant("41");
            return List.of();
        });
        StepVerifier.create(repository.findByInitiator("user"))
                .expectNext(List.of())
                .verifyComplete();
        assertThat(TenantContext.currentTenantId()).contains("41");
        TenantContext.clear();
        assertWorkerCleared();
    }

    @ParameterizedTest
    @EnumSource(
            value = Operation.class,
            names = {"SAVE", "UPDATE", "PAGE", "COUNT"})
    void rejectsMismatchedExplicitTenantWithoutMapper(Operation operation) {
        ApprovalProcess other = ApprovalProcess.builder().id(7L).tenantId(42L).build();
        StepVerifier.create(invoke(operation, 42L, other).contextWrite(TenantContext.withTenant("41")))
                .expectError(IllegalStateException.class)
                .verify();
        verifyNoInteractions(mapper, converter);
    }

    @ParameterizedTest
    @EnumSource(
            value = Operation.class,
            names = {"SAVE", "UPDATE", "PAGE", "COUNT"})
    void rejectsNullExplicitTenantWithoutMapper(Operation operation) {
        ApprovalProcess other = ApprovalProcess.builder().id(7L).build();
        StepVerifier.create(invoke(operation, null, other).contextWrite(TenantContext.withTenant("41")))
                .expectError(IllegalStateException.class)
                .verify();
        verifyNoInteractions(mapper, converter);
    }

    @Test
    void pagedQueryPreservesTenantFiltersAndPagination() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new org.apache.ibatis.session.Configuration(), "approval-test"),
                ApprovalProcessPO.class);
        when(mapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(call -> {
            assertJdbcTenant("41");
            Page<ApprovalProcessPO> page = call.getArgument(0);
            assertThat(page.getCurrent()).isEqualTo(2);
            assertThat(page.getSize()).isEqualTo(10);
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ApprovalProcessPO> query =
                    call.getArgument(1);
            assertThat(query.getSqlSegment()).contains("tenantId", "platform", "status", "ORDER BY createdAt DESC");
            assertThat(query.getParamNameValuePairs().values()).containsExactlyInAnyOrder(41L, "FEISHU", "PENDING");
            return page.setRecords(List.of());
        });
        StepVerifier.create(repository
                        .findByConditions(
                                41L, "FEISHU", com.apex.scaffold.office.domain.approval.ApprovalStatus.PENDING, 2, 10)
                        .contextWrite(TenantContext.withTenant("41")))
                .expectNext(List.of())
                .verifyComplete();
    }

    @Test
    void rejectsInvalidNumericTenantWithoutMapper() {
        StepVerifier.create(repository.findByInitiator("user").contextWrite(TenantContext.withTenant("invalid")))
                .expectError(NumberFormatException.class)
                .verify();
        StepVerifier.create(repository
                        .findByInitiator("user")
                        .contextWrite(context -> context.put(TenantContext.REACTOR_CONTEXT_KEY, " ")))
                .expectError(IllegalStateException.class)
                .verify();
        verifyNoInteractions(mapper, converter);
    }

    private Mono<?> invoke(Operation operation, Long tenantId, ApprovalProcess entity) {
        return switch (operation) {
            case SAVE -> repository.save(entity);
            case ID -> repository.findById(7L);
            case PLATFORM -> repository.findByPlatformAndInstanceId("FEISHU", "external");
            case INSTANCE -> repository.findByProcessInstanceId("instance");
            case PAGE -> repository.findByConditions(tenantId, null, null, 1, 20);
            case COUNT -> repository.countByConditions(tenantId, null, null);
            case INITIATOR -> repository.findByInitiator("user");
            case PENDING -> repository.findPendingByApprover("user");
            case COMPLETED -> repository.findCompletedByApprover("user");
            case UPDATE -> repository.update(entity);
            case DELETE -> repository.deleteById(7L);
        };
    }

    private void stubScopedMapper() {
        ApprovalProcessPO po = new ApprovalProcessPO();
        po.setId(7L);
        po.setTenantId(41L);
        org.mockito.Mockito.lenient().when(converter.toPO(process)).thenAnswer(call -> {
            assertJdbcTenant("41");
            return po;
        });
        org.mockito.Mockito.lenient().when(converter.toEntity(po)).thenAnswer(call -> {
            assertJdbcTenant("41");
            return process;
        });
        org.mockito.Mockito.lenient().when(mapper.insert(po)).thenAnswer(call -> {
            assertJdbcTenant("41");
            return 1;
        });
        org.mockito.Mockito.lenient().when(mapper.selectOne(any(Wrapper.class))).thenAnswer(call -> {
            assertJdbcTenant("41");
            assertIdScope(call.getArgument(0));
            return po;
        });
        org.mockito.Mockito.lenient()
                .when(mapper.selectByPlatformInstanceId("external", 41L))
                .thenAnswer(call -> {
                    assertJdbcTenant("41");
                    return po;
                });
        org.mockito.Mockito.lenient()
                .when(mapper.selectByProcessInstanceId("instance", 41L))
                .thenAnswer(call -> {
                    assertJdbcTenant("41");
                    return po;
                });
        org.mockito.Mockito.lenient()
                .when(mapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenAnswer(call -> {
                    assertJdbcTenant("41");
                    return new Page<ApprovalProcessPO>(1, 20).setRecords(List.of(po));
                });
        org.mockito.Mockito.lenient()
                .when(mapper.selectCount(any(Wrapper.class)))
                .thenAnswer(call -> {
                    assertJdbcTenant("41");
                    return 1L;
                });
        org.mockito.Mockito.lenient()
                .when(mapper.selectByInitiator("user", 41L))
                .thenAnswer(call -> {
                    assertJdbcTenant("41");
                    return List.of();
                });
        org.mockito.Mockito.lenient().when(mapper.selectPendingByTenant(41L)).thenAnswer(call -> {
            assertJdbcTenant("41");
            return List.of();
        });
        org.mockito.Mockito.lenient().when(mapper.selectAllByTenant(41L)).thenAnswer(call -> {
            assertJdbcTenant("41");
            return List.of();
        });
        org.mockito.Mockito.lenient()
                .when(mapper.update(any(ApprovalProcessPO.class), any(Wrapper.class)))
                .thenAnswer(call -> {
                    assertJdbcTenant("41");
                    assertIdScope(call.getArgument(1));
                    return 1;
                });
        org.mockito.Mockito.lenient().when(mapper.delete(any(Wrapper.class))).thenAnswer(call -> {
            assertJdbcTenant("41");
            assertIdScope(call.getArgument(0));
            return 1;
        });
    }

    private void assertIdScope(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ApprovalProcessPO> wrapper) {
        assertThat(wrapper.getSqlSegment()).contains("tenant_id", "id");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(41L, 7L);
    }

    private void assertJdbcTenant(String tenant) {
        assertThat(Thread.currentThread().getName()).isEqualTo("regression-jdbc");
        assertThat(TenantContext.currentTenantId()).contains(tenant);
    }

    private void assertWorkerCleared() throws Exception {
        assertThat(executor.submit(TenantContext::currentTenantId).get(5, TimeUnit.SECONDS))
                .isEmpty();
    }

    private enum Operation {
        SAVE,
        ID,
        PLATFORM,
        INSTANCE,
        PAGE,
        COUNT,
        INITIATOR,
        PENDING,
        COMPLETED,
        UPDATE,
        DELETE
    }
}
