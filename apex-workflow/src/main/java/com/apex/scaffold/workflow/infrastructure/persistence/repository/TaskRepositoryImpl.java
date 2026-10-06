package com.apex.scaffold.workflow.infrastructure.persistence.repository;

import com.apex.scaffold.common.persistence.util.ConditionUtil;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.workflow.domain.model.Task;
import com.apex.scaffold.workflow.domain.repository.TaskRepository;
import com.apex.scaffold.workflow.infrastructure.persistence.converter.TaskPoConverter;
import com.apex.scaffold.workflow.infrastructure.persistence.mapper.TaskMapper;
import com.apex.scaffold.workflow.infrastructure.persistence.po.TaskPO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 流程任务仓储实现。
 *
 * <p>基于 MyBatis-Plus 实现流程任务仓储接口 {@link TaskRepository}，
 * 负责任务领域对象与数据库表 {@code wf_task} 之间的映射与转换。</p>
 *
 * <h2>阻塞操作隔离</h2>
 * <p>所有数据库查询操作通过 {@link BlockingIoSchedulers#jdbc()} 调度器执行，
 * 避免阻塞响应式流的事件循环线程。</p>
 *
 * @author Apex Team
 * @version 1.3.0
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class TaskRepositoryImpl implements TaskRepository {

    /** 任务 MyBatis Mapper。 */
    private final TaskMapper taskMapper;

    /** 任务 PO 转换器。 */
    private final TaskPoConverter taskPoConverter;

    /** {@inheritDoc} */
    @Override
    public Mono<Task> findById(Long id) {
        return Mono.fromCallable(() -> taskMapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(taskPoConverter.toEntity(po)));
    }

    /** {@inheritDoc} */
    @Override
    public Mono<List<Task>> findByProcessInstanceId(Long processInstanceId) {
        return Mono.fromCallable(() -> taskMapper.selectList(ConditionUtil.lambda(TaskPO.class)
                        .eq(TaskPO::getProcessInstanceId, processInstanceId)
                        .orderByDesc(TaskPO::getCreateTime)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(taskPoConverter::toEntityList);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<List<Task>> findByAssignee(Long assigneeId) {
        return Mono.fromCallable(() -> taskMapper.selectList(ConditionUtil.lambda(TaskPO.class)
                        .eq(TaskPO::getAssigneeId, assigneeId)
                        .eq(TaskPO::getStatus, "PENDING")
                        .orderByDesc(TaskPO::getCreateTime)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(taskPoConverter::toEntityList);
    }

    /** {@inheritDoc} */
    @Override
    public Flux<Task> findPendingTasks() {
        return Mono.fromCallable(() -> taskMapper.selectList(ConditionUtil.lambda(TaskPO.class)
                        .eq(TaskPO::getStatus, "PENDING")
                        .isNotNull(TaskPO::getTimeoutAt)
                        .orderByAsc(TaskPO::getTimeoutAt)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(poList -> Flux.fromIterable(taskPoConverter.toEntityList(poList)));
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Integer> insert(Task entity) {
        return Mono.fromCallable(() -> taskMapper.insert(taskPoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Integer> updateById(Task entity) {
        return Mono.fromCallable(() -> taskMapper.updateById(taskPoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Integer> deleteById(Long id) {
        return Mono.fromCallable(() -> taskMapper.deleteById(id)).subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
