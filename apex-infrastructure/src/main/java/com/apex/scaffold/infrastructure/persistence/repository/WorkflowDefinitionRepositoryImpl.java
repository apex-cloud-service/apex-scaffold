package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.workflow.WorkflowDefinition;
import com.apex.scaffold.domain.repository.WorkflowDefinitionRepository;
import com.apex.scaffold.infrastructure.persistence.converter.WorkflowDefinitionPoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.WorkflowDefinitionMapper;
import com.apex.scaffold.infrastructure.persistence.po.WorkflowDefinitionPO;
import com.apex.scaffold.infrastructure.persistence.util.ConditionUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * 工作流定义仓储实现。
 *
 * <p>基于 MyBatis-Plus 实现工作流定义的持久化操作，
 * 负责工作流定义与数据库之间的映射与转换。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class WorkflowDefinitionRepositoryImpl implements WorkflowDefinitionRepository {

    private final WorkflowDefinitionMapper workflowDefinitionMapper;

    private final WorkflowDefinitionPoConverter workflowDefinitionPoConverter;

    @Override
    public Mono<WorkflowDefinition> findById(Long id) {
        return Mono.fromCallable(() -> workflowDefinitionMapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(workflowDefinitionPoConverter.toEntity(po)));
    }

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

    @Override
    public Mono<List<WorkflowDefinition>> findAll() {
        return Mono.fromCallable(
                        () -> workflowDefinitionMapper.selectList(ConditionUtil.lambda(WorkflowDefinitionPO.class)
                                .orderByDesc(WorkflowDefinitionPO::getCreateTime)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(workflowDefinitionPoConverter::toEntityList);
    }

    @Override
    public Mono<Integer> insert(WorkflowDefinition entity) {
        return Mono.fromCallable(() -> workflowDefinitionMapper.insert(workflowDefinitionPoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Integer> updateById(WorkflowDefinition entity) {
        return Mono.fromCallable(() -> workflowDefinitionMapper.updateById(workflowDefinitionPoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Integer> deleteById(Long id) {
        return Mono.fromCallable(() -> workflowDefinitionMapper.deleteById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
