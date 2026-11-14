package com.apex.scaffold.domain.repository;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.domain.model.generator.GenHistoryEntity;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 生成历史仓储接口
 *
 * <p>定义代码生成器生成历史记录的持久化操作契约，由基础设施层实现（MyBatis-Plus）。
 * 支持历史记录的查询、创建和分页查询。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>记录每次代码生成任务的执行结果</li>
 *   <li>按生成配置ID查询历史记录</li>
 *   <li>支持分页查询和按时间排序</li>
 *   <li>提供审计追踪和问题排查数据</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>查看某个配置的生成历史</li>
 *   <li>查看某个用户的生成操作记录</li>
 *   <li>统计生成成功率和失败原因</li>
 *   <li>追踪生成文件的来源</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see GenHistoryEntity
 */
public interface GenHistoryRepository {

    /**
     * 根据ID查询生成历史
     *
     * @param id 历史记录ID
     * @return 历史实体，不存在时返回 {@code Mono.empty()}
     */
    Mono<GenHistoryEntity> findById(Long id);

    /**
     * 根据配置ID查询生成历史
     *
     * <p>查询指定生成配置产生的所有历史记录。
     * 结果按创建时间倒序排列（最新的在前）。
     *
     * @param configId 生成配置ID
     * @return 历史实体列表
     */
    Mono<List<GenHistoryEntity>> findByConfigId(Long configId);

    /**
     * 查询全部生成历史
     *
     * <p>查询当前租户下的所有生成历史。
     * 结果按创建时间倒序排列。
     *
     * @return 历史实体列表
     */
    Mono<List<GenHistoryEntity>> findAll();

    /**
     * 分页查询指定配置的生成历史
     *
     * <p>在数据库侧完成过滤与分页，按创建时间倒序排列。
     * 结果集已由持久化拦截器限定为当前租户。
     *
     * @param configId 生成配置ID
     * @param pageNum 页码，从 1 开始
     * @param pageSize 每页大小
     * @return 分页结果
     */
    Mono<PageResponse<GenHistoryEntity>> searchByConfigId(Long configId, int pageNum, int pageSize);

    /**
     * 新增生成历史记录
     *
     * <p>代码生成任务开始时创建记录，初始状态可能为待处理。
     * 任务完成后通过 {@link #updateById} 更新状态和结果。
     *
     * @param entity 历史实体
     * @return 已回写主键的历史实体
     */
    Mono<GenHistoryEntity> insert(GenHistoryEntity entity);

    /**
     * 更新生成历史记录
     *
     * <p>任务完成后更新状态（SUCCESS/FAILED）、生成文件列表或错误信息。
     *
     * @param entity 历史实体
     * @return 影响行数
     */
    Mono<Integer> updateById(GenHistoryEntity entity);

    /**
     * 删除生成历史记录
     *
     * <p>逻辑删除，基于 {@code deleted} 字段。
     * 历史记录通常用于审计，删除操作应谨慎使用。
     *
     * @param id 历史记录ID
     * @return 影响行数
     */
    Mono<Integer> deleteById(Long id);
}
