package com.apex.scaffold.auth.infrastructure.persistence.repository;

import com.apex.scaffold.auth.domain.model.entity.RoleEntity;
import com.apex.scaffold.auth.domain.repository.RoleRepository;
import com.apex.scaffold.auth.infrastructure.persistence.converter.RoleConverter;
import com.apex.scaffold.auth.infrastructure.persistence.mapper.*;
import com.apex.scaffold.auth.infrastructure.persistence.po.*;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
public class RoleRepositoryImpl implements RoleRepository {
    private final RoleMapper mapper;
    private final RoleConverter converter;
    private final PermissionMapper permissions;
    private final RolePermissionMapper permissionLinks;
    private final UserRoleMapper userLinks;
    private final UserMapper users;
    private final PlatformTransactionManager transactionManager;

    @Override
    public Mono<RoleEntity> save(RoleEntity role) {
        return transactional(role.getTenantId(), t -> {
            RolePO po = converter.toPo(role);
            po.setTenantId(t);
            po.setDeleted(0);
            po.setVersion(0);
            po.setCreatedAt(LocalDateTime.now());
            po.setUpdatedAt(po.getCreatedAt());
            if (po.getEnabled() == null) {
                po.setEnabled(1);
            }
            if (po.getDataScope() == null) {
                po.setDataScope("0");
            }
            if (po.getSortOrder() == null) {
                po.setSortOrder(0);
            }
            var ids = JdbcTenantScope.ids(role.getPermissionIds());
            validatePermissions(t, ids);
            JdbcTenantScope.changed(mapper.insert(po));
            replacePermissions(t, po.getId(), ids);
            return hydrate(t, po);
        });
    }

    @Override
    public Mono<RoleEntity> update(RoleEntity role) {
        return transactional(role.getTenantId(), t -> {
            if (role.getId() == null || role.getVersion() == null) {
                throw new BusinessException(ResultCode.PARAM_ERROR);
            }
            var ids = JdbcTenantScope.ids(role.getPermissionIds());
            validatePermissions(t, ids);
            RolePO po = converter.toPo(role);
            po.setTenantId(t);
            po.setCreatedAt(null);
            po.setUpdatedAt(LocalDateTime.now());
            po.setVersion(role.getVersion() + 1);
            JdbcTenantScope.changed(mapper.update(
                    po, JdbcTenantScope.<RolePO>update(t).eq("id", po.getId()).eq("version", role.getVersion())));
            replacePermissions(t, po.getId(), ids);
            return hydrate(t, mapper.selectOne(JdbcTenantScope.<RolePO>query(t).eq("id", po.getId())));
        });
    }

    @Override
    public Mono<RoleEntity> findById(Long id) {
        return JdbcTenantScope.call(
                null,
                t -> hydrate(
                        t, mapper.selectOne(JdbcTenantScope.<RolePO>query(t).eq("id", id))));
    }

    @Override
    public Flux<RoleEntity> findAll() {
        return JdbcTenantScope.call(null, t -> mapper.selectList(JdbcTenantScope.<RolePO>query(t)).stream()
                        .map(po -> hydrate(t, po))
                        .toList())
                .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Mono<RoleEntity> findByCode(String code, String tenantId) {
        return JdbcTenantScope.call(
                tenantId,
                t -> hydrate(
                        t, mapper.selectOne(JdbcTenantScope.<RolePO>query(t).eq("code", code))));
    }

    @Override
    public Flux<RoleEntity> findByIds(Iterable<Long> ids) {
        return JdbcTenantScope.call(null, t -> {
                    var values = JdbcTenantScope.ids(ids);
                    return values.isEmpty()
                            ? List.<RoleEntity>of()
                            : mapper.selectList(JdbcTenantScope.<RolePO>query(t).in("id", values)).stream()
                                    .map(po -> hydrate(t, po))
                                    .toList();
                })
                .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Flux<RoleEntity> findByTenantId(String tenantId) {
        return JdbcTenantScope.call(tenantId, t -> mapper.selectList(JdbcTenantScope.<RolePO>query(t)).stream()
                        .map(po -> hydrate(t, po))
                        .toList())
                .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Flux<RoleEntity> findEnabledByTenantId(String tenantId) {
        return JdbcTenantScope.call(
                        tenantId,
                        t -> mapper.selectList(JdbcTenantScope.<RolePO>query(t).eq("enabled", 1)).stream()
                                .map(po -> hydrate(t, po))
                                .toList())
                .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Flux<RoleEntity> findByUserId(Long userId, String tenantId) {
        return JdbcTenantScope.call(tenantId, t -> {
                    validateUser(t, userId);
                    var ids = userLinks
                            .selectList(JdbcTenantScope.<UserRolePO>query(t).eq("user_id", userId))
                            .stream()
                            .map(UserRolePO::getRoleId)
                            .toList();
                    return ids.isEmpty()
                            ? List.<RoleEntity>of()
                            : mapper.selectList(JdbcTenantScope.<RolePO>query(t).in("id", ids)).stream()
                                    .map(po -> hydrate(t, po))
                                    .toList();
                })
                .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Mono<Boolean> existsByCode(String code, String tenantId) {
        return JdbcTenantScope.call(
                tenantId,
                t -> mapper.selectCount(JdbcTenantScope.<RolePO>query(t).eq("code", code)) > 0);
    }

    @Override
    public Mono<Boolean> deleteById(Long id) {
        return transactional(null, t -> {
            int count = mapper.delete(JdbcTenantScope.<RolePO>query(t).eq("id", id));
            if (count == 0) {
                return false;
            }
            permissionLinks.delete(JdbcTenantScope.<RolePermissionPO>query(t).eq("role_id", id));
            userLinks.delete(JdbcTenantScope.<UserRolePO>query(t).eq("role_id", id));
            return true;
        });
    }

    @Override
    public Mono<Boolean> assignRolesToUser(Long userId, Iterable<Long> roleIds, String tenantId) {
        return transactional(tenantId, t -> {
            validateUser(t, userId);
            var ids = JdbcTenantScope.ids(roleIds);
            validateRoles(t, ids);
            for (Long id : ids) {
                if (userLinks.selectCount(JdbcTenantScope.<UserRolePO>query(t)
                                .eq("user_id", userId)
                                .eq("role_id", id))
                        == 0) {
                    JdbcTenantScope.changed(userLinks.insert(UserRolePO.builder()
                            .tenantId(t)
                            .userId(userId)
                            .roleId(id)
                            .createdAt(LocalDateTime.now())
                            .build()));
                }
            }
            return true;
        });
    }

    @Override
    public Mono<Boolean> removeRolesFromUser(Long userId, Iterable<Long> roleIds, String tenantId) {
        return transactional(tenantId, t -> {
            validateUser(t, userId);
            var ids = JdbcTenantScope.ids(roleIds);
            validateRoles(t, ids);
            if (!ids.isEmpty()) {
                userLinks.delete(JdbcTenantScope.<UserRolePO>query(t)
                        .eq("user_id", userId)
                        .in("role_id", ids));
            }
            return true;
        });
    }

    private RoleEntity hydrate(String tenant, RolePO po) {
        if (po == null) {
            return null;
        }
        RoleEntity entity = converter.toEntity(po);
        var ids =
                permissionLinks
                        .selectList(
                                JdbcTenantScope.<RolePermissionPO>query(tenant).eq("role_id", po.getId()))
                        .stream()
                        .map(RolePermissionPO::getPermissionId)
                        .toList();
        entity.setPermissionIds(
                ids.isEmpty()
                        ? new HashSet<>()
                        : new HashSet<>(
                                permissions
                                        .selectList(JdbcTenantScope.<PermissionPO>query(tenant)
                                                .in("id", ids))
                                        .stream()
                                        .map(PermissionPO::getId)
                                        .toList()));
        return entity;
    }

    private void validateUser(String tenant, Long id) {
        if (id == null
                || users.selectCount(JdbcTenantScope.<UserPO>query(tenant).eq("id", id)) != 1) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }

    private void validateRoles(String tenant, List<Long> ids) {
        if (!ids.isEmpty()
                && mapper.selectCount(JdbcTenantScope.<RolePO>query(tenant).in("id", ids)) != ids.size()) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }

    private void validatePermissions(String tenant, List<Long> ids) {
        if (!ids.isEmpty()
                && permissions.selectCount(
                                JdbcTenantScope.<PermissionPO>query(tenant).in("id", ids))
                        != ids.size()) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }

    private void replacePermissions(String tenant, Long roleId, List<Long> ids) {
        permissionLinks.delete(JdbcTenantScope.<RolePermissionPO>query(tenant).eq("role_id", roleId));
        for (Long id : ids) {
            JdbcTenantScope.changed(permissionLinks.insert(RolePermissionPO.builder()
                    .tenantId(tenant)
                    .roleId(roleId)
                    .permissionId(id)
                    .createdAt(LocalDateTime.now())
                    .build()));
        }
    }

    private <T> Mono<T> transactional(String tenant, Function<String, T> action) {
        return JdbcTenantScope.call(
                tenant, t -> new TransactionTemplate(transactionManager).execute(status -> action.apply(t)));
    }
}
