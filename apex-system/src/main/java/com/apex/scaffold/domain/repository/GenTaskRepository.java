package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.generator.GenTaskEntity;
import java.time.LocalDateTime;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 代码生成器异步任务仓储接口
 *
 * <p>定义任务提交、认领、进度推进与终态写入契约，由基础设施层用 MyBatis-Plus 实现。
 * 全部方法显式携带 {@code tenantId}：任务表以注解 SQL 访问，不经过持久化拦截器自动注入租户。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see GenTaskEntity
 */
public interface GenTaskRepository {

    /**
     * 新增任务。
     *
     * @param entity 任务实体
     * @return 已回写主键的任务实体
     */
    Mono<GenTaskEntity> insert(GenTaskEntity entity);

    /**
     * 按 ID 与租户查询任务。
     *
     * @param id 任务 ID
     * @param tenantId 租户 ID
     * @return 任务实体，不存在或不属于该租户时返回 {@link Mono#empty()}
     */
    Mono<GenTaskEntity> findById(Long id, String tenantId);

    /**
     * 查找下一个可认领任务。
     *
     * <p>候选为 {@code PENDING}，或 {@code RUNNING} 且认领时间早于租约截止时间的任务；
     * 按创建时间升序保证先提交先执行。
     *
     * @param tenantId 租户 ID
     * @param maxAttempts 最大执行次数，超过的任务不再认领
     * @param leaseDeadline 租约截止时间，早于该时间认领的 RUNNING 任务视为过期
     * @return 候选任务，不存在时返回 {@link Mono#empty()}
     */
    Mono<GenTaskEntity> findNextCandidate(String tenantId, int maxAttempts, LocalDateTime leaseDeadline);

    /**
     * 列出当前存在可认领任务的租户。
     *
     * @param maxAttempts 最大执行次数
     * @param leaseDeadline 租约截止时间
     * @param limit 单轮最多返回租户数
     * @return 候选租户标识
     */
    Mono<List<String>> findCandidateTenants(int maxAttempts, LocalDateTime leaseDeadline, int limit);

    /**
     * 列出当前存在过期终态任务的租户。
     *
     * <p>保留期清理无法复用 {@link #findCandidateTenants}：后者只返回有可认领任务的租户，
     * 已结束的任务不在其中。
     *
     * @param finishedBefore 终态时间截止点
     * @param limit 单轮最多返回租户数
     * @return 租户标识
     */
    Mono<List<String>> findFinishedTenants(LocalDateTime finishedBefore, int limit);

    /**
     * 认领任务。
     *
     * <p>仅当任务仍处于可认领状态时更新成功；并发下只有一个实例能认领同一任务。
     *
     * @param id 任务 ID
     * @param tenantId 租户 ID
     * @param lockedBy 认领实例标识
     * @param lockedAt 认领时间
     * @param leaseDeadline 租约截止时间，用于乐观判断任务是否仍可认领
     * @return 认领成功返回 {@code true}
     */
    Mono<Boolean> claim(Long id, String tenantId, String lockedBy, LocalDateTime lockedAt, LocalDateTime leaseDeadline);

    /**
     * 刷新心跳，延长租约。
     *
     * @param id 任务 ID
     * @param tenantId 租户 ID
     * @param lockedBy 认领实例标识
     * @param lockedAt 新的认领时间
     * @return 更新成功返回 {@code true}
     */
    Mono<Boolean> renewLease(Long id, String tenantId, String lockedBy, LocalDateTime lockedAt);

    /**
     * 推进进度计数。
     *
     * @param id 任务 ID
     * @param tenantId 租户 ID
     * @param totalCount 总数
     * @param processedCount 已处理数
     * @param failedCount 失败数
     * @param errorMessage 最近一次失败原因，可为 {@code null}
     * @return 更新成功返回 {@code true}；任务已进入终态时返回 {@code false}
     */
    Mono<Boolean> updateProgress(
            Long id, String tenantId, int totalCount, int processedCount, int failedCount, String errorMessage);

    /**
     * 标记任务完成。
     *
     * @param id 任务 ID
     * @param tenantId 租户 ID
     * @param historyId 生成历史 ID
     * @param totalCount 总数
     * @param processedCount 已处理数
     * @param failedCount 失败数
     * @param completedAt 完成时间
     * @return 更新成功返回 {@code true}；任务已被取消或已完成时返回 {@code false}
     */
    Mono<Boolean> markCompleted(
            Long id,
            String tenantId,
            Long historyId,
            int totalCount,
            int processedCount,
            int failedCount,
            LocalDateTime completedAt);

    /**
     * 标记任务失败。
     *
     * @param id 任务 ID
     * @param tenantId 租户 ID
     * @param errorMessage 失败原因（调用方负责截断）
     * @param completedAt 终态时间
     * @return 更新成功返回 {@code true}；任务已进入终态时返回 {@code false}
     */
    Mono<Boolean> markFailed(Long id, String tenantId, String errorMessage, LocalDateTime completedAt);

    /**
     * 请求取消任务。
     *
     * <p>{@code PENDING} 直接置为 {@code CANCELED}；{@code RUNNING} 置为 {@code CANCELED}，
     * 由执行方在下一次进度推进时感知并停止。
     *
     * @param id 任务 ID
     * @param tenantId 租户 ID
     * @param canceledAt 取消时间
     * @return 更新成功返回 {@code true}；任务已进入终态时返回 {@code false}
     */
    Mono<Boolean> requestCancel(Long id, String tenantId, LocalDateTime canceledAt);

    /**
     * 物理清理指定时间之前结束的任务。
     *
     * @param tenantId 租户 ID
     * @param finishedBefore 终态时间截止点
     * @param limit 单批上限
     * @return 实际删除行数
     */
    Mono<Integer> purgeFinishedBefore(String tenantId, LocalDateTime finishedBefore, int limit);
}
