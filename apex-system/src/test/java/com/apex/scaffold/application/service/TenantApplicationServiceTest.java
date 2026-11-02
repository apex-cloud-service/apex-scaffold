package com.apex.scaffold.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.assembler.TenantAssembler;
import com.apex.scaffold.application.dto.admin.TenantCreateRequest;
import com.apex.scaffold.application.dto.admin.TenantDTO;
import com.apex.scaffold.application.dto.admin.TenantPageQuery;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.contract.event.TenantChangedEvent;
import com.apex.scaffold.domain.model.entity.TenantEntity;
import com.apex.scaffold.domain.model.valueobject.TenantQuota;
import com.apex.scaffold.domain.repository.TenantRepository;
import com.apex.scaffold.domain.service.DomainEventPublisher;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 租户应用服务单元测试。
 *
 * <p>覆盖平台租户目录分页、开通、详情、生命周期动作与配额管理，重点验证
 * 编码唯一性、状态迁移合法性映射、乐观锁版本冲突与配额超限错误码。</p>
 *
 * @author Apex Team
 * @version 1.1.0
 * @since 1.3.0
 */
@ExtendWith(MockitoExtension.class)
class TenantApplicationServiceTest {

    private static final String TENANT_CODE = "T001";

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantAssembler tenantAssembler;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    @InjectMocks
    private TenantApplicationService tenantApplicationService;

    private TenantEntity activeTenant;
    private TenantDTO mockTenantDTO;

    @BeforeEach
    void setUp() {
        activeTenant = TenantEntity.create(TENANT_CODE, "Acme Corp", "admin-001", TenantQuota.of(100, 10240));
        activeTenant.setId(1L);

        mockTenantDTO = new TenantDTO();
        mockTenantDTO.setId(1L);
        mockTenantDTO.setTenantCode(TENANT_CODE);
        mockTenantDTO.setTenantName("Acme Corp");
        mockTenantDTO.setAdminUserId("admin-001");
        mockTenantDTO.setMaxUsers(100);
        mockTenantDTO.setMaxStorageMb(10240);
        mockTenantDTO.setStatus("ACTIVE");
        lenient().when(domainEventPublisher.publish(any())).thenReturn(Mono.empty());
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
        when(tenantAssembler.toDTO(any(TenantEntity.class))).thenReturn(mockTenantDTO);

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

        // D5：创建成功后发布租户主数据变更事件
        ArgumentCaptor<TenantChangedEvent> captor = ArgumentCaptor.forClass(TenantChangedEvent.class);
        verify(domainEventPublisher).publish(captor.capture());
        assertThat(captor.getValue().getTenantCode()).isEqualTo(TENANT_CODE);
        assertThat(captor.getValue().getAction()).isEqualTo(TenantChangedEvent.ACTION_CREATED);
    }

    @Test
    @DisplayName("createTenant - 预查询发现编码已存在时抛出异常")
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

        verify(tenantRepository, never()).insert(any(TenantEntity.class));
    }

    @Test
    @DisplayName("createTenant - 并发唯一键冲突映射为编码已存在")
    void createTenant_DuplicateKeyOnInsert() {
        TenantCreateRequest request = new TenantCreateRequest();
        request.setTenantCode(TENANT_CODE);
        request.setTenantName("Acme Corp");

        when(tenantRepository.existsByCode(TENANT_CODE, null)).thenReturn(Mono.just(false));
        when(tenantRepository.insert(any(TenantEntity.class)))
                .thenReturn(Mono.error(new DuplicateKeyException("uk_sys_tenant_tenant_code")));

        tenantApplicationService
                .createTenant(request)
                .as(StepVerifier::create)
                .expectErrorMatches(err -> err instanceof BusinessException
                        && ((BusinessException) err).getCode().equals(ResultCode.TENANT_CODE_EXISTS.getCode()))
                .verify();
    }

    @Test
    @DisplayName("getById - 租户不存在时抛出 TENANT_NOT_FOUND")
    void getById_NotFound() {
        when(tenantRepository.findById(99L)).thenReturn(Mono.empty());

        tenantApplicationService
                .getById(99L)
                .as(StepVerifier::create)
                .expectErrorMatches(err -> err instanceof BusinessException
                        && ((BusinessException) err).getCode().equals(ResultCode.TENANT_NOT_FOUND.getCode()))
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
    @DisplayName("pageTenants - 返回带筛选条件的分页结果")
    void pageTenants_Success() {
        TenantPageQuery query = TenantPageQuery.builder()
                .pageNum(1)
                .pageSize(10)
                .tenantCode("T0")
                .status("ACTIVE")
                .build();

        when(tenantRepository.search("T0", null, "ACTIVE", 1, 10))
                .thenReturn(Mono.just(PageResponse.of(List.of(activeTenant), 1L, 1, 10)));
        when(tenantAssembler.toDTO(activeTenant)).thenReturn(mockTenantDTO);

        tenantApplicationService
                .pageTenants(query)
                .as(StepVerifier::create)
                .assertNext(page -> {
                    assertThat(page.getTotal()).isEqualTo(1L);
                    assertThat(page.getPageNum()).isEqualTo(1);
                    assertThat(page.getPageSize()).isEqualTo(10);
                    assertThat(page.getList()).hasSize(1);
                    assertThat(page.getList().get(0).getTenantCode()).isEqualTo(TENANT_CODE);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("changeStatus - 正常租户停用成功")
    void changeStatus_DisableSuccess() {
        TenantDTO disabledDTO = new TenantDTO();
        disabledDTO.setId(1L);
        disabledDTO.setTenantCode(TENANT_CODE);
        disabledDTO.setStatus("DISABLED");

        when(tenantRepository.findById(1L)).thenReturn(Mono.just(activeTenant));
        when(tenantRepository.updateById(any(TenantEntity.class))).thenReturn(Mono.just(1));
        when(tenantAssembler.toDTO(any(TenantEntity.class))).thenReturn(disabledDTO);

        tenantApplicationService
                .changeStatus(1L, "disable", null)
                .as(StepVerifier::create)
                .assertNext(dto -> assertThat(dto.getStatus()).isEqualTo("DISABLED"))
                .verifyComplete();

        verify(tenantRepository).updateById(eq(activeTenant));

        // D5：状态迁移成功后发布租户主数据变更事件
        ArgumentCaptor<TenantChangedEvent> captor = ArgumentCaptor.forClass(TenantChangedEvent.class);
        verify(domainEventPublisher).publish(captor.capture());
        assertThat(captor.getValue().getTenantCode()).isEqualTo(TENANT_CODE);
        assertThat(captor.getValue().getAction()).isEqualTo(TenantChangedEvent.ACTION_UPDATED);
    }

    @Test
    @DisplayName("changeStatus - 已停用租户再次停用返回 TENANT_ILLEGAL_TRANSITION")
    void changeStatus_IllegalTransition() {
        activeTenant.disable();
        when(tenantRepository.findById(1L)).thenReturn(Mono.just(activeTenant));

        tenantApplicationService
                .changeStatus(1L, "disable", null)
                .as(StepVerifier::create)
                .expectErrorMatches(err -> err instanceof BusinessException
                        && ((BusinessException) err).getCode().equals(ResultCode.TENANT_ILLEGAL_TRANSITION.getCode()))
                .verify();

        verify(tenantRepository, never()).updateById(any(TenantEntity.class));
    }

    @Test
    @DisplayName("changeStatus - 不支持的动作返回 PARAM_ERROR")
    void changeStatus_UnsupportedAction() {
        tenantApplicationService
                .changeStatus(1L, "explode", null)
                .as(StepVerifier::create)
                .expectErrorMatches(err -> err instanceof BusinessException
                        && ((BusinessException) err).getCode().equals(ResultCode.PARAM_ERROR.getCode()))
                .verify();
    }

    @Test
    @DisplayName("changeStatus - 影响行数为 0 返回 TENANT_VERSION_CONFLICT")
    void changeStatus_VersionConflict() {
        when(tenantRepository.findById(1L)).thenReturn(Mono.just(activeTenant));
        when(tenantRepository.updateById(any(TenantEntity.class))).thenReturn(Mono.just(0));

        tenantApplicationService
                .changeStatus(1L, "disable", 7)
                .as(StepVerifier::create)
                .expectErrorMatches(err -> err instanceof BusinessException
                        && ((BusinessException) err).getCode().equals(ResultCode.TENANT_VERSION_CONFLICT.getCode()))
                .verify();
    }

    @Test
    @DisplayName("changeStatus - 终态归档后任何迁移被拒绝")
    void changeStatus_ArchiveTerminalState() {
        TenantDTO archivedDTO = new TenantDTO();
        archivedDTO.setId(1L);
        archivedDTO.setTenantCode(TENANT_CODE);
        archivedDTO.setStatus("ARCHIVED");

        activeTenant.deactivate();
        when(tenantRepository.findById(1L)).thenReturn(Mono.just(activeTenant));
        when(tenantRepository.updateById(any(TenantEntity.class))).thenReturn(Mono.just(1));
        when(tenantAssembler.toDTO(any(TenantEntity.class))).thenReturn(archivedDTO);

        tenantApplicationService
                .changeStatus(1L, "archive", null)
                .as(StepVerifier::create)
                .assertNext(dto -> assertThat(dto.getStatus()).isEqualTo("ARCHIVED"))
                .verifyComplete();

        tenantApplicationService
                .changeStatus(1L, "enable", null)
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
        TenantDTO updatedDTO = new TenantDTO();
        updatedDTO.setId(1L);
        updatedDTO.setTenantCode(TENANT_CODE);
        updatedDTO.setMaxUsers(200);
        updatedDTO.setMaxStorageMb(20480);

        when(tenantRepository.findById(1L)).thenReturn(Mono.just(activeTenant));
        when(tenantRepository.updateById(any(TenantEntity.class))).thenReturn(Mono.just(1));
        when(tenantAssembler.toDTO(any(TenantEntity.class))).thenReturn(updatedDTO);

        tenantApplicationService
                .updateQuota(1L, 200, 20480, null)
                .as(StepVerifier::create)
                .assertNext(dto -> {
                    assertThat(dto.getMaxUsers()).isEqualTo(200);
                    assertThat(dto.getMaxStorageMb()).isEqualTo(20480);
                })
                .verifyComplete();

        verify(tenantRepository).updateById(eq(activeTenant));

        // D5：配额更新成功后发布租户主数据变更事件
        ArgumentCaptor<TenantChangedEvent> captor = ArgumentCaptor.forClass(TenantChangedEvent.class);
        verify(domainEventPublisher).publish(captor.capture());
        assertThat(captor.getValue().getTenantCode()).isEqualTo(TENANT_CODE);
        assertThat(captor.getValue().getAction()).isEqualTo(TenantChangedEvent.ACTION_UPDATED);
    }

    @Test
    @DisplayName("updateQuota - 影响行数为 0 返回 TENANT_VERSION_CONFLICT")
    void updateQuota_VersionConflict() {
        when(tenantRepository.findById(1L)).thenReturn(Mono.just(activeTenant));
        when(tenantRepository.updateById(any(TenantEntity.class))).thenReturn(Mono.just(0));

        tenantApplicationService
                .updateQuota(1L, 200, 20480, 3)
                .as(StepVerifier::create)
                .expectErrorMatches(err -> err instanceof BusinessException
                        && ((BusinessException) err).getCode().equals(ResultCode.TENANT_VERSION_CONFLICT.getCode()))
                .verify();
    }
}
