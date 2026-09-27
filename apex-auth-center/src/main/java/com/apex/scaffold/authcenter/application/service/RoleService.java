package com.apex.scaffold.authcenter.application.service;

import com.apex.scaffold.authcenter.domain.model.entity.RoleEntity;
import com.apex.scaffold.authcenter.domain.repository.PermissionRepository;
import com.apex.scaffold.authcenter.domain.repository.RoleRepository;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 角色应用服务，负责角色管理与权限分配。
 *
 * @author Apex Scaffold Team
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public Mono<RoleEntity> createRole(RoleEntity role) {
        return roleRepository
                .existsByCode(role.getCode(), role.getTenantId())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "角色编码已存在"));
                    }
                    role.setCreatedAt(LocalDateTime.now());
                    role.setUpdatedAt(LocalDateTime.now());
                    return roleRepository.save(role);
                })
                .doOnSuccess(r -> log.info("角色创建成功: id={}, code={}", r.getId(), r.getCode()));
    }

    public Mono<RoleEntity> getRoleById(Long id) {
        return roleRepository
                .findById(id)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "角色不存在")));
    }

    public Mono<RoleEntity> getRoleByCode(String code, String tenantId) {
        return roleRepository
                .findByCode(code, tenantId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "角色不存在")));
    }

    public Flux<RoleEntity> getRolesByTenantId(String tenantId) {
        return roleRepository.findByTenantId(tenantId);
    }

    public Flux<RoleEntity> getEnabledRoles(String tenantId) {
        return roleRepository.findEnabledByTenantId(tenantId);
    }

    public Flux<RoleEntity> getUserRoles(Long userId, String tenantId) {
        return roleRepository.findByUserId(userId, tenantId);
    }

    public Mono<RoleEntity> updateRole(RoleEntity role) {
        return roleRepository
                .findById(role.getId())
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "角色不存在")))
                .flatMap(existing -> {
                    role.setUpdatedAt(LocalDateTime.now());
                    return roleRepository.update(role);
                })
                .doOnSuccess(r -> log.info("角色更新成功: id={}", r.getId()));
    }

    public Mono<Void> deleteRole(Long id) {
        return roleRepository
                .deleteById(id)
                .flatMap(success -> {
                    if (!success) {
                        return Mono.error(new BusinessException(ResultCode.ERROR, "角色删除失败"));
                    }
                    return Mono.<Void>empty();
                })
                .doOnSuccess(v -> log.info("角色删除成功: id={}", id));
    }

    public Mono<Void> assignPermissionsToRole(Long roleId, Set<Long> permissionIds) {
        return roleRepository
                .findById(roleId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "角色不存在")))
                .flatMap(role -> {
                    role.getPermissionIds().clear();
                    role.getPermissionIds().addAll(permissionIds);
                    role.setUpdatedAt(LocalDateTime.now());
                    return roleRepository.update(role);
                })
                .then()
                .doOnSuccess(v -> log.info("角色权限分配成功: roleId={}, permissions={}", roleId, permissionIds.size()));
    }

    public Mono<Void> assignRolesToUser(Long userId, Set<Long> roleIds, String tenantId) {
        return roleRepository
                .assignRolesToUser(userId, roleIds, tenantId)
                .flatMap(success -> {
                    if (!success) {
                        return Mono.error(new BusinessException(ResultCode.ERROR, "用户角色分配失败"));
                    }
                    return Mono.<Void>empty();
                })
                .doOnSuccess(v -> log.info("用户角色分配成功: userId={}, roles={}", userId, roleIds.size()));
    }

    public Mono<Void> removeRolesFromUser(Long userId, Set<Long> roleIds, String tenantId) {
        return roleRepository
                .removeRolesFromUser(userId, roleIds, tenantId)
                .flatMap(success -> {
                    if (!success) {
                        return Mono.error(new BusinessException(ResultCode.ERROR, "用户角色移除失败"));
                    }
                    return Mono.<Void>empty();
                })
                .doOnSuccess(v -> log.info("用户角色移除成功: userId={}, roles={}", userId, roleIds.size()));
    }

    /**
     * 列出所有角色
     *
     * @return 角色列表
     */
    public Flux<RoleEntity> listRoles() {
        return roleRepository.findAll();
    }

    /**
     * 分配权限给角色
     *
     * @param roleId 角色ID
     * @param permissionIds 权限ID集合
     * @return 完成信号
     */
    public Mono<Void> assignPermissions(Long roleId, Set<Long> permissionIds) {
        return assignPermissionsToRole(roleId, permissionIds);
    }

    /**
     * 获取角色的权限列表
     *
     * @param roleId 角色ID
     * @return 权限ID集合
     */
    public Mono<Set<Long>> getRolePermissions(Long roleId) {
        return roleRepository
                .findById(roleId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "角色不存在")))
                .map(RoleEntity::getPermissionIds);
    }
}
