package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.workflow.Task;
import com.apex.scaffold.domain.repository.TaskRepository;
import com.apex.scaffold.infrastructure.persistence.converter.TaskPoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.TaskMapper;
import com.apex.scaffold.infrastructure.persistence.po.TaskPO;
import com.apex.scaffold.infrastructure.persistence.util.ConditionUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 任务仓储实现。
 *
 * <p>基于 MyBatis-Plus 实现任务的持久化操作，
 * 负责任务与数据库之间的映射与转换。
 *
 * <h2>阻塞操作隔离</h2>
 * <p>所有数据库查询操作通过 {@link BlockingIoSchedulers#jdbc()} 调度器执行，
 * 避免阻塞响应式流的事件循环线程。
 *
 * @author Apex Team
 * @version 1.3.0
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class TaskRepositoryImpl implements TaskRepository {

    private final TaskMapper taskMapper;

    private final TaskPoConverter taskPoConverter;

    @Override
    public Mono<Task> findById(Long id) {
        return Mono.fromCallable(() -> taskMapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(taskPoConverter.toEntity(po)));
    }

    @Override
    public Mono<List<Task>> findByProcessInstanceId(Long processInstanceId) {
        return Mono.fromCallable(() -> taskMapper.selectList(ConditionUtil.lambda(TaskPO.class)
                        .eq(TaskPO::getProcessInstanceId, processInstanceId)
                        .orderByDesc(TaskPO::getCreateTime)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(taskPoConverter::toEntityList);
    }

    @Override
    public Mono<List<Task>> findByAssignee(Long assigneeId) {
        return Mono.fromCallable(() -> taskMapper.selectList(ConditionUtil.lambda(TaskPO.class)
                        .eq(TaskPO::getAssigneeId, assigneeId)
                        .eq(TaskPO::getStatus, "PENDING")
                        .orderByDesc(TaskPO::getCreateTime)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(taskPoConverter::toEntityList);
    }

    @Override
    public Flux<Task> findPendingTasks() {
        return Mono.fromCallable(() -> taskMapper.selectList(ConditionUtil.lambda(TaskPO.class)
                        .eq(TaskPO::getStatus, "PENDING")
                        .isNotNull(TaskPO::getTimeoutAt)
                        .orderByAsc(TaskPO::getTimeoutAt)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(poList -> Flux.fromIterable(taskPoConverter.toEntityList(poList)));
    }

    @Override
    public Mono<Integer> insert(Task entity) {
        return Mono.fromCallable(() -> taskMapper.insert(taskPoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Integer> updateById(Task entity) {
        return Mono.fromCallable(() -> taskMapper.updateById(taskPoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Integer> deleteById(Long id) {
        return Mono.fromCallable(() -> taskMapper.deleteById(id)).subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
