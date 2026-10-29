package com.apex.scaffold.application.service;

import com.apex.scaffold.application.dto.admin.GenDatasourceDTO;
import com.apex.scaffold.application.dto.admin.GenDatasourceRequest;
import com.apex.scaffold.application.support.DataSourcePoolManager;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.security.ConfigEncryptionService;
import com.apex.scaffold.domain.model.generator.DbType;
import com.apex.scaffold.domain.model.generator.GenDatasourceEntity;
import com.apex.scaffold.domain.repository.GenConfigRepository;
import com.apex.scaffold.domain.repository.GenDatasourceRepository;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 代码生成数据源应用服务。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenDatasourceService {

    private static final int CONNECTION_TEST_TIMEOUT_SECONDS = 5;
    private static final String MASKED_PASSWORD = "******";

    private final DataSourcePoolManager dataSourcePoolManager;
    private final GenDatasourceRepository datasourceRepository;
    private final GenConfigRepository configRepository;
    private final ConfigEncryptionService encryptionService;
    private final TenantGuard tenantGuard;

    /**
     * 创建代码生成数据源。
     *
     * <p>先测试数据库连接连通性，再校验同租户下名称唯一后写入新数据源。</p>
     *
     * @param request 数据源创建请求
     * @return 创建后的数据源 DTO
     */
    public Mono<GenDatasourceDTO> create(GenDatasourceRequest request) {
        return tenantGuard.assertActive().then(Mono.defer(() -> tenantId()
                .flatMap(tenant -> testConnection(request.getJdbcUrl(), request.getUsername(), request.getPassword())
                        .filter(Boolean.TRUE::equals)
                        .switchIfEmpty(Mono.error(new BusinessException(ResultCode.DB_ERROR, "数据库连接测试失败，请检查连接配置")))
                        .then(datasourceRepository.findByNameAndTenantId(request.getName(), tenant))
                        .flatMap(existing -> existing.isPresent()
                                ? Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "数据源名称已存在"))
                                : Mono.just(toEntity(request, tenant)))
                        .flatMap(entity -> datasourceRepository.insert(entity).thenReturn(toDTO(entity))))));
    }

    /**
     * 更新代码生成数据源。
     *
     * <p>密码为掩码时沿用原解密密码，连接测试通过后更新并关闭对应连接池缓存。</p>
     *
     * @param id 数据源 ID
     * @param request 数据源更新请求
     * @return 更新后的数据源 DTO
     */
    public Mono<GenDatasourceDTO> update(Long id, GenDatasourceRequest request) {
        return tenantId().flatMap(tenant -> datasourceRepository
                .findById(id)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "数据源不存在")))
                .flatMap(entity -> requireTenant(entity.getTenantId(), tenant).thenReturn(entity))
                .flatMap(entity -> {
                    String password = MASKED_PASSWORD.equals(request.getPassword())
                            ? encryptionService.decrypt(entity.getPassword())
                            : request.getPassword();
                    return testConnection(request.getJdbcUrl(), request.getUsername(), password)
                            .filter(Boolean.TRUE::equals)
                            .switchIfEmpty(Mono.error(new BusinessException(ResultCode.DB_ERROR, "数据库连接测试失败，请检查连接配置")))
                            .then(Mono.fromSupplier(() -> {
                                copy(request, entity, tenant, password);
                                return entity;
                            }));
                })
                .flatMap(entity -> datasourceRepository.updateById(entity).thenReturn(toDTO(entity)))
                .doOnSuccess(value -> dataSourcePoolManager.closeDataSource(tenant, id)));
    }

    /**
     * 删除代码生成数据源。
     *
     * <p>数据源仍被生成配置引用时拒绝删除，完成后关闭对应连接池缓存。</p>
     *
     * @param id 数据源 ID
     * @return 是否删除成功
     */
    public Mono<Boolean> delete(Long id) {
        return tenantId().flatMap(tenant -> datasourceRepository
                .findById(id)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "数据源不存在")))
                .flatMap(entity -> requireTenant(entity.getTenantId(), tenant))
                .then(configRepository.findByDatasourceId(id))
                .flatMap(configs -> configs.isEmpty()
                        ? datasourceRepository.deleteById(id).map(rows -> rows > 0)
                        : Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "数据源仍被生成配置引用")))
                .doOnSuccess(deleted -> dataSourcePoolManager.closeDataSource(tenant, id)));
    }

    /**
     * 按 ID 查询代码生成数据源。
     *
     * <p>校验租户归属，数据源不存在时返回错误信号。</p>
     *
     * @param id 数据源 ID
     * @return 数据源 DTO（密码已脱敏），不存在时返回错误信号
     */
    public Mono<GenDatasourceDTO> findById(Long id) {
        return tenantId().flatMap(tenant -> datasourceRepository
                .findById(id)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "数据源不存在")))
                .flatMap(entity -> requireTenant(entity.getTenantId(), tenant).thenReturn(toDTO(entity))));
    }

    /**
     * 分页查询当前租户的数据源，支持按名称模糊过滤。
     *
     * @param name 名称关键字，为空时查询全部
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 数据源分页结果
     */
    public Mono<PageResponse<GenDatasourceDTO>> list(String name, int pageNum, int pageSize) {
        return tenantId().flatMap(tenant -> datasourceRepository
                .findByTenantId(tenant)
                .map(items -> items.stream()
                        .filter(item ->
                                name == null || name.isBlank() || item.getName().contains(name))
                        .map(this::toDTO)
                        .toList())
                .map(items -> {
                    int from = Math.min(Math.max(pageNum - 1, 0) * pageSize, items.size());
                    int to = Math.min(from + pageSize, items.size());
                    return PageResponse.of(items.subList(from, to), (long) items.size(), pageNum, pageSize);
                }));
    }

    /**
     * 测试指定数据源的数据库连接连通性。
     *
     * @param id 数据源 ID
     * @return 连接成功返回 true，失败或超时返回 false
     */
    public Mono<Boolean> testConnection(Long id) {
        return tenantId().flatMap(tenant -> datasourceRepository
                .findById(id)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "数据源不存在")))
                .flatMap(entity -> requireTenant(entity.getTenantId(), tenant)
                        .then(testConnection(
                                entity.getParams(),
                                entity.getUsername(),
                                encryptionService.decrypt(entity.getPassword())))));
    }

    private Mono<Boolean> testConnection(String jdbcUrl, String username, String password) {
        return dataSourcePoolManager
                .testConnection(jdbcUrl, username, password, CONNECTION_TEST_TIMEOUT_SECONDS)
                .timeout(Duration.ofSeconds(CONNECTION_TEST_TIMEOUT_SECONDS + 1))
                .onErrorReturn(false);
    }

    private Mono<String> tenantId() {
        return Mono.deferContextual(context -> {
            String tenant = context.getOrDefault(TenantContext.REACTOR_CONTEXT_KEY, null);
            return tenant == null || tenant.isBlank()
                    ? Mono.error(new BusinessException(ResultCode.FORBIDDEN, "租户上下文不能为空"))
                    : Mono.just(tenant);
        });
    }

    private Mono<Void> requireTenant(String actual, String expected) {
        return expected.equals(actual)
                ? Mono.empty()
                : Mono.error(new BusinessException(ResultCode.FORBIDDEN, "无权访问其他租户的数据"));
    }

    private GenDatasourceEntity toEntity(GenDatasourceRequest request, String tenant) {
        GenDatasourceEntity entity = new GenDatasourceEntity();
        copy(request, entity, tenant, request.getPassword());
        return entity;
    }

    private void copy(GenDatasourceRequest request, GenDatasourceEntity entity, String tenant, String password) {
        entity.setTenantId(tenant);
        entity.setName(request.getName());
        entity.setDbType(DbType.valueOf(request.getDbType().toUpperCase()));
        entity.setParams(request.getJdbcUrl());
        entity.setUsername(request.getUsername());
        entity.setPassword(encryptionService.encrypt(password));
        entity.setStatus(1);
    }

    private GenDatasourceDTO toDTO(GenDatasourceEntity entity) {
        return GenDatasourceDTO.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .name(entity.getName())
                .dbType(
                        entity.getDbType() == null
                                ? null
                                : entity.getDbType().name().toLowerCase())
                .jdbcUrl(entity.getParams())
                .username(entity.getUsername())
                .password(MASKED_PASSWORD)
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }
}
