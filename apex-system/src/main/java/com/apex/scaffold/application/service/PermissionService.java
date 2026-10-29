package com.apex.scaffold.application.service;

import com.apex.scaffold.domain.model.entity.PermissionEntity;
import com.apex.scaffold.domain.repository.PermissionRepository;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 权限服务类
 *
 * <p>提供权限查询、校验和管理等功能，支持基于 RBAC 模型的权限控制
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final UnifiedAuthorizationService unifiedAuthorizationService;

    /**
     * 权限仓储。
     *
     * <p>用于权限数据的持久化读写操作。</p>
     */
    private final PermissionRepository permissionRepository;

    /**
     * 租户写链路守卫。
     */
    private final TenantGuard tenantGuard;

    /**
     * 根据用户 ID 获取权限列表
     *
     * <p>通过用户 ID 查询用户拥有的所有角色，再通过角色查询对应的权限，实现基于角色的权限访问控制
     *
     * @param userId 用户唯一标识
     * @return 权限列表
     */
    public Mono<List<PermissionEntity>> getPermissionsByUserId(Long userId) {
        if (unifiedAuthorizationService != null && unifiedAuthorizationService.enabled()) {
            return unifiedAuthorizationService.permissions(userId);
        }
        return permissionRepository.findByUserId(userId);
    }

    /**
     * 检查用户是否有指定权限
     *
     * <p>通过用户 ID 获取所有权限，然后检查是否包含指定的权限编码，用于单权限点验证场景
     *
     * @param userId 用户唯一标识
     * @param permissionCode 权限编码
     * @return 权限检查结果，true-有权限，false-无权限
     */
    public Mono<Boolean> hasPermission(Long userId, String permissionCode) {
        return getPermissionsByUserId(userId).map(permissions -> permissions.stream()
                .anyMatch(permission -> Objects.equals(permission.getCode(), permissionCode)));
    }

    /**
     * 检查用户是否拥有任何一个权限（OR 模式）
     *
     * <p>用于 OR 模式的权限校验，只要用户拥有权限列表中的任意一个权限即返回 true
     *
     * @param userId 用户唯一标识
     * @param permissionCodes 权限编码列表
     * @return 权限检查结果，true-拥有任一权限，false-无任何权限
     */
    public Mono<Boolean> hasAnyPermission(Long userId, List<String> permissionCodes) {
        return getPermissionsByUserId(userId)
                .map(permissions ->
                        permissions.stream().map(PermissionEntity::getCode).anyMatch(permissionCodes::contains));
    }

    /**
     * 检查用户是否拥有所有权限（AND 模式）
     *
     * <p>用于 AND 模式的权限校验，必须用户拥有所有指定权限才返回 true
     *
     * <p>P0-12修复(SV-3): 增加null/空列表前置检查，防止 Stream.allMatch 对空集合返回 true 的安全漏洞。
     * 当 permissionCodes 为 null 或空时，直接返回 false，避免权限校验被绕过。</p>
     *
     * @param userId 用户唯一标识
     * @param permissionCodes 权限编码列表
     * @return 权限检查结果，true-拥有所有权限，false-缺少部分权限或传入空列表
     */
    public Mono<Boolean> hasAllPermissions(Long userId, List<String> permissionCodes) {
        // P0-12修复(SV-3): 空列表直接返回false，避免Stream.allMatch在空集合上返回true导致权限绕过
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            return Mono.just(false);
        }
        return getPermissionsByUserId(userId).map(permissions -> {
            java.util.Set<String> userPermissions =
                    permissions.stream().map(PermissionEntity::getCode).collect(java.util.stream.Collectors.toSet());
            return permissionCodes.stream().allMatch(userPermissions::contains);
        });
    }

    /**
     * 获取所有权限
     *
     * <p>查询系统中所有的权限定义，返回完整的权限列表
     *
     * @return 权限列表
     */
    public Mono<List<PermissionEntity>> getAllPermissions() {
        return permissionRepository.findAll();
    }

    /**
     * 根据权限编码获取权限
     *
     * <p>通过权限编码查询对应的权限信息，支持精确匹配
     *
     * @param code 权限编码
     * @return 权限实体对象
     */
    public Mono<PermissionEntity> getPermissionByCode(String code) {
        return permissionRepository.findOne("code", code);
    }

    /**
     * 创建权限
     *
     * <p>新增权限定义并持久化到数据库。
     * 方法成功后会记录创建日志。若存在权限编码唯一性约束，则由持久化层抛出相应异常。</p>
     *
     * @param permission 权限实体对象
     * @return 已创建的权限实体对象
     * @throws org.springframework.dao.DuplicateKeyException 当权限编码重复时由持久化层抛出
     */
    public Mono<PermissionEntity> createPermission(PermissionEntity permission) {
        return tenantGuard
                .assertActive()
                .then(Mono.defer(() -> permissionRepository.insert(permission).map(rows -> {
                    log.info("创建权限成功，code: {}, name: {}", permission.getCode(), permission.getName());
                    return permission;
                })));
    }

    /**
     * 更新权限
     *
     * <p>根据权限 ID 查询已有记录后更新权限的名称、描述与类型信息，并持久化到数据库。
     * 方法成功后会记录更新日志。
     *
     * <p>P0-12修复(SV-8): 当前为反应式（Mono）风格，无法直接使用 @Transactional。
     * 存在 find-modify-save 竞态条件风险。建议后续引入版本号（version）字段实现乐观锁，
     * 在 updateById 时通过 WHERE version = ? 条件进行并发控制。
     * 如切换为命令式事务或 R2DBC 事务支持，可加 @Transactional 注解。</p>
     *
     * @param permission 权限实体对象（必须包含 id；name/description/type 为期望更新的值）
     * @return 已更新的权限实体对象
     */
    public Mono<PermissionEntity> updatePermission(PermissionEntity permission) {
        return permissionRepository.findById(permission.getId()).flatMap(existingPermission -> {
            existingPermission.updateInfo(permission.getName(), permission.getDescription(), permission.getType());
            return permissionRepository.updateById(existingPermission).map(rows -> {
                log.info("更新权限成功，id: {}, code: {}", existingPermission.getId(), existingPermission.getCode());
                return existingPermission;
            });
        });
    }

    /**
     * 删除权限
     *
     * <p>从数据库中删除指定的权限记录，并记录日志
     *
     * @param id 权限唯一标识
     * @return 删除结果，true-删除了权限记录（rows &gt; 0），false-未删除任何记录
     */
    public Mono<Boolean> deletePermission(Long id) {
        return permissionRepository.deleteById(id).map(rows -> {
            log.info("删除权限成功，id: {}", id);
            return rows > 0;
        });
    }
}
