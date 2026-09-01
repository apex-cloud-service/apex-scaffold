package com.apex.scaffold.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.dto.admin.TenantCreateRequest;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.model.entity.TenantEntity;
import com.apex.scaffold.domain.model.valueobject.TenantQuota;
import com.apex.scaffold.domain.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 租户应用服务单元测试。
 *
 * <p>覆盖租户生命周期管理（创建、停用、启用、冻结、解冻、注销、归档）与配额校验，
 * 重点验证编码唯一性、状态迁移合法性映射与配额超限错误码。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 */
@ExtendWith(MockitoExtension.class)
class TenantApplicationServiceTest {

    private static final String TENANT_CODE = "T001";

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private TenantApplicationService tenantApplicationService;

    private TenantEntity activeTenant;

    @BeforeEach
    void setUp() {
        activeTenant = TenantEntity.create(TENANT_CODE, "Acme Corp", "admin-001", TenantQuota.of(100, 10240));
        activeTenant.setId(1L);
    }

    @Test
    @DisplayName("createTenant - 成功创建租户")
    void createTenant_Success() {
        TenantCreateRequest request = new TenantCreateRequest();
        request.setTenantCode(TENANT_CODE);
        request.setTenantName("Acme Corp");
        request.setAdminUserId("admin-001");
        request.setMaxUsers(100);
        request.setMaxStorageMb(10240);

        when(tenantRepository.existsByCode(TENANT_CODE, null)).thenReturn(Mono.just(false));
        when(tenantRepository.insert(any(TenantEntity.class))).thenReturn(Mono.just(1));

        tenantApplicationService
                .createTenant(request)
                .as(StepVerifier::create)
                .assertNext(dto -> {
                    assertThat(dto.getTenantCode()).isEqualTo(TENANT_CODE);
                    assertThat(dto.getStatus()).isEqualTo("ACTIVE");
                    assertThat(dto.getMaxUsers()).isEqualTo(100);
                })
                .verifyComplete();

        verify(tenantRepository).insert(any(TenantEntity.class));
    }

    @Test
    @DisplayName("createTenant - 编码已存在时抛出异常")
    void createTenant_DuplicateCode() {
        TenantCreateRequest request = new TenantCreateRequest();
        request.setTenantCode(TENANT_CODE);
        request.setTenantName("Acme Corp");

        when(tenantRepository.existsByCode(TENANT_CODE, null)).thenReturn(Mono.just(true));

        tenantApplicationService
                .createTenant(request)
                .as(StepVerifier::create)
                .expectErrorMatches(err -> err instanceof BusinessException
                        && ((BusinessException) err).getCode().equals(ResultCode.TENANT_CODE_EXISTS.getCode()))
                .verify();
    }

    @Test
    @DisplayName("getByCode - 租户不存在时抛出 TENANT_NOT_FOUND")
    void getByCode_NotFound() {
        when(tenantRepository.findByCode("missing")).thenReturn(Mono.empty());

        tenantApplicationService
                .getByCode("missing")
                .as(StepVerifier::create)
                .expectErrorMatches(err -> err instanceof BusinessException
                        && ((BusinessException) err).getCode().equals(ResultCode.TENANT_NOT_FOUND.getCode()))
                .verify();
    }

    @Test
    @DisplayName("disable - 正常租户停用成功")
    void disable_Success() {
        when(tenantRepository.findByCode(TENANT_CODE)).thenReturn(Mono.just(activeTenant));
        when(tenantRepository.updateById(any(TenantEntity.class))).thenReturn(Mono.just(1));

        tenantApplicationService
                .disable(TENANT_CODE)
                .as(StepVerifier::create)
                .assertNext(dto -> assertThat(dto.getStatus()).isEqualTo("DISABLED"))
                .verifyComplete();
    }

    @Test
    @DisplayName("disable - 已停用租户再次停用返回 TENANT_ILLEGAL_TRANSITION")
    void disable_IllegalTransition() {
        activeTenant.disable();
        when(tenantRepository.findByCode(TENANT_CODE)).thenReturn(Mono.just(activeTenant));

        tenantApplicationService
                .disable(TENANT_CODE)
                .as(StepVerifier::create)
                .expectErrorMatches(err -> err instanceof BusinessException
                        && ((BusinessException) err).getCode().equals(ResultCode.TENANT_ILLEGAL_TRANSITION.getCode()))
                .verify();
    }

    @Test
    @DisplayName("archive - 终态归档后任何迁移被拒绝")
    void archive_TerminalState() {
        activeTenant.deactivate();
        when(tenantRepository.findByCode(TENANT_CODE)).thenReturn(Mono.just(activeTenant));
        when(tenantRepository.updateById(any(TenantEntity.class))).thenReturn(Mono.just(1));

        tenantApplicationService
                .archive(TENANT_CODE)
                .as(StepVerifier::create)
                .assertNext(dto -> assertThat(dto.getStatus()).isEqualTo("ARCHIVED"))
                .verifyComplete();

        when(tenantRepository.findByCode(TENANT_CODE)).thenReturn(Mono.just(activeTenant));
        tenantApplicationService
                .enable(TENANT_CODE)
                .as(StepVerifier::create)
                .expectErrorMatches(err -> err instanceof BusinessException
                        && ((BusinessException) err).getCode().equals(ResultCode.TENANT_ILLEGAL_TRANSITION.getCode()))
                .verify();
    }

    @Test
    @DisplayName("assertUserQuota - 配额超限返回 TENANT_QUOTA_EXCEEDED")
    void assertUserQuota_Exceeded() {
        when(tenantRepository.findByCode(TENANT_CODE)).thenReturn(Mono.just(activeTenant));

        tenantApplicationService
                .assertUserQuota(TENANT_CODE, 100L, 1L)
                .as(StepVerifier::create)
                .expectErrorMatches(err -> err instanceof BusinessException
                        && ((BusinessException) err).getCode().equals(ResultCode.TENANT_QUOTA_EXCEEDED.getCode()))
                .verify();
    }

    @Test
    @DisplayName("assertUserQuota - 配额内增长通过")
    void assertUserQuota_WithinQuota() {
        when(tenantRepository.findByCode(TENANT_CODE)).thenReturn(Mono.just(activeTenant));

        tenantApplicationService
                .assertUserQuota(TENANT_CODE, 50L, 1L)
                .as(StepVerifier::create)
                .verifyComplete();
    }

    @Test
    @DisplayName("assertUserQuota - 停用租户拒绝配额增长")
    void assertUserQuota_DisabledTenant() {
        activeTenant.disable();
        when(tenantRepository.findByCode(TENANT_CODE)).thenReturn(Mono.just(activeTenant));

        tenantApplicationService
                .assertUserQuota(TENANT_CODE, 10L, 1L)
                .as(StepVerifier::create)
                .expectErrorMatches(err -> err instanceof BusinessException
                        && ((BusinessException) err).getCode().equals(ResultCode.TENANT_ILLEGAL_TRANSITION.getCode()))
                .verify();
    }

    @Test
    @DisplayName("assertCanCreateBusinessData - 停用租户拒绝创建业务数据")
    void assertCanCreateBusinessData_Disabled() {
        activeTenant.disable();
        when(tenantRepository.findByCode(TENANT_CODE)).thenReturn(Mono.just(activeTenant));

        tenantApplicationService
                .assertCanCreateBusinessData(TENANT_CODE)
                .as(StepVerifier::create)
                .expectErrorMatches(err -> err instanceof BusinessException
                        && ((BusinessException) err).getCode().equals(ResultCode.TENANT_ILLEGAL_TRANSITION.getCode()))
                .verify();
    }

    @Test
    @DisplayName("assertCanCreateBusinessData - 正常租户允许创建业务数据")
    void assertCanCreateBusinessData_Active() {
        when(tenantRepository.findByCode(TENANT_CODE)).thenReturn(Mono.just(activeTenant));

        tenantApplicationService
                .assertCanCreateBusinessData(TENANT_CODE)
                .as(StepVerifier::create)
                .verifyComplete();
    }

    @Test
    @DisplayName("updateQuota - 更新配额成功")
    void updateQuota_Success() {
        when(tenantRepository.findByCode(TENANT_CODE)).thenReturn(Mono.just(activeTenant));
        when(tenantRepository.updateById(any(TenantEntity.class))).thenReturn(Mono.just(1));

        tenantApplicationService
                .updateQuota(TENANT_CODE, 200, 20480)
                .as(StepVerifier::create)
                .assertNext(dto -> {
                    assertThat(dto.getMaxUsers()).isEqualTo(200);
                    assertThat(dto.getMaxStorageMb()).isEqualTo(20480);
                })
                .verifyComplete();

        verify(tenantRepository).updateById(eq(activeTenant));
    }
}
