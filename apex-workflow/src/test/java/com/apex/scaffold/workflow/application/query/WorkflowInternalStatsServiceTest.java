package com.apex.scaffold.workflow.application.query;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.workflow.infrastructure.persistence.mapper.ProcessInstanceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

/**
 * {@link WorkflowInternalStatsService} 单元测试
 *
 * <p>测试内部统计端点的租户维度进行中实例数查询：可信租户上下文解析、
 * 计数映射与缺失上下文 fail-closed 行为。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.2
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowInternalStatsService 单元测试")
class WorkflowInternalStatsServiceTest {

    /** 测试租户。 */
    private static final String TENANT = "tenant-a";

    /** 可信租户上下文。 */
    private static final Context TENANT_CONTEXT = Context.of(TenantContext.REACTOR_CONTEXT_KEY, TENANT);

    @Mock
    private ProcessInstanceMapper processInstanceMapper;

    @InjectMocks
    private WorkflowInternalStatsService statsService;

    @Test
    @DisplayName("countInProgressInstances - 成功返回进行中实例数")
    void testCountInProgress_Success() {
        when(processInstanceMapper.countByStatus(TENANT, "RUNNING")).thenReturn(7);

        statsService
                .countInProgressInstances()
                .contextWrite(TENANT_CONTEXT)
                .as(StepVerifier::create)
                .expectNext(7L)
                .verifyComplete();
    }

    @Test
    @DisplayName("countInProgressInstances - 计数为 null 时映射为 0")
    void testCountInProgress_NullCountMapsToZero() {
        when(processInstanceMapper.countByStatus(TENANT, "RUNNING")).thenReturn(null);

        statsService
                .countInProgressInstances()
                .contextWrite(TENANT_CONTEXT)
                .as(StepVerifier::create)
                .expectNext(0L)
                .verifyComplete();
    }

    @Test
    @DisplayName("countInProgressInstances - 缺失租户上下文时 fail-closed")
    void testCountInProgress_MissingTenantContextFails() {
        statsService
                .countInProgressInstances()
                .as(StepVerifier::create)
                .expectErrorMatches(error -> error instanceof IllegalStateException
                        && error.getMessage().contains("租户上下文"))
                .verify();
    }

    @Test
    @DisplayName("countInProgressInstances - 空白租户上下文同样 fail-closed")
    void testCountInProgress_BlankTenantContextFails() {
        statsService
                .countInProgressInstances()
                .contextWrite(Context.of(TenantContext.REACTOR_CONTEXT_KEY, "  "))
                .as(StepVerifier::create)
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    @DisplayName("countInProgressInstances - 使用 RUNNING 状态口径查询")
    void testCountInProgress_UsesRunningStatus() {
        when(processInstanceMapper.countByStatus(TENANT, "RUNNING")).thenReturn(3);

        statsService
                .countInProgressInstances()
                .contextWrite(TENANT_CONTEXT)
                .as(StepVerifier::create)
                .expectNext(3L)
                .verifyComplete();

        verify(processInstanceMapper).countByStatus(eq(TENANT), eq("RUNNING"));
    }
}
