package com.apex.scaffold.auth.domain.repository;

import com.apex.scaffold.auth.domain.model.entity.PermissionEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 权限仓储接口
 *
 * <p>定义权限实体的持久化操作，遵循DDD仓储模式。
 *
 * @author Apex Scaffold Team
 * @since 1.2.0
 */
public interface PermissionRepository {

    /**
     * 保存权限
     *
     * @param permission 权限实体
     * @return 保存后的权限实体
     */
    Mono<PermissionEntity> save(PermissionEntity permission);

    /**
     * 根据ID查找权限
     *
     * @param id 权限ID
     * @return 权限实体，如果不存在返回空
     */
    Mono<PermissionEntity> findById(Long id);

    /**
     * 查找所有权限
     *
     * @return 所有权限实体流
     */
    Flux<PermissionEntity> findAll();

    /**
     * 根据资源标识查找权限
     *
     * @param resource 资源标识
     * @return 权限实体流
     */
    Flux<PermissionEntity> findByResource(String resource);

    /**
     * 根据权限编码查找权限
     *
     * @param code 权限编码
     * @param tenantId 租户ID
     * @return 权限实体，如果不存在返回空
     */
    Mono<PermissionEntity> findByCode(String code, String tenantId);

    /**
     * 根据ID集合批量查找权限
     *
     * @param ids 权限ID集合
     * @return 权限实体流
     */
    Flux<PermissionEntity> findByIds(Iterable<Long> ids);

    /**
     * 根据租户ID查询所有权限
     *
     * @param tenantId 租户ID
     * @return 权限实体流
     */
    Flux<PermissionEntity> findByTenantId(String tenantId);

    /**
     * 根据资源类型查询权限
     *
     * @param resourceType 资源类型
     * @param tenantId 租户ID
     * @return 权限实体流
     */
    Flux<PermissionEntity> findByResourceType(String resourceType, String tenantId);

    /**
     * 根据角色ID查找角色拥有的所有权限
     *
     * @param roleId 角色ID
     * @return 权限实体流
     */
    Flux<PermissionEntity> findByRoleId(Long roleId);

    /**
     * 更新权限
     *
     * @param permission 权限实体
     * @return 更新后的权限实体
     */
    Mono<PermissionEntity> update(PermissionEntity permission);

    /**
     * 删除权限（逻辑删除）
     *
     * @param id 权限ID
     * @return 删除是否成功
     */
    Mono<Boolean> deleteById(Long id);

    /**
     * 检查权限编码是否存在
     *
     * @param code 权限编码
     * @param tenantId 租户ID
     * @return 如果存在返回true
     */
    Mono<Boolean> existsByCode(String code, String tenantId);

    /**
     * 查询启用状态的权限
     *
     * @param tenantId 租户ID
     * @return 启用的权限实体流
     */
    Flux<PermissionEntity> findEnabledByTenantId(String tenantId);
}
