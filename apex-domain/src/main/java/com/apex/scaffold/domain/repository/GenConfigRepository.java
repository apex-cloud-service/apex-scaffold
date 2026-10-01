package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.generator.GenConfigEntity;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 生成配置仓储接口
 *
 * <p>定义代码生成器生成配置的持久化操作契约，由基础设施层实现（MyBatis-Plus）。
 * 支持配置的CRUD操作和按数据源查询。
 *
 * <h2>核心职责</h2>
 * <ul>
 *   <li>生成配置的增删改查</li>
 *   <li>按数据源ID查询关联的配置列表</li>
 *   <li>支持配置的批量操作</li>
 * </ul>
 *
 * <h2>关联关系</h2>
 * <ul>
 *   <li>多对一：多个配置可引用同一个数据源</li>
 *   <li>一对多：一个配置可产生多次生成历史</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see GenConfigEntity
 */
public interface GenConfigRepository {

    /**
     * 根据ID查询生成配置
     *
     * @param id 配置ID
     * @return 配置实体，不存在时返回 {@code Mono.empty()}
     */
    Mono<GenConfigEntity> findById(Long id);

    /**
     * 根据数据源ID查询生成配置列表
     *
     * <p>查询关联到指定数据源的所有生成配置。
     * 用于数据源删除前的依赖检查和配置管理。
     *
     * @param datasourceId 数据源ID
     * @return 配置实体列表
     */
    Mono<List<GenConfigEntity>> findByDatasourceId(Long datasourceId);

    /**
     * 查询全部生成配置
     *
     * <p>查询当前租户下的所有生成配置。
     * 结果按创建时间倒序排列。
     *
     * @return 配置实体列表
     */
    Mono<List<GenConfigEntity>> findAll();

    /**
     * 新增生成配置
     *
     * <p>插入前应先校验：
     * <ul>
     *   <li>关联的数据源是否存在且属于同一租户</li>
     *   <li>输出路径是否合法（防止路径穿越）</li>
     *   <li>表名列表和模板ID列表的JSON格式是否正确</li>
     * </ul>
     *
     * @param entity 配置实体
     * @return 影响行数
     */
    Mono<Integer> insert(GenConfigEntity entity);

    /**
     * 更新生成配置
     *
     * <p>更新前应校验配置是否属于当前租户。
     *
     * @param entity 配置实体
     * @return 影响行数
     */
    Mono<Integer> updateById(GenConfigEntity entity);

    /**
     * 删除生成配置
     *
     * <p>逻辑删除，基于 {@code deleted} 字段。
     * 删除配置不影响已产生的生成历史记录。
     *
     * @param id 配置ID
     * @return 影响行数
     */
    Mono<Integer> deleteById(Long id);
}
