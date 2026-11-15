package com.apex.scaffold.auth.domain.repository;

import com.apex.scaffold.auth.domain.model.entity.AuthConfigEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 认证配置仓储接口
 *
 * <p>定义认证配置实体的持久化操作，遵循DDD仓储模式。
 *
 * @author Apex Scaffold Team
 * @since 1.2.0
 */
public interface AuthConfigRepository {

    /**
     * 保存认证配置
     *
     * @param config 认证配置实体
     * @return 保存后的认证配置实体
     */
    Mono<AuthConfigEntity> save(AuthConfigEntity config);

    /**
     * 更新认证配置
     *
     * @param config 认证配置实体
     * @return 更新后的认证配置实体
     */
    Mono<AuthConfigEntity> update(AuthConfigEntity config);

    /**
     * 开放应用单行CAS更新；版本冲突不得覆盖并发管理操作。
     *
     * @param config 待更新的配置实体
     * @param expectedVersion 期望的当前版本号
     * @return 更新后的配置实体
     */
    Mono<AuthConfigEntity> updateOpenApp(AuthConfigEntity config, int expectedVersion);

    /**
     * 在数据库中分页读取OAuth客户端，页码从0开始。
     *
     * @param tenantId 租户ID
     * @param page 页码，从0开始
     * @param size 每页数量
     * @return OAuth2客户端配置流
     */
    Flux<AuthConfigEntity> findOAuth2Clients(String tenantId, int page, int size);

    /**
     * 根据ID删除认证配置
     *
     * @param id 配置ID
     * @param tenantId 租户ID
     * @return 删除完成信号（空完成）
     */
    Mono<Void> deleteById(Long id, String tenantId);

    /**
     * 根据ID查找认证配置
     *
     * @param id 配置ID
     * @param tenantId 租户ID
     * @return 认证配置实体，如果不存在返回空
     */
    Mono<AuthConfigEntity> findById(Long id, String tenantId);

    /**
     * 根据配置类型和键查找配置
     *
     * @param configType 配置类型
     * @param configKey 配置键
     * @param tenantId 租户ID
     * @return 认证配置实体，如果不存在返回空
     */
    Mono<AuthConfigEntity> findByTypeAndKey(String configType, String configKey, String tenantId);

    /**
     * 根据配置类型查询所有配置
     *
     * @param configType 配置类型
     * @param tenantId 租户ID
     * @return 认证配置实体流
     */
    Flux<AuthConfigEntity> findByType(String configType, String tenantId);

    /**
     * 查询所有启用的配置
     *
     * @param tenantId 租户ID
     * @return 启用的认证配置实体流
     */
    Flux<AuthConfigEntity> findAllEnabled(String tenantId);

    /**
     * 查询指定租户的所有配置
     *
     * @param tenantId 租户ID
     * @return 认证配置实体流
     */
    Flux<AuthConfigEntity> findAll(String tenantId);

    /**
     * 查询系统默认配置
     *
     * @return 系统默认配置实体流
     */
    Flux<AuthConfigEntity> findDefaultConfigs();

    /**
     * 根据配置类型查询启用的配置
     *
     * @param configType 配置类型
     * @param tenantId 租户ID
     * @return 启用的认证配置实体流
     */
    Flux<AuthConfigEntity> findEnabledByType(String configType, String tenantId);

    /**
     * 检查配置键是否存在
     *
     * @param configType 配置类型
     * @param configKey 配置键
     * @param tenantId 租户ID
     * @return 如果存在返回true
     */
    Mono<Boolean> existsByTypeAndKey(String configType, String configKey, String tenantId);

    /**
     * 批量保存配置
     *
     * @param configs 配置列表
     * @return 保存的配置数量
     */
    Mono<Long> batchSave(Flux<AuthConfigEntity> configs);

    /**
     * 统计指定类型的配置数量
     *
     * @param configType 配置类型
     * @param tenantId 租户ID
     * @return 配置数量
     */
    Mono<Long> countByType(String configType, String tenantId);
}
