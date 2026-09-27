package com.apex.scaffold.authcenter.infrastructure.persistence.repository;

import com.apex.scaffold.authcenter.domain.model.entity.PermissionEntity;
import com.apex.scaffold.authcenter.domain.repository.PermissionRepository;
import com.apex.scaffold.authcenter.infrastructure.persistence.converter.PermissionConverter;
import com.apex.scaffold.authcenter.infrastructure.persistence.mapper.*;
import com.apex.scaffold.authcenter.infrastructure.persistence.po.*;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 租户隔离的认证仓储实现。
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Repository
@RequiredArgsConstructor
public class PermissionRepositoryImpl implements PermissionRepository {
    private static final String ID_COLUMN = "id";
    private final PermissionMapper mapper;
    private final PermissionConverter converter;
    private final RoleMapper roles;
    private final RolePermissionMapper links;

    @Override
    public Mono<PermissionEntity> save(PermissionEntity entity) {
        return JdbcTenantScope.call(entity.getTenantId(), t -> {
            PermissionPO po = converter.toPo(entity);
            po.setTenantId(t);
            po.setCreatedAt(LocalDateTime.now());
            po.setUpdatedAt(po.getCreatedAt());
            po.setDeleted(0);
            po.setVersion(0);
            if (po.getEnabled() == null) {
                po.setEnabled(1);
            }
            JdbcTenantScope.changed(mapper.insert(po));
            return converter.toEntity(po);
        });
    }

    @Override
    public Mono<PermissionEntity> findById(Long id) {
        return JdbcTenantScope.call(
                null,
                t -> converter.toEntity(
                        mapper.selectOne(JdbcTenantScope.<PermissionPO>query(t).eq("id", id))));
    }

    @Override
    public Flux<PermissionEntity> findAll() {
        return JdbcTenantScope.call(null, t -> mapper.selectList(JdbcTenantScope.<PermissionPO>query(t)))
                .flatMapMany(Flux::fromIterable)
                .map(converter::toEntity);
    }

    @Override
    public Flux<PermissionEntity> findByResource(String resource) {
        return JdbcTenantScope.call(
                        null,
                        t -> mapper.selectList(
                                JdbcTenantScope.<PermissionPO>query(t).eq("resource", resource)))
                .flatMapMany(Flux::fromIterable)
                .map(converter::toEntity);
    }

    @Override
    public Mono<PermissionEntity> update(PermissionEntity entity) {
        return JdbcTenantScope.call(entity.getTenantId(), t -> {
            if (entity.getId() == null) {
                throw new BusinessException(ResultCode.PARAM_ERROR);
            }
            PermissionPO po = converter.toPo(entity);
            po.setTenantId(t);
            po.setCreatedAt(null);
            po.setUpdatedAt(LocalDateTime.now());
            var query = JdbcTenantScope.<PermissionPO>update(t).eq("id", po.getId());
            if (po.getVersion() == null) {
                throw new BusinessException(ResultCode.PARAM_ERROR);
            }
            query.eq("version", po.getVersion());
            po.setVersion(po.getVersion() + 1);
            JdbcTenantScope.changed(mapper.update(po, query));
            return converter.toEntity(
                    mapper.selectOne(JdbcTenantScope.<PermissionPO>query(t).eq("id", po.getId())));
        });
    }

    @Override
    public Mono<Boolean> deleteById(Long id) {
        return JdbcTenantScope.call(
                null, t -> mapper.delete(JdbcTenantScope.<PermissionPO>query(t).eq("id", id)) > 0);
    }

    @Override
    public Mono<PermissionEntity> findByCode(String code, String tenantId) {
        return JdbcTenantScope.call(
                tenantId,
                t -> converter.toEntity(
                        mapper.selectOne(JdbcTenantScope.<PermissionPO>query(t).eq("code", code))));
    }

    @Override
    public Mono<Boolean> existsByCode(String code, String tenantId) {
        return JdbcTenantScope.call(
                tenantId,
                t -> mapper.selectCount(JdbcTenantScope.<PermissionPO>query(t).eq("code", code)) > 0);
    }

    @Override
    public Flux<PermissionEntity> findByTenantId(String tenantId) {
        return JdbcTenantScope.call(tenantId, t -> mapper.selectList(JdbcTenantScope.<PermissionPO>query(t)))
                .flatMapMany(Flux::fromIterable)
                .map(converter::toEntity);
    }

    @Override
    public Flux<PermissionEntity> findEnabledByTenantId(String tenantId) {
        return JdbcTenantScope.call(
                        tenantId,
                        t -> mapper.selectList(
                                JdbcTenantScope.<PermissionPO>query(t).eq("enabled", 1)))
                .flatMapMany(Flux::fromIterable)
                .map(converter::toEntity);
    }

    @Override
    public Flux<PermissionEntity> findByResourceType(String resourceType, String tenantId) {
        return JdbcTenantScope.call(
                        tenantId,
                        t -> mapper.selectList(
                                JdbcTenantScope.<PermissionPO>query(t).eq("resource_type", resourceType)))
                .flatMapMany(Flux::fromIterable)
                .map(converter::toEntity);
    }

    @Override
    public Flux<PermissionEntity> findByIds(Iterable<Long> ids) {
        return JdbcTenantScope.call(null, t -> {
                    java.util.List<Long> values = JdbcTenantScope.ids(ids);
                    return values.isEmpty()
                            ? java.util.List.<PermissionPO>of()
                            : mapper.selectList(
                                    JdbcTenantScope.<PermissionPO>query(t).in("id", values));
                })
                .flatMapMany(Flux::fromIterable)
                .map(converter::toEntity);
    }

    @Override
    public Flux<PermissionEntity> findByRoleId(Long roleId) {
        return JdbcTenantScope.call(null, t -> {
                    if (roles.selectCount(JdbcTenantScope.<RolePO>query(t).eq(ID_COLUMN, roleId)) == 0) {
                        return java.util.List.<PermissionPO>of();
                    }
                    var ids =
                            links
                                    .selectList(JdbcTenantScope.<RolePermissionPO>query(t)
                                            .eq("role_id", roleId))
                                    .stream()
                                    .map(RolePermissionPO::getPermissionId)
                                    .toList();
                    return ids.isEmpty()
                            ? java.util.List.<PermissionPO>of()
                            : mapper.selectList(
                                    JdbcTenantScope.<PermissionPO>query(t).in("id", ids));
                })
                .flatMapMany(Flux::fromIterable)
                .map(converter::toEntity);
    }
}
