package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.entity.SysSsoProviderEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * SSO 提供商仓储接口
 *
 * <p>定义单点登录（SSO）提供商配置的持久化操作契约，管理 SAML、OAuth2、LDAP 等 SSO 提供商的配置信息。
 * 支持提供商的增删改查、按名称和租户查询、多租户隔离等功能。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>SSO 提供商配置的基础 CRUD 操作</li>
 *   <li>按名称和租户查询提供商（租户内提供商名称唯一）</li>
 *   <li>按租户查询所有可用提供商（登录页展示）</li>
 *   <li>提供商配置的启用/禁用管理</li>
 *   <li>多租户环境下的提供商隔离</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>登录页：查询租户下所有可用的 SSO 提供商</li>
 *   <li>SSO 配置：通过名称查找特定提供商配置</li>
 *   <li>提供商管理：租户管理员管理 SSO 提供商</li>
 *   <li>回调处理：SSO 回调时根据名称定位提供商配置</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>SAML 2.0：企业级单点登录协议</li>
 *   <li>OAuth 2.0 / OIDC：社交登录和企业 IdP</li>
 *   <li>LDAP：企业目录服务集成</li>
 *   <li>CAS：中央认证服务</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>响应式编程：所有方法返回 Mono/Flux</li>
 *   <li>租户隔离：name + tenantId 唯一标识提供商</li>
 *   <li>配置加密：敏感信息（密钥、证书）应加密存储</li>
 *   <li>动态加载：支持运行时动态添加/修改提供商</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see SysSsoProviderEntity
 */
public interface SysSsoProviderRepository {

    /**
     * 根据ID查询SSO提供商
     *
     * @param id 提供商ID
     * @return 提供商实体
     */
    Mono<SysSsoProviderEntity> findById(Long id);

    /**
     * 根据名称和租户ID查询SSO提供商
     *
     * @param name 提供商名称
     * @param tenantId 租户ID
     * @return 提供商实体
     */
    Mono<SysSsoProviderEntity> findByNameAndTenantId(String name, String tenantId);

    /**
     * 根据租户ID查询SSO提供商列表
     *
     * @param tenantId 租户ID
     * @return 提供商列表
     */
    Flux<SysSsoProviderEntity> findByTenantId(String tenantId);

    /**
     * 插入SSO提供商
     *
     * @param entity 提供商实体
     * @return 影响行数
     */
    Mono<Integer> insert(SysSsoProviderEntity entity);

    /**
     * 根据ID更新SSO提供商
     *
     * @param entity 提供商实体
     * @return 影响行数
     */
    Mono<Integer> updateById(SysSsoProviderEntity entity);

    /**
     * 根据ID删除SSO提供商
     *
     * @param id 提供商ID
     * @return 影响行数
     */
    Mono<Integer> deleteById(Long id);
}
