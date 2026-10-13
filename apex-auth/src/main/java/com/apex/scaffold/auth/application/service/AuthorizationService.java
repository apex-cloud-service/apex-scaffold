package com.apex.scaffold.auth.application.service;

import com.apex.scaffold.auth.domain.model.entity.PermissionEntity;
import com.apex.scaffold.auth.domain.model.entity.RoleEntity;
import com.apex.scaffold.auth.domain.repository.RoleRepository;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 权限校验服务
 *
 * <p>提供基于角色的访问控制（RBAC）权限验证功能。
 * 支持用户权限检查、角色权限验证和资源访问控制。
 *
 * @author Apex Scaffold Team
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorizationService {

    private static final String DATA_SCOPE_ALL = "ALL";
    private static final String DATA_SCOPE_DEPT_AND_SUB = "DEPT_AND_SUB";
    private static final String DATA_SCOPE_DEPT = "DEPT";
    private static final String DATA_SCOPE_SELF = "SELF";

    private final RoleService roleService;
    private final PermissionService permissionService;
    private final RoleRepository roleRepository;

    /**
     * 检查用户是否拥有指定权限
     *
     * <p>通过用户的角色来判断是否拥有某个权限。支持超级管理员角色自动通过所有权限检查。
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @param permissionCode 权限编码
     * @return 如果用户拥有该权限返回true
     */
    public Mono<Boolean> hasPermission(Long userId, String tenantId, String permissionCode) {
        return roleService
                .getUserRoles(userId, tenantId)
                .filter(RoleEntity::isValid)
                .flatMap(role -> {
                    // 超级管理员自动拥有所有权限
                    if (role.isSuperRole()) {
                        return Mono.just(true);
                    }
                    // 检查角色的权限
                    return permissionService
                            .getPermissionsByIds(role.getPermissionIds())
                            .filter(PermissionEntity::isValid)
                            .any(permission -> permission.getCode().equals(permissionCode));
                })
                .any(hasPermission -> hasPermission)
                .defaultIfEmpty(false)
                .doOnSuccess(hasPermission ->
                        log.debug("权限检查: userId={}, permission={}, result={}", userId, permissionCode, hasPermission));
    }

    /**
     * 检查用户是否拥有任意一个指定权限
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @param permissionCodes 权限编码集合
     * @return 如果用户拥有任意一个权限返回true
     */
    public Mono<Boolean> hasAnyPermission(Long userId, String tenantId, Set<String> permissionCodes) {
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            return Mono.just(false);
        }

        return roleService
                .getUserRoles(userId, tenantId)
                .filter(RoleEntity::isValid)
                .flatMap(role -> {
                    // 超级管理员自动拥有所有权限
                    if (role.isSuperRole()) {
                        return Mono.just(true);
                    }
                    // 检查角色的权限
                    return permissionService
                            .getPermissionsByIds(role.getPermissionIds())
                            .filter(PermissionEntity::isValid)
                            .map(PermissionEntity::getCode)
                            .collect(Collectors.toSet())
                            .map(userPermissions -> userPermissions.stream().anyMatch(permissionCodes::contains));
                })
                .any(hasPermission -> hasPermission)
                .defaultIfEmpty(false);
    }

    /**
     * 检查用户是否拥有所有指定权限
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @param permissionCodes 权限编码集合
     * @return 如果用户拥有所有权限返回true
     */
    public Mono<Boolean> hasAllPermissions(Long userId, String tenantId, Set<String> permissionCodes) {
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            return Mono.just(true);
        }

        return roleService
                .getUserRoles(userId, tenantId)
                .filter(RoleEntity::isValid)
                .flatMap(role -> {
                    // 超级管理员自动拥有所有权限
                    if (role.isSuperRole()) {
                        return Mono.just(permissionCodes);
                    }
                    // 收集角色的所有权限
                    return permissionService
                            .getPermissionsByIds(role.getPermissionIds())
                            .filter(PermissionEntity::isValid)
                            .map(PermissionEntity::getCode)
                            .collect(Collectors.toSet());
                })
                .reduce(new HashSet<>(), (acc, perms) -> {
                    acc.addAll(perms);
                    return acc;
                })
                .map(userPermissions -> userPermissions.containsAll(permissionCodes))
                .defaultIfEmpty(false);
    }

    /**
     * 检查用户是否拥有指定角色
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @param roleCode 角色编码
     * @return 如果用户拥有该角色返回true
     */
    public Mono<Boolean> hasRole(Long userId, String tenantId, String roleCode) {
        return roleService
                .getUserRoles(userId, tenantId)
                .filter(RoleEntity::isValid)
                .any(role -> role.getCode().equals(roleCode))
                .defaultIfEmpty(false)
                .doOnSuccess(hasRole -> log.debug("角色检查: userId={}, role={}, result={}", userId, roleCode, hasRole));
    }

    /**
     * 检查用户是否拥有任意一个指定角色
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @param roleCodes 角色编码集合
     * @return 如果用户拥有任意一个角色返回true
     */
    public Mono<Boolean> hasAnyRole(Long userId, String tenantId, Set<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return Mono.just(false);
        }

        return roleService
                .getUserRoles(userId, tenantId)
                .filter(RoleEntity::isValid)
                .map(RoleEntity::getCode)
                .collect(Collectors.toSet())
                .map(userRoles -> userRoles.stream().anyMatch(roleCodes::contains))
                .defaultIfEmpty(false);
    }

    /**
     * 检查用户是否为超级管理员
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @return 如果用户是超级管理员返回true
     */
    public Mono<Boolean> isSuperAdmin(Long userId, String tenantId) {
        return roleService
                .getUserRoles(userId, tenantId)
                .filter(RoleEntity::isValid)
                .any(RoleEntity::isSuperRole)
                .defaultIfEmpty(false);
    }

    /**
     * 获取用户的所有权限编码
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @return 权限编码集合
     */
    public Mono<Set<String>> getUserPermissions(Long userId, String tenantId) {
        return roleService
                .getUserRoles(userId, tenantId)
                .filter(RoleEntity::isValid)
                .flatMap(role -> {
                    // 超级管理员返回所有权限
                    if (role.isSuperRole()) {
                        return permissionService.getEnabledPermissions(tenantId).map(PermissionEntity::getCode);
                    }
                    // 普通角色返回关联的权限
                    return permissionService
                            .getPermissionsByIds(role.getPermissionIds())
                            .filter(PermissionEntity::isValid)
                            .map(PermissionEntity::getCode);
                })
                .collect(Collectors.toSet())
                .doOnSuccess(permissions -> log.debug("用户权限列表: userId={}, permissions={}", userId, permissions.size()));
    }

    /**
     * 检查用户是否有权访问指定资源
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @param resourceIdentifier 资源标识
     * @param action 操作类型
     * @return 如果用户有权访问返回true
     */
    public Mono<Boolean> canAccessResource(Long userId, String tenantId, String resourceIdentifier, String action) {
        return roleService
                .getUserRoles(userId, tenantId)
                .filter(RoleEntity::isValid)
                .flatMap(role -> {
                    // 超级管理员可以访问所有资源
                    if (role.isSuperRole()) {
                        return Mono.just(true);
                    }
                    // 检查角色权限是否匹配资源和操作
                    return permissionService
                            .getPermissionsByIds(role.getPermissionIds())
                            .filter(PermissionEntity::isValid)
                            .any(permission ->
                                    permission.matchesResource(resourceIdentifier) && permission.matchesAction(action));
                })
                .any(canAccess -> canAccess)
                .defaultIfEmpty(false)
                .doOnSuccess(canAccess -> log.debug(
                        "资源访问检查: userId={}, resource={}, action={}, result={}",
                        userId,
                        resourceIdentifier,
                        action,
                        canAccess));
    }

    /**
     * 获取用户的数据权限范围
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @return 数据权限范围（ALL、DEPT_AND_SUB、DEPT、SELF）
     */
    public Mono<String> getUserDataScope(Long userId, String tenantId) {
        return roleService
                .getUserRoles(userId, tenantId)
                .filter(RoleEntity::isValid)
                .map(RoleEntity::getDataScope)
                .reduce((scope1, scope2) -> {
                    // 返回权限最大的数据范围
                    if (DATA_SCOPE_ALL.equals(scope1) || DATA_SCOPE_ALL.equals(scope2)) {
                        return DATA_SCOPE_ALL;
                    }
                    if (DATA_SCOPE_DEPT_AND_SUB.equals(scope1) || DATA_SCOPE_DEPT_AND_SUB.equals(scope2)) {
                        return DATA_SCOPE_DEPT_AND_SUB;
                    }
                    if (DATA_SCOPE_DEPT.equals(scope1) || DATA_SCOPE_DEPT.equals(scope2)) {
                        return DATA_SCOPE_DEPT;
                    }
                    return DATA_SCOPE_SELF;
                })
                .defaultIfEmpty(DATA_SCOPE_SELF);
    }
}
