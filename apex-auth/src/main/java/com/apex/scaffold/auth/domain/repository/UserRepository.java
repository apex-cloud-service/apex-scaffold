package com.apex.scaffold.auth.domain.repository;

import com.apex.scaffold.auth.domain.model.entity.UserEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 用户仓储接口
 *
 * <p>定义用户实体的持久化操作，遵循DDD仓储模式。
 *
 * @author Apex Scaffold Team
 * @since 1.2.0
 */
public interface UserRepository {

    /**
     * 保存用户
     *
     * @param user 用户实体
     * @return 保存后的用户实体
     */
    Mono<UserEntity> save(UserEntity user);

    /**
     * 根据ID查找用户
     *
     * @param id 用户ID
     * @return 用户实体，如果不存在返回空
     */
    Mono<UserEntity> findById(Long id);

    /**
     * 根据用户名查找用户
     *
     * @param username 用户名
     * @param tenantId 租户ID
     * @return 用户实体，如果不存在返回空
     */
    Mono<UserEntity> findByUsername(String username, String tenantId);

    /**
     * 根据统一账户ID查找用户（迁移方案 Phase 2.3）.
     *
     * <p>供内部 API {@code GET /internal/v1/users/{accountId}/permissions} 使用：
     * 消费方（system/workflow/office service）持有的是主应用 {@code sys_user.id}
     * （即 JWT 中的 {@code accountId}），需据此反查认证中心用户以展开其权限编码。
     * 同一账户在不同租户下可能存在多条映射，故携带租户维度精确定位。
     *
     * @param accountId 统一账户ID（主应用 sys_user.id）
     * @param tenantId 租户ID（用于多租户隔离）
     * @return 用户实体，如果不存在返回空
     */
    Mono<UserEntity> findByAccountId(Long accountId, String tenantId);

    /**
     * 根据邮箱查找用户
     *
     * @param email 邮箱
     * @param tenantId 租户ID
     * @return 用户实体，如果不存在返回空
     */
    Mono<UserEntity> findByEmail(String email, String tenantId);

    /**
     * 根据手机号查找用户
     *
     * @param phone 手机号
     * @param tenantId 租户ID
     * @return 用户实体，如果不存在返回空
     */
    Mono<UserEntity> findByPhone(String phone, String tenantId);

    /**
     * 更新用户
     *
     * @param user 用户实体
     * @return 更新后的用户实体
     */
    Mono<UserEntity> update(UserEntity user);

    /**
     * 删除用户（逻辑删除）
     *
     * @param id 用户ID
     * @return 删除是否成功
     */
    Mono<Boolean> deleteById(Long id);

    /**
     * 根据租户ID查询所有用户
     *
     * @param tenantId 租户ID
     * @return 用户实体列表
     */
    Flux<UserEntity> findByTenantId(String tenantId);

    /**
     * 检查用户名是否存在
     *
     * @param username 用户名
     * @param tenantId 租户ID
     * @return 如果存在返回true
     */
    Mono<Boolean> existsByUsername(String username, String tenantId);

    /**
     * 检查邮箱是否存在
     *
     * @param email 邮箱
     * @param tenantId 租户ID
     * @return 如果存在返回true
     */
    Mono<Boolean> existsByEmail(String email, String tenantId);

    /**
     * 检查手机号是否存在
     *
     * @param phone 手机号
     * @param tenantId 租户ID
     * @return 如果存在返回true
     */
    Mono<Boolean> existsByPhone(String phone, String tenantId);
}
