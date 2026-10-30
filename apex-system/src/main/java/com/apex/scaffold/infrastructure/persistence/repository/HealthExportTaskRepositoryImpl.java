package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.monitor.HealthExportTaskEntity;
import com.apex.scaffold.domain.repository.HealthExportTaskRepository;
import com.apex.scaffold.infrastructure.persistence.converter.HealthExportTaskPoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.HealthExportTaskMapper;
import com.apex.scaffold.infrastructure.persistence.po.HealthExportTaskPO;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * {@link HealthExportTaskRepository} 的 MyBatis-Plus 实现（基础设施层）。
 *
 * <p>认领与回写走注解 SQL 条件更新，显式携带 {@code tenant_id}；
 * 所有 JDBC 阻塞操作调度到 {@link BlockingIoSchedulers#jdbc()}。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.4.0
 * @see HealthExportTaskRepository
 */
@Repository
@RequiredArgsConstructor
public class HealthExportTaskRepositoryImpl implements HealthExportTaskRepository {

    private final HealthExportTaskMapper taskMapper;

    private final HealthExportTaskPoConverter taskPoConverter;

    @Override
    public Mono<HealthExportTaskEntity> insert(HealthExportTaskEntity entity) {
        return Mono.fromCallable(() -> {
                    HealthExportTaskPO po = taskPoConverter.toPo(entity);
                    taskMapper.insert(po);
                    entity.setId(po.getId());
                    return entity;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<HealthExportTaskEntity> findById(Long id) {
        return Mono.fromCallable(() -> taskPoConverter.toEntity(taskMapper.selectById(id)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<HealthExportTaskEntity> findNextCandidate(String tenantId, int maxAttempts, LocalDateTime staleBefore) {
        return Mono.fromCallable(
                        () -> Optional.ofNullable(taskMapper.findNextCandidate(tenantId, maxAttempts, staleBefore))
                                .map(taskPoConverter::toEntity)
                                .orElse(null))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Boolean> claim(Long id, String tenantId, String workerId, LocalDateTime staleBefore) {
        return Mono.fromCallable(() -> taskMapper.claim(id, tenantId, LocalDateTime.now(), workerId, staleBefore) == 1)
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Boolean> markCompleted(Long id, String tenantId, String fileName, int rowCount, byte[] content) {
        return Mono.fromCallable(() ->
                        taskMapper.markCompleted(id, tenantId, fileName, rowCount, content, LocalDateTime.now()) == 1)
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Boolean> markFailed(Long id, String tenantId, String error) {
        return Mono.fromCallable(() -> taskMapper.markFailed(id, tenantId, error) == 1)
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Integer> purgeCompletedBefore(String tenantId, LocalDateTime before, int limit) {
        return Mono.fromCallable(() -> taskMapper.purgeCompletedBefore(tenantId, before, limit))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
