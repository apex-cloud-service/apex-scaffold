package com.apex.scaffold.auth.application.service;

import com.apex.scaffold.auth.domain.model.entity.RoleEntity;
import com.apex.scaffold.auth.domain.repository.PermissionRepository;
import com.apex.scaffold.auth.domain.repository.RoleRepository;
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

    /**
     * 创建角色，角色编码在同租户内不允许重复
     *
     * @param role 待创建的角色
     * @return 创建后的角色
     */
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

    /**
     * 按主键查询角色。
     *
     * @param id 角色主键
     * @return 角色实体；不存在时以 {@link ResultCode#NOT_FOUND} 业务异常结束
     */
    public Mono<RoleEntity> getRoleById(Long id) {
        return roleRepository
                .findById(id)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "角色不存在")));
    }

    /**
     * 按角色编码查询租户内角色。
     *
     * @param code 角色编码
     * @param tenantId 租户标识
     * @return 角色实体；不存在时以 {@link ResultCode#NOT_FOUND} 业务异常结束
     */
    public Mono<RoleEntity> getRoleByCode(String code, String tenantId) {
        return roleRepository
                .findByCode(code, tenantId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "角色不存在")));
    }

    /**
     * 查询租户下全部角色。
     *
     * @param tenantId 租户标识
     * @return 角色实体流；无数据时为空流
     */
    public Flux<RoleEntity> getRolesByTenantId(String tenantId) {
        return roleRepository.findByTenantId(tenantId);
    }

    /**
     * 查询租户下已启用角色。
     *
     * @param tenantId 租户标识
     * @return 已启用角色流；无数据时为空流
     */
    public Flux<RoleEntity> getEnabledRoles(String tenantId) {
        return roleRepository.findEnabledByTenantId(tenantId);
    }

    /**
     * 查询用户在指定租户下的角色。
     *
     * @param userId 用户标识
     * @param tenantId 租户标识
     * @return 角色实体流；用户未分配角色时为空流
     */
    public Flux<RoleEntity> getUserRoles(Long userId, String tenantId) {
        return roleRepository.findByUserId(userId, tenantId);
    }

    /**
     * 更新角色
     *
     * @param role 待更新的角色
     * @return 更新后的角色
     */
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

    /**
     * 删除角色
     *
     * @param id 角色ID
     * @return 删除完成信号（空完成）
     */
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

    /**
     * 为角色分配权限（全量覆盖该角色的权限集合）
     *
     * @param roleId 角色ID
     * @param permissionIds 权限ID集合
     * @return 分配完成信号（空完成）
     */
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

    /**
     * 为用户分配角色
     *
     * @param userId 用户ID
     * @param roleIds 角色ID集合
     * @param tenantId 租户ID
     * @return 分配完成信号（空完成）
     */
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

    /**
     * 移除用户的指定角色
     *
     * @param userId 用户ID
     * @param roleIds 角色ID集合
     * @param tenantId 租户ID
     * @return 移除完成信号（空完成）
     */
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
     * @return 分配完成信号（空完成）
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
