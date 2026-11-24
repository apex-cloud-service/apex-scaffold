package com.apex.scaffold.integration.connector.domain.repository;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.integration.connector.domain.ConnectorDefinition;
import reactor.core.publisher.Mono;

/**
 * 连接器配置仓储端口（方案 M5 / 批次 E2）。
 *
 * <p>全部查询以租户为作用域（显式 {@code tenant_id} 条件，叠加行级拦截器）；
 * 写操作依赖乐观锁（{@code version}）与逻辑删除（{@code deleted}）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public interface ConnectorConfigRepository {

    /**
     * 按租户内配置标识查询启用中的连接器配置。
     *
     * @param tenantId  租户编码
     * @param configKey 配置标识
     * @return 配置；不存在或停用时为空
     */
    Mono<ConnectorDefinition> findActive(String tenantId, String configKey);

    /**
     * 按 ID 查询（租户限定）。
     *
     * @param tenantId 租户编码
     * @param id       主键
     * @return 配置；跨租户访问视为不存在
     */
    Mono<ConnectorDefinition> findById(String tenantId, Long id);

    /**
     * 分页查询租户连接器配置。
     *
     * @param tenantId      租户编码
     * @param connectorType 连接器类型（可空）
     * @param pageNum       页码（1 起）
     * @param pageSize      页大小
     * @return 分页结果
     */
    Mono<PageResponse<ConnectorDefinition>> page(String tenantId, String connectorType, int pageNum, int pageSize);

    /**
     * 新增配置（租户内 configKey 唯一）。
     *
     * @param definition 配置实体（含租户）
     * @return 落库后的配置
     */
    Mono<ConnectorDefinition> insert(ConnectorDefinition definition);

    /**
     * 更新配置（乐观锁；版本冲突返回错误）。
     *
     * @param definition 配置实体（含版本）
     * @return 更新后的配置
     */
    Mono<ConnectorDefinition> update(ConnectorDefinition definition);

    /**
     * 逻辑删除配置（租户限定）。
     *
     * @param tenantId 租户编码
     * @param id       主键
     * @throws com.apex.scaffold.common.exception.BusinessException 删除 0 行时以 {@code NOT_FOUND} 终止
     * @return 操作完成信号，失败时传播错误
     */
    Mono<Void> delete(String tenantId, Long id);
}
