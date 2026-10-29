package com.apex.scaffold.application.service;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 租户写链路守卫单元测试。
 *
 * @author Apex Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("租户写链路守卫测试")
class TenantGuardTest {

    private static final String TENANT_CODE = "apex-default";

    @Mock
    private TenantApplicationService tenantApplicationService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TenantGuard tenantGuard;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("租户上下文缺失时 assertActive 拒绝")
    void assertActiveRejectsMissingContext() {
        StepVerifier.create(tenantGuard.assertActive())
                .expectErrorMatches(TenantGuardTest::isForbidden)
                .verify();

        verifyNoInteractions(tenantApplicationService);
    }

    @Test
    @DisplayName("租户不可写时 assertActive 透传业务异常")
    void assertActivePropagatesIllegalTransition() {
        TenantContext.setTenantId(TENANT_CODE);
        when(tenantApplicationService.assertCanCreateBusinessData(TENANT_CODE))
                .thenReturn(Mono.error(new BusinessException(ResultCode.TENANT_ILLEGAL_TRANSITION, "租户已停用")));

        StepVerifier.create(tenantGuard.assertActive())
                .expectErrorMatches(error -> error instanceof BusinessException businessException
                        && businessException.getCode().equals(ResultCode.TENANT_ILLEGAL_TRANSITION.getCode()))
                .verify();
    }

    @Test
    @DisplayName("租户可写时 assertActive 放行")
    void assertActivePasses() {
        TenantContext.setTenantId(TENANT_CODE);
        when(tenantApplicationService.assertCanCreateBusinessData(TENANT_CODE)).thenReturn(Mono.empty());

        StepVerifier.create(tenantGuard.assertActive()).verifyComplete();
    }

    @Test
    @DisplayName("租户上下文缺失时 assertUserQuota 拒绝且不查询用量")
    void assertUserQuotaRejectsMissingContext() {
        StepVerifier.create(tenantGuard.assertUserQuota(1))
                .expectErrorMatches(TenantGuardTest::isForbidden)
                .verify();

        verifyNoInteractions(userRepository, tenantApplicationService);
    }

    @Test
    @DisplayName("按当前租户用量校验配额")
    void assertUserQuotaUsesCurrentTenantUsage() {
        TenantContext.setTenantId(TENANT_CODE);
        when(userRepository.countByCurrentTenant()).thenReturn(Mono.just(9L));
        when(tenantApplicationService.assertUserQuota(eq(TENANT_CODE), anyLong(), anyLong()))
                .thenReturn(Mono.empty());

        StepVerifier.create(tenantGuard.assertUserQuota(1)).verifyComplete();

        verify(tenantApplicationService).assertUserQuota(TENANT_CODE, 9L, 1L);
    }

    @Test
    @DisplayName("配额超限时 assertUserQuota 透传业务异常")
    void assertUserQuotaPropagatesExceeded() {
        TenantContext.setTenantId(TENANT_CODE);
        when(userRepository.countByCurrentTenant()).thenReturn(Mono.just(10L));
        when(tenantApplicationService.assertUserQuota(anyString(), anyLong(), anyLong()))
                .thenReturn(Mono.error(new BusinessException(ResultCode.TENANT_QUOTA_EXCEEDED, "用户数超出配额")));

        StepVerifier.create(tenantGuard.assertUserQuota(1))
                .expectErrorMatches(error -> error instanceof BusinessException businessException
                        && businessException.getCode().equals(ResultCode.TENANT_QUOTA_EXCEEDED.getCode()))
                .verify();
    }

    private static boolean isForbidden(Throwable error) {
        return error instanceof BusinessException businessException
                && businessException.getCode().equals(ResultCode.FORBIDDEN.getCode());
    }
}
