package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.entity.TenantConfigEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 租户级参数配置仓储端口（方案批次 B6）。
 *
 * <p>随租户行级隔离：查询/写入均要求可信租户上下文（拦截器注入或实现侧显式条件）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public interface TenantConfigRepository {

    /**
     * 按租户与配置键查询。
     *
     * @param tenantId  租户编码
     * @param configKey 配置键
     * @return 配置实体；不存在时为空
     */
    Mono<TenantConfigEntity> findByTenantAndKey(String tenantId, String configKey);

    /**
     * 查询租户全部配置。
     *
     * @param tenantId 租户编码
     * @return 配置列表
     */
    Flux<TenantConfigEntity> findAllByTenant(String tenantId);

    /**
     * 保存配置（id 为空插入，否则更新；同键冲突由唯一键约束兜底）。
     *
     * @param entity 配置实体
     * @return 已保存实体
     */
    Mono<TenantConfigEntity> save(TenantConfigEntity entity);

    /**
     * 删除配置（逻辑删除）。
     *
     * @param tenantId  租户编码
     * @param configKey 配置键
     * @return 是否删除了记录
     */
    Mono<Boolean> deleteByTenantAndKey(String tenantId, String configKey);
}
