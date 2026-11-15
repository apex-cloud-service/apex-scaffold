package com.apex.scaffold.auth.application.service;

import com.apex.scaffold.auth.domain.model.entity.PermissionEntity;
import com.apex.scaffold.auth.domain.repository.PermissionRepository;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 权限服务
 *
 * <p>提供权限管理的核心业务逻辑，包括权限的创建、查询、更新和删除。
 * 支持基于角色的权限查询和权限验证。
 *
 * @author Apex Scaffold Team
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionRepository permissionRepository;

    /**
     * 创建权限
     *
     * @param permission 权限实体
     * @return 创建后的权限实体
     */
    public Mono<PermissionEntity> createPermission(PermissionEntity permission) {
        return permissionRepository
                .existsByCode(permission.getCode(), permission.getTenantId())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "权限编码已存在"));
                    }
                    permission.setCreatedAt(LocalDateTime.now());
                    permission.setUpdatedAt(LocalDateTime.now());
                    return permissionRepository.save(permission);
                })
                .doOnSuccess(p -> log.info("权限创建成功: id={}, code={}", p.getId(), p.getCode()));
    }

    /**
     * 根据ID获取权限
     *
     * @param id 权限ID
     * @return 权限实体
     */
    public Mono<PermissionEntity> getPermissionById(Long id) {
        return permissionRepository
                .findById(id)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "权限不存在")));
    }

    /**
     * 根据权限编码获取权限
     *
     * @param code 权限编码
     * @param tenantId 租户ID
     * @return 权限实体
     */
    public Mono<PermissionEntity> getPermissionByCode(String code, String tenantId) {
        return permissionRepository
                .findByCode(code, tenantId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "权限不存在")));
    }

    /**
     * 根据租户ID获取所有权限
     *
     * @param tenantId 租户ID
     * @return 权限列表
     */
    public Flux<PermissionEntity> getPermissionsByTenantId(String tenantId) {
        return permissionRepository.findByTenantId(tenantId);
    }

    /**
     * 获取启用状态的权限
     *
     * @param tenantId 租户ID
     * @return 启用的权限列表
     */
    public Flux<PermissionEntity> getEnabledPermissions(String tenantId) {
        return permissionRepository.findEnabledByTenantId(tenantId);
    }

    /**
     * 根据资源类型获取权限
     *
     * @param resourceType 资源类型
     * @param tenantId 租户ID
     * @return 权限列表
     */
    public Flux<PermissionEntity> getPermissionsByResourceType(String resourceType, String tenantId) {
        return permissionRepository.findByResourceType(resourceType, tenantId);
    }

    /**
     * 根据角色ID获取权限
     *
     * @param roleId 角色ID
     * @return 权限列表
     */
    public Flux<PermissionEntity> getPermissionsByRoleId(Long roleId) {
        return permissionRepository.findByRoleId(roleId);
    }

    /**
     * 批量获取权限
     *
     * @param permissionIds 权限ID集合
     * @return 权限列表
     */
    public Flux<PermissionEntity> getPermissionsByIds(Set<Long> permissionIds) {
        return permissionRepository.findByIds(permissionIds);
    }

    /**
     * 更新权限
     *
     * @param permission 权限实体
     * @return 更新后的权限实体
     */
    public Mono<PermissionEntity> updatePermission(PermissionEntity permission) {
        return permissionRepository
                .findById(permission.getId())
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "权限不存在")))
                .flatMap(existing -> {
                    permission.setUpdatedAt(LocalDateTime.now());
                    return permissionRepository.update(permission);
                })
                .doOnSuccess(p -> log.info("权限更新成功: id={}", p.getId()));
    }

    /**
     * 删除权限
     *
     * @param id 权限ID
     * @return 删除完成信号（空完成）
     */
    public Mono<Void> deletePermission(Long id) {
        return permissionRepository
                .deleteById(id)
                .flatMap(success -> {
                    if (!success) {
                        return Mono.error(new BusinessException(ResultCode.ERROR, "权限删除失败"));
                    }
                    return Mono.<Void>empty();
                })
                .doOnSuccess(v -> log.info("权限删除成功: id={}", id));
    }

    /**
     * 批量创建权限
     *
     * @param permissions 权限列表
     * @return 创建的权限列表
     */
    public Flux<PermissionEntity> batchCreatePermissions(List<PermissionEntity> permissions) {
        return Flux.fromIterable(permissions)
                .flatMap(this::createPermission)
                .doOnComplete(() -> log.info("批量创建权限完成: count={}", permissions.size()));
    }

    /**
     * 检查权限编码是否存在
     *
     * @param code 权限编码
     * @param tenantId 租户ID
     * @return 是否存在
     */
    public Mono<Boolean> existsByCode(String code, String tenantId) {
        return permissionRepository.existsByCode(code, tenantId);
    }

    /**
     * 列出所有权限
     *
     * @return 权限列表
     */
    public Flux<PermissionEntity> listPermissions() {
        return permissionRepository.findAll();
    }

    /**
     * 根据资源获取权限
     *
     * @param resource 资源标识
     * @return 权限列表
     */
    public Flux<PermissionEntity> getPermissionsByResource(String resource) {
        return permissionRepository.findByResource(resource);
    }
}
