package com.apex.scaffold.domain.repository;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.domain.model.monitor.HealthSnapshotEntity;
import java.time.LocalDateTime;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 运维体检历史快照仓储端口。
 *
 * <p>定义体检快照的写入、分页查询、趋势取样与区间统计契约，由基础设施层基于
 * MyBatis-Plus 实现。所有查询的租户范围由持久化拦截器追加，调用方不得自行拼接租户条件。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see HealthSnapshotEntity
 */
public interface HealthSnapshotRepository {

    /**
     * 新增体检快照。
     *
     * @param entity 快照实体
     * @return 已回写主键的快照实体
     */
    Mono<HealthSnapshotEntity> insert(HealthSnapshotEntity entity);

    /**
     * 分页查询体检快照。
     *
     * <p>过滤与分页下推到数据库，按生成时间倒序排列。
     *
     * @param from 生成时间下界（含），可为 {@code null}
     * @param to 生成时间上界（含），可为 {@code null}
     * @param overallStatus 总体结论过滤，可为 {@code null} 表示不过滤
     * @param pageNum 页码，从 1 开始
     * @param pageSize 每页大小
     * @return 分页结果
     */
    Mono<PageResponse<HealthSnapshotEntity>> search(
            LocalDateTime from, LocalDateTime to, String overallStatus, int pageNum, int pageSize);

    /**
     * 按时间区间取样用于趋势分析。
     *
     * <p>返回按生成时间升序排列的快照，调用方通过 {@code limit} 控制数据量，
     * 避免长区间一次拉取过多记录。
     *
     * @param from 生成时间下界（含）
     * @param to 生成时间上界（含）
     * @param limit 最大取样条数
     * @return 快照列表，按生成时间升序
     */
    Mono<List<HealthSnapshotEntity>> findForTrend(LocalDateTime from, LocalDateTime to, int limit);

    /**
     * 统计时间区间内的快照总数。
     *
     * <p>用于导出前的规模校验，避免一次导出过多行导致内存与响应体失控。
     *
     * @param from 生成时间下界（含），可为 {@code null}
     * @param to 生成时间上界（含），可为 {@code null}
     * @return 满足条件的记录数
     */
    Mono<Long> countByRange(LocalDateTime from, LocalDateTime to);
}
