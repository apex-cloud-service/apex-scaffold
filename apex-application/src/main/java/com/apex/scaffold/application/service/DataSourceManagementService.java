package com.apex.scaffold.application.service;

import com.apex.scaffold.application.assembler.DataSourceAssembler;
import com.apex.scaffold.application.dto.admin.DataSourceMetricsResponse;
import com.apex.scaffold.application.dto.admin.DataSourceRequest;
import com.apex.scaffold.application.dto.admin.DataSourceResponse;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.common.security.EncryptionService;
import com.apex.scaffold.domain.model.system.SysDatasource;
import com.apex.scaffold.domain.repository.SysDatasourceRepository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 数据源管理应用服务。
 *
 * <p>承载管理后台"数据库配置"模块的数据源管理能力，提供数据源的增删改查、
 * 分页查询与连接池运行时监控。数据配置持久化到 {@code sys_datasource} 表
 * （通过 {@link SysDatasourceRepository}），连接池运行时状态缓存在进程内，
 * 重启后按需重建。
 *
 * <h2>核心职责</h2>
 * <ul>
 *   <li>数据源 CRUD：创建、更新、删除（逻辑删除）、分页查询</li>
 *   <li>唯一性校验：数据源名称在同一租户内唯一</li>
 *   <li>密码安全：写入前经 {@link EncryptionService} 加密，响应永不返回密码</li>
 *   <li>连接池生命周期：创建/更新时重建池（尽力而为），删除时关闭池</li>
 *   <li>运行时监控：采集 HikariCP 连接池指标，未建池时返回 OFFLINE</li>
 * </ul>
 *
 * <h2>安全与隔离约束</h2>
 * <ul>
 *   <li>租户隔离：所有操作限定 {@code TenantContext.currentTenantId()}，
 *       更新与删除校验记录归属租户，防止跨租户访问</li>
 *   <li>阻塞隔离：JDBC 操作由仓储层调度到 {@code BlockingIoSchedulers.jdbc()}；
 *       连接池建立（阻塞网络 I/O）在本服务调度到 jdbc 调度器</li>
 *   <li>脱敏规则：URL 数据库名与用户名在响应中脱敏；密码完全隐藏</li>
 * </ul>
 *
 * <h2>指标说明</h2>
 * <p>HikariCP 的 {@link HikariPoolMXBean} 仅暴露连接数与等待线程类指标，
 * 平均耗时/创建失败数等扩展指标在接入 Micrometer 采集前为 {@code null}。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see DataSourceRequest
 * @see DataSourceResponse
 * @see DataSourceMetricsResponse
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataSourceManagementService {

    private static final String PATH_SEPARATOR = "/";

    /** 响应中用户名脱敏保留的前缀长度。 */
    private static final int USERNAME_MASK_PREFIX = 2;

    /** JDBC URL 脱敏后的占位段。 */
    private static final String URL_MASK = "/***";

    /** 数据源最小空闲连接数默认值。 */
    private static final int DEFAULT_MIN_IDLE = 5;

    /** 数据源最大连接池大小默认值。 */
    private static final int DEFAULT_MAX_POOL_SIZE = 20;

    /** 数据源获取连接超时默认值（毫秒）。 */
    private static final long DEFAULT_CONNECTION_TIMEOUT_MS = 30000L;

    /** 数据源空闲连接超时默认值（毫秒）。 */
    private static final long DEFAULT_IDLE_TIMEOUT_MS = 600000L;

    /**
     * 数据源运行时连接池缓存。
     *
     * <p>key 为数据源 ID，value 为该数据源当前生效的 HikariCP 连接池。
     * 池的建立在创建/更新时尽力而为（best-effort）：失败仅记录告警并将
     * 状态置为 OFFLINE，不影响配置保存；进程重启后按需重建。
     */
    private final Map<Long, HikariDataSource> poolCache = new ConcurrentHashMap<>();

    /** 系统数据源配置仓储。 */
    private final SysDatasourceRepository datasourceRepository;

    /** 数据源实体与响应 DTO 转换器。 */
    private final DataSourceAssembler dataSourceAssembler;

    /** 加密服务（密码加密存储 / 建池时解密）。 */
    private final EncryptionService encryptionService;

    /**
     * 分页查询当前租户的数据源列表。
     *
     * <p>查询租户下全部数据源配置后按创建时间倒序在内存中分页。
     * 数据源配置属于低频小体量数据（个位数到两位数），内存分页可避免
     * 引入 MyBatis-Plus 分页插件的额外依赖与两次计数查询。
     *
     * @param pageNum 页码（从 1 开始）
     * @param pageSize 每页大小
     * @return 分页的脱敏数据源列表
     */
    public Mono<PageResponse<DataSourceResponse>> listDataSources(int pageNum, int pageSize) {
        return Mono.defer(() -> {
            String tenantId = requireTenantId();
            log.debug("分页查询数据源: tenantId={}, pageNum={}, pageSize={}", tenantId, pageNum, pageSize);

            return datasourceRepository.findByTenantId(tenantId).map(entities -> {
                List<DataSourceResponse> all = entities.stream()
                        .map(dataSourceAssembler::toResponse)
                        .peek(this::applyRuntimeState)
                        .peek(this::maskSensitiveFields)
                        .toList();
                return paginate(all, pageNum, pageSize);
            });
        });
    }

    /**
     * 创建数据源。
     *
     * <p>流程：名称唯一性校验 → 密码加密 → 持久化 → 尽力而为建立连接池。
     * 连接池建立失败不影响配置保存（状态为 OFFLINE，可后续更新重试）。
     *
     * @param request 数据源创建请求（JSR-303 已校验必填与取值范围）
     * @return 创建成功的数据源响应（脱敏）
     */
    public Mono<DataSourceResponse> createDataSource(DataSourceRequest request) {
        return Mono.defer(() -> {
            String tenantId = requireTenantId();
            log.info(
                    "创建数据源: tenantId={}, name={}, type={}, driver={}",
                    tenantId,
                    request.getName(),
                    request.getType(),
                    request.getDriver());

            return assertNameAvailable(request.getName(), tenantId, null)
                    // encrypt 延迟到订阅期执行，避免链构建期的急切副作用
                    .then(Mono.defer(() -> encryptionService.encrypt(request.getPassword())))
                    .flatMap(encryptedPassword -> {
                        SysDatasource entity = new SysDatasource();
                        entity.setName(request.getName());
                        entity.setType(request.getType());
                        entity.setDriver(request.getDriver());
                        entity.setUrl(request.getUrl());
                        entity.setUsername(request.getUsername());
                        entity.setPassword(encryptedPassword);
                        entity.setMinIdle(request.getMinIdle() != null ? request.getMinIdle() : DEFAULT_MIN_IDLE);
                        entity.setMaxPoolSize(
                                request.getMaxPoolSize() != null ? request.getMaxPoolSize() : DEFAULT_MAX_POOL_SIZE);
                        entity.setConnectionTimeout(
                                request.getConnectionTimeout() != null
                                        ? request.getConnectionTimeout()
                                        : DEFAULT_CONNECTION_TIMEOUT_MS);
                        entity.setIdleTimeout(
                                request.getIdleTimeout() != null ? request.getIdleTimeout() : DEFAULT_IDLE_TIMEOUT_MS);
                        entity.setStatus(1);
                        entity.setTenantId(tenantId);
                        return datasourceRepository.insert(entity);
                    })
                    .flatMap(saved -> buildPool(saved).thenReturn(saved).map(this::toMaskedResponse))
                    .doOnSuccess(response -> log.info(
                            "数据源创建成功: id={}, name={}, tenantId={}", response.getId(), response.getName(), tenantId));
        });
    }

    /**
     * 更新数据源。
     *
     * <p>流程：归属校验（租户）→ 名称唯一性校验（排除自身）→ 密码加密 →
     * 乐观锁更新 → 关闭旧连接池并尽力重建新池。连接参数变更通过池重建生效。
     *
     * @param id 数据源 ID
     * @param request 数据源更新请求
     * @return 更新后的数据源响应（脱敏）
     */
    public Mono<DataSourceResponse> updateDataSource(Long id, DataSourceRequest request) {
        return Mono.defer(() -> {
            String tenantId = requireTenantId();
            log.info("更新数据源: id={}, tenantId={}, name={}", id, tenantId, request.getName());

            return datasourceRepository
                    .findById(id)
                    .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "数据源不存在: " + id)))
                    .flatMap(existing -> {
                        assertTenantOwner(existing, tenantId);
                        return assertNameAvailable(request.getName(), tenantId, id)
                                // encrypt 延迟到订阅期执行，避免链构建期的急切副作用
                                .then(Mono.defer(() -> encryptionService.encrypt(request.getPassword())))
                                .flatMap(encryptedPassword -> {
                                    existing.setName(request.getName());
                                    existing.setType(request.getType());
                                    existing.setDriver(request.getDriver());
                                    existing.setUrl(request.getUrl());
                                    existing.setUsername(request.getUsername());
                                    existing.setPassword(encryptedPassword);
                                    existing.setMinIdle(
                                            request.getMinIdle() != null ? request.getMinIdle() : DEFAULT_MIN_IDLE);
                                    existing.setMaxPoolSize(
                                            request.getMaxPoolSize() != null
                                                    ? request.getMaxPoolSize()
                                                    : DEFAULT_MAX_POOL_SIZE);
                                    existing.setConnectionTimeout(
                                            request.getConnectionTimeout() != null
                                                    ? request.getConnectionTimeout()
                                                    : 30000L);
                                    existing.setIdleTimeout(
                                            request.getIdleTimeout() != null
                                                    ? request.getIdleTimeout()
                                                    : DEFAULT_IDLE_TIMEOUT_MS);
                                    return datasourceRepository.update(existing);
                                })
                                .flatMap(rows -> {
                                    if (rows == 0) {
                                        return Mono.error(
                                                new BusinessException(ResultCode.DB_ERROR, "数据源更新失败（可能被并发修改），请重试"));
                                    }
                                    return closePool(id)
                                            .then(buildPool(existing))
                                            .thenReturn(existing);
                                });
                    })
                    .map(this::toMaskedResponse)
                    .doOnSuccess(response -> log.info("数据源更新成功: id={}", id));
        });
    }

    /**
     * 删除数据源。
     *
     * <p>流程：归属校验（租户）→ 逻辑删除 → 关闭并移除运行时连接池。
     *
     * @param id 数据源 ID
     * @return 完成信号；数据源不存在时抛出 NOT_FOUND
     */
    public Mono<Void> deleteDataSource(Long id) {
        return Mono.defer(() -> {
            String tenantId = requireTenantId();
            log.info("删除数据源: id={}, tenantId={}", id, tenantId);

            return datasourceRepository
                    .findById(id)
                    .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "数据源不存在: " + id)))
                    .flatMap(existing -> {
                        assertTenantOwner(existing, tenantId);
                        return datasourceRepository.deleteById(id);
                    })
                    .then(closePool(id))
                    .doOnSuccess(v -> log.info("数据源删除成功: id={}", id));
        });
    }

    /**
     * 查询数据源连接池监控指标。
     *
     * <p>从进程内连接池缓存读取 HikariCP 运行时指标；池不存在（未建立或
     * 建立失败）时返回状态 OFFLINE、指标为空值的结果，不抛出异常。
     * 数据源配置本身不存在时抛出 NOT_FOUND。
     *
     * @param id 数据源 ID
     * @return 连接池监控指标
     */
    public Mono<DataSourceMetricsResponse> getDataSourceMetrics(Long id) {
        return Mono.defer(() -> {
            String tenantId = requireTenantId();
            log.debug("查询数据源监控指标: id={}, tenantId={}", id, tenantId);

            return datasourceRepository
                    .findById(id)
                    .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "数据源不存在: " + id)))
                    .flatMap(existing -> {
                        assertTenantOwner(existing, tenantId);
                        return Mono.fromCallable(() -> collectMetrics(id)).subscribeOn(BlockingIoSchedulers.jdbc());
                    });
        });
    }

    /**
     * 获取当前租户 ID（未初始化时抛出业务异常）。
     *
     * @return 租户 ID
     */
    private String requireTenantId() {
        return TenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException(ResultCode.FORBIDDEN, "租户上下文未初始化"));
    }

    /**
     * 校验数据源名称在租户内可用（不存在，或属于指定 ID 自身）。
     *
     * @param name 待校验的数据源名称
     * @param tenantId 租户 ID
     * @param selfId 更新场景下允许重名的记录 ID；创建场景传 {@code null}
     * @return 完成信号；名称冲突时抛出 PARAM_ERROR
     */
    private Mono<Void> assertNameAvailable(String name, String tenantId, Long selfId) {
        return datasourceRepository.findByNameAndTenantId(name, tenantId).flatMap(existing -> {
            boolean conflict = existing.isPresent()
                    && (selfId == null || !selfId.equals(existing.get().getId()));
            if (conflict) {
                return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "数据源名称已存在: " + name));
            }
            return Mono.empty();
        });
    }

    /**
     * 校验数据源归属当前租户。
     *
     * @param entity 数据源实体
     * @param tenantId 当前租户 ID
     */
    private void assertTenantOwner(SysDatasource entity, String tenantId) {
        if (!tenantId.equals(entity.getTenantId())) {
            log.warn(
                    "检测到跨租户数据源访问: id={}, ownerTenant={}, currentTenant={}",
                    entity.getId(),
                    entity.getTenantId(),
                    tenantId);
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作该数据源");
        }
    }

    /**
     * 尽力而为建立数据源连接池。
     *
     * <p>解密密码后按实体配置创建 HikariCP 连接池并放入缓存；
     * 建立失败仅记录告警（保持 OFFLINE 状态），不中断业务流程。
     *
     * @param entity 数据源实体（password 为密文）
     * @return 完成信号（永不以错误结束）
     */
    private Mono<Void> buildPool(SysDatasource entity) {
        return encryptionService.decrypt(entity.getPassword()).flatMap(plainPassword -> Mono.fromCallable(() -> {
                    closePool(entity.getId()).subscribe();
                    try {
                        HikariConfig config = new HikariConfig();
                        config.setJdbcUrl(entity.getUrl());
                        config.setUsername(entity.getUsername());
                        config.setPassword(plainPassword);
                        config.setMinimumIdle(entity.getMinIdle() != null ? entity.getMinIdle() : DEFAULT_MIN_IDLE);
                        config.setMaximumPoolSize(
                                entity.getMaxPoolSize() != null ? entity.getMaxPoolSize() : DEFAULT_MAX_POOL_SIZE);
                        config.setConnectionTimeout(
                                entity.getConnectionTimeout() != null
                                        ? entity.getConnectionTimeout()
                                        : DEFAULT_CONNECTION_TIMEOUT_MS);
                        config.setIdleTimeout(
                                entity.getIdleTimeout() != null ? entity.getIdleTimeout() : DEFAULT_IDLE_TIMEOUT_MS);
                        config.setPoolName("SysDatasource-" + entity.getId());
                        config.setAutoCommit(true);
                        config.setConnectionTestQuery("SELECT 1");

                        HikariDataSource pool = new HikariDataSource(config);
                        poolCache.put(entity.getId(), pool);
                        log.info("数据源连接池建立成功: id={}, pool={}", entity.getId(), config.getPoolName());
                    } catch (Exception e) {
                        log.warn("数据源连接池建立失败（配置已保存，状态 OFFLINE）: id={}, reason={}", entity.getId(), e.getMessage());
                    }
                    return (Void) null;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .onErrorResume(e -> {
                    log.warn("数据源连接池建立流程异常: id={}, reason={}", entity.getId(), e.getMessage());
                    return Mono.empty();
                }));
    }

    /**
     * 关闭并移除指定数据源的连接池。
     *
     * @param id 数据源 ID
     * @return 完成信号
     */
    private Mono<Void> closePool(Long id) {
        return Mono.fromCallable(() -> {
                    HikariDataSource pool = poolCache.remove(id);
                    if (pool != null && !pool.isClosed()) {
                        pool.close();
                        log.info("数据源连接池已关闭: id={}", id);
                    }
                    return (Void) null;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .onErrorResume(e -> {
                    log.warn("数据源连接池关闭异常: id={}, reason={}", id, e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * 采集数据源连接池运行时指标。
     *
     * <p>池存在且未关闭时读取 HikariPoolMXBean；否则返回 OFFLINE 空指标。
     *
     * @param id 数据源 ID
     * @return 监控指标响应
     */
    private DataSourceMetricsResponse collectMetrics(Long id) {
        HikariDataSource pool = poolCache.get(id);
        if (pool == null || pool.isClosed()) {
            return DataSourceMetricsResponse.builder().build();
        }
        HikariPoolMXBean mxBean = pool.getHikariPoolMXBean();
        if (mxBean == null) {
            return DataSourceMetricsResponse.builder().build();
        }
        return DataSourceMetricsResponse.builder()
                .totalConnections(mxBean.getTotalConnections())
                .activeConnections(mxBean.getActiveConnections())
                .idleConnections(mxBean.getIdleConnections())
                .waitingThreads(mxBean.getThreadsAwaitingConnection())
                // HikariPoolMXBean 未暴露平均耗时/累计创建失败等扩展指标，
                // 接入 Micrometer 采集前保持 null
                .build();
    }

    /**
     * 将实体转换为脱敏响应并附加运行时状态。
     *
     * @param entity 数据源实体
     * @return 脱敏后的数据源响应
     */
    private DataSourceResponse toMaskedResponse(SysDatasource entity) {
        DataSourceResponse response = dataSourceAssembler.toResponse(entity);
        applyRuntimeState(response);
        maskSensitiveFields(response);
        return response;
    }

    /**
     * 为响应附加连接池运行时状态（池大小、活跃连接数、在线状态）。
     *
     * @param response 数据源响应
     */
    private void applyRuntimeState(DataSourceResponse response) {
        HikariDataSource pool = poolCache.get(response.getId());
        if (pool == null || pool.isClosed()) {
            response.setStatus("OFFLINE");
            response.setActiveConnections(0);
            return;
        }
        response.setStatus("ONLINE");
        response.setPoolSize(pool.getMaximumPoolSize());
        HikariPoolMXBean mxBean = pool.getHikariPoolMXBean();
        response.setActiveConnections(mxBean != null ? mxBean.getActiveConnections() : 0);
    }

    /**
     * 脱敏响应中的敏感字段（URL 数据库名、用户名）。
     *
     * <p>脱敏规则：
     * <ul>
     *   <li>URL：最后一个路径段（数据库名）替换为 ***，如
     *       {@code jdbc:mysql://localhost:3306/apex → jdbc:mysql://localhost:3306/***}</li>
     *   <li>用户名：保留前 2 个字符，其余替换为 ***，如 {@code root → ro***}</li>
     * </ul>
     *
     * @param response 数据源响应
     */
    private void maskSensitiveFields(DataSourceResponse response) {
        String url = response.getUrl();
        if (url != null && url.contains(PATH_SEPARATOR)) {
            int lastSlash = url.lastIndexOf('/');
            response.setUrl(url.substring(0, lastSlash + 1) + "***");
        }
        String username = response.getUsername();
        if (username != null && !username.isBlank()) {
            response.setUsername(
                    username.length() <= USERNAME_MASK_PREFIX
                            ? username + "***"
                            : username.substring(0, USERNAME_MASK_PREFIX) + "***");
        }
    }

    /**
     * 对已排序的全量列表执行内存分页。
     *
     * @param all 全量列表（已按预期顺序排列）
     * @param pageNum 页码（从 1 开始）
     * @param pageSize 每页大小
     * @return 分页结果
     */
    private PageResponse<DataSourceResponse> paginate(List<DataSourceResponse> all, int pageNum, int pageSize) {
        int safePageNum = Math.max(pageNum, 1);
        int safePageSize = Math.max(pageSize, 1);
        int total = all.size();
        int fromIndex = Math.min((safePageNum - 1) * safePageSize, total);
        int toIndex = Math.min(fromIndex + safePageSize, total);
        List<DataSourceResponse> pageContent = all.subList(fromIndex, toIndex);
        return PageResponse.of(pageContent, (long) total, safePageNum, safePageSize);
    }
}
