package com.apex.scaffold.domain.repository;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.domain.model.entity.OffSyncHistoryEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 办公同步历史仓储接口
 *
 * <p>定义办公平台组织同步历史的持久化操作契约，记录从外部平台（飞书、钉钉、企业微信）
 * 同步组织架构（部门、员工）的历史记录。提供同步记录的增删改查、按来源查询、最新记录查询等功能。
 *
 * <h2>核心职责</h2>
 * <ul>
 *   <li>同步历史的基础 CRUD 操作</li>
 *   <li>按同步来源查询历史记录</li>
 *   <li>查询某来源的最新同步记录</li>
 *   <li>按同步状态筛选记录（成功、失败、进行中）</li>
 *   <li>分页查询和条件过滤</li>
 *   <li>同步任务的审计追踪</li>
 * </ul>
 *
 * <h2>查询场景</h2>
 * <ul>
 *   <li>同步监控：查看各平台的同步执行情况</li>
 *   <li>故障排查：查询失败的同步记录和错误信息</li>
 *   <li>增量同步：获取上次同步时间点进行增量更新</li>
 *   <li>同步统计：统计同步成功率和数据量</li>
 * </ul>
 *
 * <h2>技术特性</h2>
 * <ul>
 *   <li>响应式编程：所有方法返回 Mono/Flux</li>
 *   <li>来源标识：syncSource 区分不同的外部平台</li>
 *   <li>状态跟踪：记录同步的完整生命周期状态</li>
 *   <li>时间戳记录：用于增量同步和时间范围查询</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 * @see OffSyncHistoryEntity
 */
public interface OffSyncHistoryRepository {

    /**
     * 根据同步历史ID查询记录
     *
     * @param id 同步历史ID
     * @return 同步历史实体的Mono包装，如果不存在则返回空Mono
     */
    Mono<OffSyncHistoryEntity> findById(Long id);

    /**
     * 根据同步来源查询所有同步历史记录
     *
     * @param syncSource 同步来源（如feishu、dingtalk、wechat）
     * @return 同步历史记录列表的Flux流
     */
    Flux<OffSyncHistoryEntity> findBySyncSource(String syncSource);

    /**
     * 查询指定来源的最新一次同步记录
     *
     * @param syncSource 同步来源（如feishu、dingtalk、wechat）
     * @return 最新同步历史记录的Mono包装，如果不存在则返回空Mono
     */
    Mono<OffSyncHistoryEntity> findLatestBySource(String syncSource);

    /**
     * 插入新同步历史记录
     *
     * @param entity 同步历史实体
     * @return 受影响的行数
     */
    Mono<Integer> insert(OffSyncHistoryEntity entity);

    /**
     * 更新同步历史记录
     *
     * @param entity 同步历史实体，必须包含ID
     * @return 受影响的行数
     */
    Mono<Integer> update(OffSyncHistoryEntity entity);

    /**
     * 分页查询同步历史记录
     *
     * @param pageNum 页码，从1开始
     * @param pageSize 每页大小
     * @param syncSource 同步来源，可为null表示查询所有来源
     * @param syncStatus 同步状态，可为null表示查询所有状态
     * @return 分页结果
     */
    Mono<PageResponse<OffSyncHistoryEntity>> findPage(
            Integer pageNum, Integer pageSize, String syncSource, String syncStatus);
}
