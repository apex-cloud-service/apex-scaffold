package com.apex.scaffold.workflow.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.common.persistence.util.ConditionUtil;
import com.apex.scaffold.workflow.domain.model.WorkflowDefinition;
import com.apex.scaffold.workflow.domain.repository.WorkflowDefinitionRepository;
import com.apex.scaffold.workflow.infrastructure.persistence.converter.WorkflowDefinitionPoConverter;
import com.apex.scaffold.workflow.infrastructure.persistence.mapper.WorkflowDefinitionMapper;
import com.apex.scaffold.workflow.infrastructure.persistence.po.WorkflowDefinitionPO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * 工作流定义仓储实现。
 *
 * <p>基于 MyBatis-Plus 实现工作流定义仓储接口 {@link WorkflowDefinitionRepository}，
 * 负责工作流定义领域对象与数据库表 {@code wf_workflow_definition} 之间的映射与转换。</p>
 *
 * <h2>阻塞操作隔离</h2>
 * <p>所有数据库操作通过 {@link BlockingIoSchedulers#jdbc()} 调度器执行，
 * 避免阻塞响应式流的事件循环线程。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class WorkflowDefinitionRepositoryImpl implements WorkflowDefinitionRepository {

    /** 工作流定义 MyBatis Mapper。 */
    private final WorkflowDefinitionMapper workflowDefinitionMapper;

    /** 工作流定义 PO 转换器。 */
    private final WorkflowDefinitionPoConverter workflowDefinitionPoConverter;

    /** {@inheritDoc} */
    @Override
    public Mono<WorkflowDefinition> findById(Long id) {
        return Mono.fromCallable(() -> workflowDefinitionMapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(workflowDefinitionPoConverter.toEntity(po)));
    }

    /** {@inheritDoc} */
    @Override
    public Mono<WorkflowDefinition> findByCode(String workflowCode) {
        return Mono.fromCallable(
                        () -> workflowDefinitionMapper.selectOne(ConditionUtil.lambda(WorkflowDefinitionPO.class)
                                .eq(WorkflowDefinitionPO::getWorkflowCode, workflowCode)
                                .eq(WorkflowDefinitionPO::getEnabled, 1)
                                .orderByDesc(WorkflowDefinitionPO::getVersion)
                                .last("LIMIT 1")))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(workflowDefinitionPoConverter.toEntity(po)));
    }

    /** {@inheritDoc} */
    @Override
    public Mono<List<WorkflowDefinition>> findAll() {
        return Mono.fromCallable(
                        () -> workflowDefinitionMapper.selectList(ConditionUtil.lambda(WorkflowDefinitionPO.class)
                                .orderByDesc(WorkflowDefinitionPO::getCreateTime)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(workflowDefinitionPoConverter::toEntityList);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Integer> insert(WorkflowDefinition entity) {
        return Mono.fromCallable(() -> workflowDefinitionMapper.insert(workflowDefinitionPoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Integer> updateById(WorkflowDefinition entity) {
        return Mono.fromCallable(() -> workflowDefinitionMapper.updateById(workflowDefinitionPoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Integer> deleteById(Long id) {
        return Mono.fromCallable(() -> workflowDefinitionMapper.deleteById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }
}