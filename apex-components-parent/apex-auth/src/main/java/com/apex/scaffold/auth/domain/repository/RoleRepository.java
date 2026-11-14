package com.apex.scaffold.auth.domain.repository;

import com.apex.scaffold.auth.domain.model.entity.RoleEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 角色仓储接口
 *
 * <p>定义角色实体的持久化操作，遵循DDD仓储模式。
 *
 * @author Apex Scaffold Team
 * @since 1.2.0
 */
public interface RoleRepository {

    /**
     * 保存角色
     *
     * @param role 角色实体
     * @return 保存后的角色实体
     */
    Mono<RoleEntity> save(RoleEntity role);

    /**
     * 根据ID查找角色
     *
     * @param id 角色ID
     * @return 角色实体，如果不存在返回空
     */
    Mono<RoleEntity> findById(Long id);

    /**
     * 查找所有角色
     *
     * @return 所有角色实体流
     */
    Flux<RoleEntity> findAll();

    /**
     * 根据角色编码查找角色
     *
     * @param code 角色编码
     * @param tenantId 租户ID
     * @return 角色实体，如果不存在返回空
     */
    Mono<RoleEntity> findByCode(String code, String tenantId);

    /**
     * 根据ID集合批量查找角色
     *
     * @param ids 角色ID集合
     * @return 角色实体流
     */
    Flux<RoleEntity> findByIds(Iterable<Long> ids);

    /**
     * 根据租户ID查询所有角色
     *
     * @param tenantId 租户ID
     * @return 角色实体流
     */
    Flux<RoleEntity> findByTenantId(String tenantId);

    /**
     * 根据用户ID查找用户拥有的所有角色
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @return 角色实体流
     */
    Flux<RoleEntity> findByUserId(Long userId, String tenantId);

    /**
     * 更新角色
     *
     * @param role 角色实体
     * @return 更新后的角色实体
     */
    Mono<RoleEntity> update(RoleEntity role);

    /**
     * 删除角色（逻辑删除）
     *
     * @param id 角色ID
     * @return 删除是否成功
     */
    Mono<Boolean> deleteById(Long id);

    /**
     * 检查角色编码是否存在
     *
     * @param code 角色编码
     * @param tenantId 租户ID
     * @return 如果存在返回true
     */
    Mono<Boolean> existsByCode(String code, String tenantId);

    /**
     * 查询启用状态的角色
     *
     * @param tenantId 租户ID
     * @return 启用的角色实体流
     */
    Flux<RoleEntity> findEnabledByTenantId(String tenantId);

    /**
     * 为用户分配角色
     *
     * @param userId 用户ID
     * @param roleIds 角色ID集合
     * @param tenantId 租户ID
     * @return 分配是否成功
     */
    Mono<Boolean> assignRolesToUser(Long userId, Iterable<Long> roleIds, String tenantId);

    /**
     * 移除用户的角色
     *
     * @param userId 用户ID
     * @param roleIds 角色ID集合
     * @param tenantId 租户ID
     * @return 移除是否成功
     */
    Mono<Boolean> removeRolesFromUser(Long userId, Iterable<Long> roleIds, String tenantId);
}
