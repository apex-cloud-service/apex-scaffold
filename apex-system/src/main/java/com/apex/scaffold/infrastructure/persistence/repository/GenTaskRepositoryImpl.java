package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.persistence.util.ConditionUtil;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.generator.GenTaskEntity;
import com.apex.scaffold.domain.repository.GenTaskRepository;
import com.apex.scaffold.infrastructure.persistence.converter.GenTaskPoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.GenTaskMapper;
import com.apex.scaffold.infrastructure.persistence.po.GenTaskPO;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * {@link GenTaskRepository} 的 MyBatis-Plus 实现（基础设施层）。
 *
 * <p>全部方法显式携带 {@code tenantId}：任务表的注解 SQL 不经过持久化拦截器，
 * 租户条件必须由调用方传入并写入 SQL。所有 JDBC 调用在 jdbc 调度器上执行。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.5.0
 * @see GenTaskRepository
 */
@Repository
@RequiredArgsConstructor
public class GenTaskRepositoryImpl implements GenTaskRepository {

    private final GenTaskMapper taskMapper;

    private final GenTaskPoConverter taskPoConverter;

    /**
     * 新增生成任务。
     *
     * <p>插入前由 MyBatis-Plus 分配雪花主键，写回实体以便立即返回任务 ID。
     *
     * @param entity 任务实体
     * @return 已回写主键的任务实体
     */
    @Override
    public Mono<GenTaskEntity> insert(GenTaskEntity entity) {
        return Mono.fromCallable(() -> {
                    GenTaskPO po = taskPoConverter.toPo(entity);
                    taskMapper.insert(po);
                    entity.setId(po.getId());
                    return entity;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 按 ID 与租户查询任务。
     *
     * @param id       任务 ID
     * @param tenantId 租户标识
     * @return 任务实体，不存在时返回 {@link Mono#empty()}
     */
    @Override
    public Mono<GenTaskEntity> findById(Long id, String tenantId) {
        return Mono.fromCallable(() -> taskMapper.selectOne(ConditionUtil.lambda(GenTaskPO.class)
                        .eq(GenTaskPO::getId, id)
                        .eq(GenTaskPO::getTenantId, tenantId)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(taskPoConverter.toEntity(po)));
    }

    /**
     * 查找一条可认领任务。
     *
     * @param tenantId      租户标识
     * @param maxAttempts   最大尝试次数
     * @param leaseDeadline 租约过期阈值
     * @return 候选任务，无候选时返回 {@link Mono#empty()}
     */
    @Override
    public Mono<GenTaskEntity> findNextCandidate(String tenantId, int maxAttempts, LocalDateTime leaseDeadline) {
        return Mono.fromCallable(() -> taskMapper.findNextCandidate(tenantId, maxAttempts, leaseDeadline))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(taskPoConverter.toEntity(po)));
    }

    /**
     * 列出当前存在可认领任务的租户。
     *
     * @param maxAttempts   最大尝试次数
     * @param leaseDeadline 租约过期阈值
     * @param limit         单轮最多返回租户数
     * @return 候选租户标识
     */
    @Override
    public Mono<List<String>> findCandidateTenants(int maxAttempts, LocalDateTime leaseDeadline, int limit) {
        return Mono.fromCallable(() -> taskMapper.findCandidateTenants(maxAttempts, leaseDeadline, limit))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 列出当前存在过期终态任务的租户。
     *
     * @param finishedBefore 终态时间截止点
     * @param limit          单轮最多返回租户数
     * @return 租户标识
     */
    @Override
    public Mono<List<String>> findFinishedTenants(LocalDateTime finishedBefore, int limit) {
        return Mono.fromCallable(() -> taskMapper.findFinishedTenants(finishedBefore, limit))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 竞争认领任务。
     *
     * @param id            任务 ID
     * @param tenantId      租户标识
     * @param lockedBy      认领实例标识
     * @param lockedAt      认领时间
     * @param leaseDeadline 租约过期阈值
     * @return 是否认领成功
     */
    @Override
    public Mono<Boolean> claim(
            Long id, String tenantId, String lockedBy, LocalDateTime lockedAt, LocalDateTime leaseDeadline) {
        return Mono.fromCallable(() -> taskMapper.claim(id, tenantId, lockedBy, lockedAt, leaseDeadline) > 0)
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 续租。
     *
     * @param id       任务 ID
     * @param tenantId 租户标识
     * @param lockedBy 认领实例标识
     * @param lockedAt 新的认领时间
     * @return 是否续租成功
     */
    @Override
    public Mono<Boolean> renewLease(Long id, String tenantId, String lockedBy, LocalDateTime lockedAt) {
        return Mono.fromCallable(() -> taskMapper.renewLease(id, tenantId, lockedBy, lockedAt) > 0)
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 回写处理进度。
     *
     * @param id             任务 ID
     * @param tenantId       租户标识
     * @param totalCount     总处理单元数
     * @param processedCount 已处理单元数
     * @param failedCount    失败单元数
     * @param errorMessage   最近一次错误摘要
     * @return 是否更新成功
     */
    @Override
    public Mono<Boolean> updateProgress(
            Long id, String tenantId, int totalCount, int processedCount, int failedCount, String errorMessage) {
        return Mono.fromCallable(() ->
                        taskMapper.updateProgress(id, tenantId, totalCount, processedCount, failedCount, errorMessage)
                                > 0)
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 标记任务完成。
     *
     * @param id             任务 ID
     * @param tenantId       租户标识
     * @param historyId      生成历史 ID
     * @param totalCount     总处理单元数
     * @param processedCount 已处理单元数
     * @param failedCount    失败单元数
     * @param completedAt    完成时间
     * @return 是否更新成功
     */
    @Override
    public Mono<Boolean> markCompleted(
            Long id,
            String tenantId,
            Long historyId,
            int totalCount,
            int processedCount,
            int failedCount,
            LocalDateTime completedAt) {
        return Mono.fromCallable(() -> taskMapper.markCompleted(
                                id, tenantId, historyId, totalCount, processedCount, failedCount, completedAt)
                        > 0)
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 标记任务失败。
     *
     * @param id           任务 ID
     * @param tenantId     租户标识
     * @param errorMessage 失败原因
     * @param completedAt  完成时间
     * @return 是否更新成功
     */
    @Override
    public Mono<Boolean> markFailed(Long id, String tenantId, String errorMessage, LocalDateTime completedAt) {
        return Mono.fromCallable(() -> taskMapper.markFailed(id, tenantId, errorMessage, completedAt) > 0)
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 请求取消任务。
     *
     * @param id         任务 ID
     * @param tenantId   租户标识
     * @param canceledAt 取消时间
     * @return 是否取消成功（false 表示任务已是终态）
     */
    @Override
    public Mono<Boolean> requestCancel(Long id, String tenantId, LocalDateTime canceledAt) {
        return Mono.fromCallable(() -> taskMapper.requestCancel(id, tenantId, canceledAt) > 0)
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 清理已过保留期的终态任务。
     *
     * @param tenantId       租户标识
     * @param finishedBefore 完成时间阈值
     * @param limit          单次清理条数
     * @return 实际删除条数
     */
    @Override
    public Mono<Integer> purgeFinishedBefore(String tenantId, LocalDateTime finishedBefore, int limit) {
        return Mono.fromCallable(() -> taskMapper.purgeFinishedBefore(tenantId, finishedBefore, limit))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
