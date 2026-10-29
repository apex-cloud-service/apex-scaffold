package com.apex.scaffold.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.assembler.DataSourceAssembler;
import com.apex.scaffold.application.dto.admin.DataSourceRequest;
import com.apex.scaffold.application.dto.admin.DataSourceResponse;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.security.EncryptionService;
import com.apex.scaffold.domain.model.system.SysDatasource;
import com.apex.scaffold.domain.repository.SysDatasourceRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * {@link DataSourceManagementService} 数据源管理单元测试。
 *
 * <p>验证数据源 CRUD、名称唯一性校验、租户隔离与脱敏逻辑，
 * 外部依赖（仓储、加密服务、装配器）全部 Mock，不依赖真实数据库。
 *
 * <h2>测试覆盖</h2>
 * <ul>
 *   <li>创建数据源 - 成功场景（密码加密、ID 回填、脱敏、离线状态）</li>
 *   <li>创建数据源 - 名称重复时抛出业务异常</li>
 *   <li>分页查询 - 总数、页内容与脱敏规则</li>
 *   <li>更新数据源 - 成功（排除自身重名）与不存在时抛出异常</li>
 *   <li>更新数据源 - 跨租户访问被拒绝</li>
 *   <li>删除数据源 - 成功与不存在时抛出异常</li>
 *   <li>监控指标 - 未建池时返回 OFFLINE 空指标</li>
 * </ul>
 *
 * <p>连接池建立为尽力而为：测试环境的 JDBC 地址不可达，池建立失败被
 * 服务吞掉（记录告警并保持 OFFLINE），不影响 CRUD 断言。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DataSourceManagementService - 数据源管理测试")
class DataSourceManagementServiceTest {

    private static final String TENANT = "tenant-a";

    @Mock
    private SysDatasourceRepository datasourceRepository;

    @Mock
    private DataSourceAssembler dataSourceAssembler;

    @Mock
    private EncryptionService encryptionService;

    @Mock
    private TenantGuard tenantGuard;

    private DataSourceManagementService service;

    @BeforeEach
    void setUp() {
        service = new DataSourceManagementService(
                datasourceRepository, dataSourceAssembler, encryptionService, tenantGuard);
        TenantContext.setTenantId(TENANT);
        lenient().when(tenantGuard.assertActive()).thenReturn(Mono.empty());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /**
     * 构建数据源创建请求（短连接超时，保证测试中建池快速失败）。
     */
    private DataSourceRequest request(String name) {
        return DataSourceRequest.builder()
                .name(name)
                .type("master")
                .driver("mysql")
                .url("jdbc:mysql://localhost:3306/db_" + name)
                .username("user_" + name)
                .password("pass")
                .minIdle(2)
                .maxPoolSize(5)
                .connectionTimeout(500L)
                .idleTimeout(60000L)
                .build();
    }

    /**
     * 构建指定 ID 与租户的数据源实体。
     */
    private SysDatasource entity(Long id, String name, String tenantId) {
        SysDatasource entity = new SysDatasource();
        entity.setId(id);
        entity.setName(name);
        entity.setType("master");
        entity.setDriver("mysql");
        entity.setUrl("jdbc:mysql://localhost:3306/db_" + name);
        entity.setUsername("user_" + name);
        entity.setPassword("ENC-" + name);
        entity.setMinIdle(2);
        entity.setMaxPoolSize(5);
        entity.setConnectionTimeout(500L);
        entity.setIdleTimeout(60000L);
        entity.setStatus(1);
        entity.setTenantId(tenantId);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        return entity;
    }

    /**
     * 构建与实体字段对应的响应 DTO。
     */
    private DataSourceResponse response(Long id, String name) {
        DataSourceResponse response = new DataSourceResponse();
        response.setId(id);
        response.setName(name);
        response.setType("master");
        response.setDriver("mysql");
        response.setUrl("jdbc:mysql://localhost:3306/db_" + name);
        response.setUsername("user_" + name);
        response.setPoolSize(5);
        return response;
    }

    @Test
    @DisplayName("createDataSource - 成功创建并回填 ID、脱敏响应")
    void createDataSource_Success() {
        SysDatasource saved = entity(100L, "ds1", TENANT);
        DataSourceResponse mapped = response(100L, "ds1");

        when(datasourceRepository.findByNameAndTenantId("ds1", TENANT)).thenReturn(Mono.just(Optional.empty()));
        when(encryptionService.encrypt("pass")).thenReturn(Mono.just("ENC-ds1"));
        when(datasourceRepository.insert(any(SysDatasource.class))).thenReturn(Mono.just(saved));
        when(encryptionService.decrypt("ENC-ds1")).thenReturn(Mono.just("pass"));
        when(dataSourceAssembler.toResponse(any(SysDatasource.class))).thenReturn(mapped);

        StepVerifier.create(service.createDataSource(request("ds1")))
                .assertNext(resp -> {
                    assertThat(resp.getId()).isEqualTo(100L);
                    assertThat(resp.getName()).isEqualTo("ds1");
                    assertThat(resp.getStatus()).isEqualTo("OFFLINE");
                    // URL 数据库名与用户名脱敏
                    assertThat(resp.getUrl()).isEqualTo("jdbc:mysql://localhost:3306/***");
                    assertThat(resp.getUsername()).isEqualTo("us***");
                })
                .verifyComplete();

        verify(datasourceRepository).insert(any(SysDatasource.class));
    }

    @Test
    @DisplayName("createDataSource - 名称重复时抛出业务异常")
    void createDataSource_DuplicateName() {
        when(datasourceRepository.findByNameAndTenantId("dup", TENANT))
                .thenReturn(Mono.just(Optional.of(entity(2L, "dup", TENANT))));

        StepVerifier.create(service.createDataSource(request("dup")))
                .expectError(BusinessException.class)
                .verify();

        verify(datasourceRepository, never()).insert(any(SysDatasource.class));
    }

    @Test
    @DisplayName("listDataSources - 分页返回并统一脱敏")
    void listDataSources_Pagination() {
        List<SysDatasource> entities = List.of(
                entity(1L, "ds1", TENANT),
                entity(2L, "ds2", TENANT),
                entity(3L, "ds3", TENANT),
                entity(4L, "ds4", TENANT),
                entity(5L, "ds5", TENANT));
        when(datasourceRepository.findByTenantId(TENANT)).thenReturn(Mono.just(entities));
        when(dataSourceAssembler.toResponse(any(SysDatasource.class)))
                .thenAnswer(inv -> response(
                        ((SysDatasource) inv.getArgument(0)).getId(), ((SysDatasource) inv.getArgument(0)).getName()));

        StepVerifier.create(service.listDataSources(1, 3))
                .assertNext(page -> {
                    assertThat(page.getTotal()).isEqualTo(5L);
                    assertThat(page.getList()).hasSize(3);
                    assertThat(page.getList().get(0).getName()).isEqualTo("ds1");
                    assertThat(page.getList().get(0).getUrl()).isEqualTo("jdbc:mysql://localhost:3306/***");
                    assertThat(page.getList().get(0).getStatus()).isEqualTo("OFFLINE");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("updateDataSource - 成功更新（排除自身重名）并重建连接池")
    void updateDataSource_Success() {
        SysDatasource existing = entity(100L, "old", TENANT);
        DataSourceResponse mapped = response(100L, "new");

        when(datasourceRepository.findById(100L)).thenReturn(Mono.just(existing));
        when(datasourceRepository.findByNameAndTenantId("new", TENANT)).thenReturn(Mono.just(Optional.empty()));
        when(encryptionService.encrypt("pass")).thenReturn(Mono.just("ENC-new"));
        when(datasourceRepository.update(any(SysDatasource.class))).thenReturn(Mono.just(1));
        when(encryptionService.decrypt("ENC-new")).thenReturn(Mono.just("pass"));
        when(dataSourceAssembler.toResponse(any(SysDatasource.class))).thenReturn(mapped);

        StepVerifier.create(service.updateDataSource(100L, request("new")))
                .assertNext(resp -> {
                    assertThat(resp.getId()).isEqualTo(100L);
                    assertThat(resp.getName()).isEqualTo("new");
                    // 密码已加密更新，且不会出现在响应中
                    assertThat(existing.getPassword()).isEqualTo("ENC-new");
                    assertThat(resp.getUrl()).doesNotContain("db_new");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("updateDataSource - 数据源不存在时抛出异常")
    void updateDataSource_NotFound() {
        when(datasourceRepository.findById(999L)).thenReturn(Mono.empty());

        StepVerifier.create(service.updateDataSource(999L, request("ghost")))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    @DisplayName("updateDataSource - 跨租户访问被拒绝")
    void updateDataSource_TenantMismatch() {
        when(datasourceRepository.findById(100L)).thenReturn(Mono.just(entity(100L, "other-tenant-ds", "tenant-b")));

        StepVerifier.create(service.updateDataSource(100L, request("hijack")))
                .expectError(BusinessException.class)
                .verify();

        verify(datasourceRepository, never()).update(any(SysDatasource.class));
    }

    @Test
    @DisplayName("deleteDataSource - 成功删除")
    void deleteDataSource_Success() {
        when(datasourceRepository.findById(100L)).thenReturn(Mono.just(entity(100L, "doomed", TENANT)));
        when(datasourceRepository.deleteById(100L)).thenReturn(Mono.just(1));

        StepVerifier.create(service.deleteDataSource(100L)).verifyComplete();

        verify(datasourceRepository).deleteById(100L);
    }

    @Test
    @DisplayName("deleteDataSource - 数据源不存在时抛出异常")
    void deleteDataSource_NotFound() {
        when(datasourceRepository.findById(999L)).thenReturn(Mono.empty());

        StepVerifier.create(service.deleteDataSource(999L))
                .expectError(BusinessException.class)
                .verify();

        verify(datasourceRepository, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("getDataSourceMetrics - 未建立连接池时返回 OFFLINE 空指标")
    void getDataSourceMetrics_NoPool() {
        when(datasourceRepository.findById(100L)).thenReturn(Mono.just(entity(100L, "idle", TENANT)));

        StepVerifier.create(service.getDataSourceMetrics(100L))
                .assertNext(metrics -> {
                    assertThat(metrics).isNotNull();
                    assertThat(metrics.getTotalConnections()).isNull();
                    assertThat(metrics.getActiveConnections()).isNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("getDataSourceMetrics - 数据源不存在时抛出异常")
    void getDataSourceMetrics_NotFound() {
        when(datasourceRepository.findById(999L)).thenReturn(Mono.empty());

        StepVerifier.create(service.getDataSourceMetrics(999L))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    @DisplayName("租户上下文未初始化时拒绝操作")
    void rejectsWhenTenantMissing() {
        TenantContext.clear();

        StepVerifier.create(service.listDataSources(1, 10))
                .expectError(BusinessException.class)
                .verify();

        verify(datasourceRepository, never()).findByTenantId(anyString());
    }
}
