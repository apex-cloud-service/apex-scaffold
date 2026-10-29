package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.generator.GenDatasourceEntity;
import java.util.List;
import java.util.Optional;
import reactor.core.publisher.Mono;

/**
 * 数据源配置仓储接口
 *
 * <p>定义代码生成器数据源配置的持久化操作契约，由基础设施层实现（MyBatis-Plus）。
 * 支持数据源的CRUD操作、连接测试和租户隔离查询。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>数据源配置的增删改查</li>
 *   <li>按租户隔离查询数据源列表</li>
 *   <li>校验数据源名称唯一性（租户级别）</li>
 *   <li>支持分页查询</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <p>所有查询和操作必须强制校验租户ID，确保：
 * <ul>
 *   <li>每个租户只能访问自己创建的数据源</li>
 *   <li>数据源名称在租户内唯一</li>
 *   <li>跨租户数据源隔离</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see GenDatasourceEntity
 */
public interface GenDatasourceRepository {

    /**
     * 根据ID查询数据源配置
     *
     * @param id 数据源ID
     * @return 数据源实体，不存在时返回 {@code Mono.empty()}
     */
    Mono<GenDatasourceEntity> findById(Long id);

    /**
     * 根据名称和租户ID查询数据源配置
     *
     * <p>用于校验数据源名称唯一性。数据源名称在同一租户内必须唯一。
     *
     * @param name 数据源名称
     * @param tenantId 租户ID
     * @return 数据源实体，不存在时返回 {@code Optional.empty()}
     */
    Mono<Optional<GenDatasourceEntity>> findByNameAndTenantId(String name, String tenantId);

    /**
     * 根据租户ID查询数据源列表
     *
     * <p>查询指定租户下的所有数据源配置。
     * 结果按创建时间倒序排列。
     *
     * @param tenantId 租户ID
     * @return 数据源实体列表
     */
    Mono<List<GenDatasourceEntity>> findByTenantId(String tenantId);

    /**
     * 新增数据源配置
     *
     * <p>插入前应先校验数据源名称在租户内是否重复，并测试连接有效性。
     * 密码字段需要在应用层加密后传入。
     *
     * @param entity 数据源实体
     * @return 影响行数
     */
    Mono<Integer> insert(GenDatasourceEntity entity);

    /**
     * 更新数据源配置
     *
     * <p>更新前应校验数据源是否属于当前租户。
     * 如果修改了密码，需要重新加密。
     *
     * @param entity 数据源实体
     * @return 影响行数
     */
    Mono<Integer> updateById(GenDatasourceEntity entity);

    /**
     * 删除数据源配置
     *
     * <p>逻辑删除，基于 {@code deleted} 字段。
     * 删除前应检查是否有生成配置正在引用此数据源。
     *
     * @param id 数据源ID
     * @return 影响行数
     */
    Mono<Integer> deleteById(Long id);
}
