package com.apex.scaffold.auth.application.service;

import com.apex.scaffold.auth.domain.model.entity.AuthConfigEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 认证配置服务接口
 *
 * <p>提供认证授权系统的配置管理功能，包括密码策略、会话策略、OAuth2配置等。
 * 支持配置的CRUD操作、配置热更新和多租户隔离。
 *
 * @author Apex Scaffold Team
 * @since 1.2.0
 */
public interface AuthConfigService {

    /**
     * 创建认证配置
     *
     * @param config 认证配置实体
     * @return 创建后的配置实体
     */
    Mono<AuthConfigEntity> createConfig(AuthConfigEntity config);

    /**
     * 更新认证配置
     *
     * @param id 配置ID
     * @param config 更新的配置数据
     * @param tenantId 租户ID
     * @return 更新后的配置实体
     */
    Mono<AuthConfigEntity> updateConfig(Long id, AuthConfigEntity config, String tenantId);

    /**
     * 删除认证配置
     *
     * @param id 配置ID
     * @param tenantId 租户ID
     * @return 删除完成信号（空完成）
     */
    Mono<Void> deleteConfig(Long id, String tenantId);

    /**
     * 根据ID查询配置
     *
     * @param id 配置ID
     * @param tenantId 租户ID
     * @return 配置实体
     */
    Mono<AuthConfigEntity> getConfigById(Long id, String tenantId);

    /**
     * 根据类型和键查询配置
     *
     * @param configType 配置类型
     * @param configKey 配置键
     * @param tenantId 租户ID
     * @return 配置实体
     */
    Mono<AuthConfigEntity> getConfigByTypeAndKey(String configType, String configKey, String tenantId);

    /**
     * 查询指定类型的所有配置
     *
     * @param configType 配置类型
     * @param tenantId 租户ID
     * @return 配置实体流
     */
    Flux<AuthConfigEntity> listConfigsByType(String configType, String tenantId);

    /**
     * 查询所有配置
     *
     * @param tenantId 租户ID
     * @return 配置实体流
     */
    Flux<AuthConfigEntity> listAllConfigs(String tenantId);

    /**
     * 查询所有启用的配置
     *
     * @param tenantId 租户ID
     * @return 启用的配置实体流
     */
    Flux<AuthConfigEntity> listEnabledConfigs(String tenantId);

    /**
     * 启用配置
     *
     * @param id 配置ID
     * @param tenantId 租户ID
     * @return 更新后的配置实体
     */
    Mono<AuthConfigEntity> enableConfig(Long id, String tenantId);

    /**
     * 禁用配置
     *
     * @param id 配置ID
     * @param tenantId 租户ID
     * @return 更新后的配置实体
     */
    Mono<AuthConfigEntity> disableConfig(Long id, String tenantId);

    /**
     * 获取密码策略配置
     *
     * @param tenantId 租户ID
     * @return 密码策略配置
     */
    Mono<AuthConfigEntity> getPasswordPolicy(String tenantId);

    /**
     * 获取会话策略配置
     *
     * @param tenantId 租户ID
     * @return 会话策略配置
     */
    Mono<AuthConfigEntity> getSessionPolicy(String tenantId);

    /**
     * 获取安全策略配置
     *
     * @param tenantId 租户ID
     * @return 安全策略配置
     */
    Mono<AuthConfigEntity> getSecurityPolicy(String tenantId);

    /**
     * 获取OAuth2客户端配置
     *
     * @param clientId 客户端ID
     * @param tenantId 租户ID
     * @return OAuth2客户端配置
     */
    Mono<AuthConfigEntity> getOauth2ClientConfig(String clientId, String tenantId);

    /**
     * 批量更新配置
     *
     * @param configs 配置实体流
     * @param tenantId 租户ID
     * @return 更新的配置数量
     */
    Mono<Long> batchUpdateConfigs(Flux<AuthConfigEntity> configs, String tenantId);

    /**
     * 重置为系统默认配置
     *
     * @param configType 配置类型
     * @param tenantId 租户ID
     * @return 重置后的配置实体
     */
    Mono<AuthConfigEntity> resetToDefault(String configType, String tenantId);
}
