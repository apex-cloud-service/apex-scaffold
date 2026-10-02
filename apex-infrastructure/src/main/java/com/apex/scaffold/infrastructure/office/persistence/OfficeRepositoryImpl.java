package com.apex.scaffold.infrastructure.office.persistence;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.office.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 办公平台仓储实现
 *
 * <p>实现领域仓储接口，提供办公平台应用配置和身份绑定的持久化能力。
 * 所有 JDBC 操作均使用 {@link BlockingIoSchedulers#jdbc()} 隔离，避免阻塞 WebFlux 事件循环。
 *
 * <p>技术实现：
 * <ul>
 *   <li>MyBatis-Plus：简化 CRUD 操作和条件构造</li>
 *   <li>乐观锁：更新和删除操作校验 version 字段</li>
 *   <li>逻辑删除：deleted 字段标记删除状态</li>
 *   <li>租户隔离：所有查询和更新条件包含 tenant_id</li>
 *   <li>MapStruct：PO 与领域实体双向转换</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Repository
@RequiredArgsConstructor
public class OfficeRepositoryImpl implements OfficeRepository {

    private static final int DEFAULT_PAGE_SIZE = 100;

    private final OfficeAppMapper apps;
    private final OfficeBindingMapper bindings;
    private final OfficeOrganizationMappingMapper organizationMappings;
    private final OfficeSyncTaskMapper syncTasks;
    private final OfficePersistenceConverter converter;

    @Override
    public Mono<OfficePlatformApp> findApp(String tenantId, OfficePlatform platform) {
        return scoped(
                tenantId,
                () -> converter.toApp(apps.selectOne(new QueryWrapper<OfficeAppPO>()
                        .eq("tenant_id", tenantId)
                        .eq("platform", platform.name())
                        .eq("deleted", 0))));
    }

    @Override
    public Mono<OfficeIdentityBinding> findBinding(String tenantId, OfficePlatform platform, String externalUserId) {
        return scoped(
                tenantId,
                () -> converter.toBinding(bindings.selectOne(new QueryWrapper<OfficeBindingPO>()
                        .eq("tenant_id", tenantId)
                        .eq("platform", platform.name())
                        .eq("external_user_id", externalUserId)
                        .eq("deleted", 0))));
    }

    @Override
    public Mono<OfficePlatformApp> saveApp(OfficePlatformApp app) {
        return scoped(app.getTenantId(), () -> {
            OfficeAppPO po = converter.toPo(app);
            int changed;
            if (po.getId() == null) {
                po.setVersion(0);
                po.setDeleted(0);
                changed = apps.insert(po);
            } else {
                if (po.getVersion() == null) {
                    throw new BusinessException(ResultCode.PARAM_ERROR);
                }
                Integer version = po.getVersion();
                po.setVersion(version + 1);
                changed = apps.update(
                        po,
                        new UpdateWrapper<OfficeAppPO>()
                                .eq("id", po.getId())
                                .eq("tenant_id", app.getTenantId())
                                .eq("platform", app.getPlatform().name())
                                .eq("version", version)
                                .eq("deleted", 0));
            }
            if (changed != 1) {
                throw new BusinessException(ResultCode.PARAM_ERROR);
            }
            return converter.toApp(po);
        });
    }

    @Override
    public Mono<java.util.List<OfficePlatformApp>> listApps(String tenantId) {
        return scoped(tenantId, () -> apps
                .selectList(new QueryWrapper<OfficeAppPO>()
                        .eq("tenant_id", tenantId)
                        .eq("deleted", 0)
                        .orderByAsc("platform"))
                .stream()
                .map(converter::toApp)
                .toList());
    }

    @Override
    public Mono<Boolean> deleteApp(String tenantId, OfficePlatform platform, Integer version) {
        return scoped(
                tenantId,
                () -> changed(apps.update(
                        null,
                        new UpdateWrapper<OfficeAppPO>()
                                .eq("tenant_id", tenantId)
                                .eq("platform", platform.name())
                                .eq("version", version)
                                .eq("deleted", 0)
                                .set("deleted", 1)
                                .set("enabled", false)
                                .setSql("version = version + 1"))));
    }

    @Override
    public Mono<OfficeIdentityBinding> findBindingById(String tenantId, Long id) {
        return scoped(
                tenantId,
                () -> converter.toBinding(bindings.selectOne(new QueryWrapper<OfficeBindingPO>()
                        .eq("tenant_id", tenantId)
                        .eq("id", id)
                        .eq("deleted", 0))));
    }

    @Override
    public Mono<java.util.List<OfficeIdentityBinding>> listBindings(
            String tenantId, OfficePlatform platform, int page, int size) {
        return scoped(tenantId, () -> {
            if (page < 1 || size < 1 || size > DEFAULT_PAGE_SIZE) {
                throw new BusinessException(ResultCode.PARAM_ERROR);
            }
            return bindings
                    .selectList(new QueryWrapper<OfficeBindingPO>()
                            .eq("tenant_id", tenantId)
                            .eq("platform", platform.name())
                            .eq("deleted", 0)
                            .orderByAsc("id")
                            .last("LIMIT " + size + " OFFSET " + ((long) (page - 1) * size)))
                    .stream()
                    .map(converter::toBinding)
                    .toList();
        });
    }

    @Override
    public Mono<PageResponse<OfficeIdentityBinding>> listBindingsPage(
            String tenantId, OfficePlatform platform, String status, int page, int size) {
        return scoped(tenantId, () -> {
            validatePage(page, size);
            QueryWrapper<OfficeBindingPO> base = new QueryWrapper<OfficeBindingPO>()
                    .eq("tenant_id", tenantId)
                    .eq("platform", platform.name())
                    .eq("deleted", 0);
            if (status != null && !status.isBlank()) {
                base.eq("status", status);
            }
            Long total = bindings.selectCount(base);
            java.util.List<OfficeIdentityBinding> list = bindings
                    .selectList(base.orderByAsc("id").last("LIMIT " + size + " OFFSET " + ((long) (page - 1) * size)))
                    .stream()
                    .map(converter::toBinding)
                    .toList();
            return PageResponse.of(list, total, page, size);
        });
    }

    @Override
    public Mono<Boolean> changeBindingStatus(
            String tenantId, Long id, Integer version, String expectedStatus, String status) {
        return scoped(
                tenantId,
                () -> changed(bindings.update(
                        null,
                        new UpdateWrapper<OfficeBindingPO>()
                                .eq("tenant_id", tenantId)
                                .eq("id", id)
                                .eq("version", version)
                                .eq("status", expectedStatus)
                                .eq("deleted", 0)
                                .set("status", status)
                                .set("enabled", "APPROVED".equals(status))
                                .setSql("version = version + 1"))));
    }

    @Override
    public Mono<OfficeOrganizationMapping> saveOrganizationMapping(OfficeOrganizationMapping mapping) {
        return scoped(mapping.getTenantId(), () -> {
            OfficeOrganizationMappingPO po = converter.toOrganizationMappingPo(mapping);
            if (po.getId() == null) {
                po.setVersion(0);
                po.setDeleted(0);
                if (organizationMappings.insert(po) != 1) {
                    throw new BusinessException(ResultCode.PARAM_ERROR);
                }
            } else {
                int version = requireVersion(po.getVersion());
                po.setVersion(version + 1);
                if (organizationMappings.update(
                                po,
                                new UpdateWrapper<OfficeOrganizationMappingPO>()
                                        .eq("id", po.getId())
                                        .eq("tenant_id", mapping.getTenantId())
                                        .eq("platform", mapping.getPlatform().name())
                                        .eq("version", version)
                                        .eq("deleted", 0))
                        != 1) {
                    throw new BusinessException(ResultCode.PARAM_ERROR);
                }
            }
            return converter.toOrganizationMapping(po);
        });
    }

    @Override
    public Mono<Integer> batchSaveOrganizationMappings(List<OfficeOrganizationMapping> mappings) {
        return Flux.fromIterable(mappings)
                .flatMap(this::saveOrganizationMapping)
                .collectList()
                .map(List::size);
    }

    @Override
    public Mono<OfficeOrganizationMapping> findDepartmentMapping(
            String tenantId, OfficePlatform platform, String externalOrgId) {
        return scoped(
                tenantId,
                () -> converter.toOrganizationMapping(
                        organizationMappings.selectOne(organizationMappingQuery(tenantId, platform)
                                .eq("external_org_id", externalOrgId)
                                .isNull("external_user_id"))));
    }

    @Override
    public Mono<OfficeOrganizationMapping> findMemberMapping(
            String tenantId, OfficePlatform platform, String externalUserId) {
        return scoped(
                tenantId,
                () -> converter.toOrganizationMapping(
                        organizationMappings.selectOne(new QueryWrapper<OfficeOrganizationMappingPO>()
                                .eq("tenant_id", tenantId)
                                .eq("platform", platform.name())
                                .eq("external_user_id", externalUserId)
                                .eq("deleted", 0))));
    }

    @Override
    public Mono<List<OfficeOrganizationMapping>> listOrganizationMappings(String tenantId, OfficePlatform platform) {
        return scoped(
                tenantId,
                () ->
                        organizationMappings
                                .selectList(organizationMappingQuery(tenantId, platform)
                                        .orderByAsc("id"))
                                .stream()
                                .map(converter::toOrganizationMapping)
                                .toList());
    }

    @Override
    public Mono<OfficeSyncTask> saveSyncTask(OfficeSyncTask task) {
        return scoped(task.getTenantId(), () -> {
            OfficeSyncTaskPO po = converter.toSyncTaskPo(task);
            if (po.getId() == null) {
                po.setVersion(0);
                po.setDeleted(0);
                if (syncTasks.insert(po) != 1) {
                    throw new BusinessException(ResultCode.PARAM_ERROR);
                }
            } else {
                int version = requireVersion(po.getVersion());
                po.setVersion(version + 1);
                if (syncTasks.update(
                                po,
                                new UpdateWrapper<OfficeSyncTaskPO>()
                                        .eq("id", po.getId())
                                        .eq("tenant_id", task.getTenantId())
                                        .eq("platform", task.getPlatform().name())
                                        .eq("version", version)
                                        .eq("deleted", 0))
                        != 1) {
                    throw new BusinessException(ResultCode.PARAM_ERROR);
                }
            }
            return converter.toSyncTask(po);
        });
    }

    @Override
    public Mono<OfficeSyncTask> findLatestSyncTask(String tenantId, OfficePlatform platform) {
        return scoped(
                tenantId,
                () -> converter.toSyncTask(syncTasks.selectOne(new QueryWrapper<OfficeSyncTaskPO>()
                        .eq("tenant_id", tenantId)
                        .eq("platform", platform.name())
                        .eq("deleted", 0)
                        .orderByDesc("id")
                        .last("LIMIT 1"))));
    }

    @Override
    public Mono<List<OfficeSyncTask>> listSyncTasks(String tenantId, OfficePlatform platform, int page, int size) {
        return scoped(tenantId, () -> {
            validatePage(page, size);
            return syncTasks
                    .selectList(syncTaskQuery(tenantId, platform)
                            .orderByDesc("id")
                            .last("LIMIT " + size + " OFFSET " + ((long) (page - 1) * size)))
                    .stream()
                    .map(converter::toSyncTask)
                    .toList();
        });
    }

    private QueryWrapper<OfficeOrganizationMappingPO> organizationMappingQuery(
            String tenantId, OfficePlatform platform) {
        QueryWrapper<OfficeOrganizationMappingPO> query = new QueryWrapper<OfficeOrganizationMappingPO>()
                .eq("tenant_id", tenantId)
                .eq("deleted", 0);
        if (platform != null) {
            query.eq("platform", platform.name());
        }
        return query;
    }

    private QueryWrapper<OfficeSyncTaskPO> syncTaskQuery(String tenantId, OfficePlatform platform) {
        QueryWrapper<OfficeSyncTaskPO> query =
                new QueryWrapper<OfficeSyncTaskPO>().eq("tenant_id", tenantId).eq("deleted", 0);
        if (platform != null) {
            query.eq("platform", platform.name());
        }
        return query;
    }

    private int requireVersion(Integer version) {
        if (version == null || version < 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }
        return version;
    }

    private void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > DEFAULT_PAGE_SIZE) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }
    }

    /**
     * 校验更新影响行数
     *
     * @param count 影响行数
     * @return true-更新成功
     * @throws BusinessException 影响行数不为 1 时（记录不存在或版本冲突）
     */
    private boolean changed(int count) {
        if (count != 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }
        return true;
    }

    /**
     * 在租户范围和 JDBC 调度器中执行阻塞操作
     *
     * <p>执行前校验租户上下文与参数匹配，避免跨租户操作。
     * 使用 {@link BlockingIoSchedulers#jdbc()} 隔离 JDBC 阻塞调用。
     *
     * @param tenantId 租户标识
     * @param operation 阻塞操作
     * @param <T> 结果类型
     * @return 响应式结果
     * @throws BusinessException 租户校验失败时返回 FORBIDDEN
     */
    private <T> Mono<T> scoped(String tenantId, java.util.concurrent.Callable<T> operation) {
        return Mono.defer(() -> {
            if (tenantId == null
                    || tenantId.isBlank()
                    || !TenantContext.currentTenantId().filter(tenantId::equals).isPresent()) {
                return Mono.error(new BusinessException(ResultCode.FORBIDDEN));
            }
            return Mono.fromCallable(operation).subscribeOn(BlockingIoSchedulers.jdbc());
        });
    }
}
