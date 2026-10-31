package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.persistence.util.ConditionUtil;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.generator.GenTaskEntity;
import com.apex.scaffold.domain.repository.GenTaskRepository;
import com.apex.scaffold.infrastructure.persistence.converter.GenTaskPoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.GenTaskMapper;
import com.apex.scaffold.infrastructure.persistence.po.GenTaskPO;
import java.time.LocalDateTime;
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
     * 竞争认领任务。
     *
     * @param id            任务 ID
     * @param tenantId      租户 ID
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
     * 续租并校验执行权。
     *
     * @param id       任务 ID
     * @param tenantId 租户 ID
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
     * 回写处理进度并顺延续租。
     *
     * @param id             任务 ID
     * @param tenantId       租户 ID
     * @param lockedBy       认领实例标识
     * @param lockedAt       本次心跳时间
     * @param totalCount     总处理单元数
     * @param processedCount 已处理单元数
     * @param failedCount    失败单元数
     * @param errorMessage   最近一次错误摘要
     * @return 是否更新成功
     */
    @Override
    public Mono<Boolean> updateProgress(
            Long id,
            String tenantId,
            String lockedBy,
            LocalDateTime lockedAt,
            int totalCount,
            int processedCount,
            int failedCount,
            String errorMessage) {
        return Mono.fromCallable(() -> taskMapper.updateProgress(
                                id, tenantId, lockedBy, lockedAt, totalCount, processedCount, failedCount, errorMessage)
                        > 0)
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 标记任务完成。
     *
     * @param id             任务 ID
     * @param tenantId       租户 ID
     * @param lockedBy       认领实例标识
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
            String lockedBy,
            Long historyId,
            int totalCount,
            int processedCount,
            int failedCount,
            LocalDateTime completedAt) {
        return Mono.fromCallable(() -> taskMapper.markCompleted(
                                id, tenantId, lockedBy, historyId, totalCount, processedCount, failedCount, completedAt)
                        > 0)
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 标记任务失败。
     *
     * @param id           任务 ID
     * @param tenantId     租户 ID
     * @param lockedBy     认领实例标识
     * @param errorMessage 失败原因
     * @param completedAt  终态时间
     * @return 是否更新成功
     */
    @Override
    public Mono<Boolean> markFailed(
            Long id, String tenantId, String lockedBy, String errorMessage, LocalDateTime completedAt) {
        return Mono.fromCallable(() -> taskMapper.markFailed(id, tenantId, lockedBy, errorMessage, completedAt) > 0)
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 请求取消任务。
     *
     * @param id         任务 ID
     * @param tenantId   租户 ID
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
     * <p>两步实现：先按租户与完成时间查出过期终态任务主键（可移植的 SELECT ... ORDER BY ... LIMIT），
     * 再按主键物理删除。查出的批次为空时直接返回 0，不发删除语句。
     *
     * @param tenantId       租户 ID
     * @param finishedBefore 完成时间阈值
     * @param limit          单次清理条数
     * @return 实际删除条数
     */
    @Override
    public Mono<Integer> purgeFinishedBefore(String tenantId, LocalDateTime finishedBefore, int limit) {
        return Mono.fromCallable(() -> taskMapper.findFinishedIdsBefore(tenantId, finishedBefore, limit))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .filter(ids -> !ids.isEmpty())
                .flatMap(ids -> Mono.fromCallable(() -> taskMapper.deleteFinishedByIds(ids))
                        .subscribeOn(BlockingIoSchedulers.jdbc()))
                .defaultIfEmpty(0);
    }
}
