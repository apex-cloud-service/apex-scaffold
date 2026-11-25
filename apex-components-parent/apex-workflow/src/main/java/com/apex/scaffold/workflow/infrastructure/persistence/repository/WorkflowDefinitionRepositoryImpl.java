package com.apex.scaffold.workflow.infrastructure.persistence.repository;

import com.apex.scaffold.common.persistence.util.ConditionUtil;
import com.apex.scaffold.workflow.domain.model.WorkflowDefinition;
import com.apex.scaffold.workflow.domain.model.WorkflowDefinitionStatus;
import com.apex.scaffold.workflow.domain.repository.WorkflowDefinitionRepository;
import com.apex.scaffold.workflow.infrastructure.persistence.converter.WorkflowDefinitionPoConverter;
import com.apex.scaffold.workflow.infrastructure.persistence.mapper.WorkflowDefinitionMapper;
import com.apex.scaffold.workflow.infrastructure.persistence.po.WorkflowDefinitionPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * 使用显式租户 SQL 条件的定义仓储。
 * @author Apex Team
 * @version 1.3.1
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class WorkflowDefinitionRepositoryImpl implements WorkflowDefinitionRepository {
    private final WorkflowDefinitionMapper workflowDefinitionMapper;
    private final WorkflowDefinitionPoConverter workflowDefinitionPoConverter;

    /** {@inheritDoc} */
    @Override
    public Mono<WorkflowDefinition> findById(Long id) {
        return WorkflowTenantAccess.execute(tenant -> findByIdSync(tenant, id));
    }

    /**
     * 同步按主键查询流程定义，供事务回调复用。
     *
     * <p>查询条件同时限定主键与租户，避免跨租户读取。
     *
     * @param tenant 租户标识，由受信任上下文传入
     * @param id 流程定义主键
     * @return 流程定义实体；不存在时返回 {@code null}
     */
    public WorkflowDefinition findByIdSync(String tenant, Long id) {
        return workflowDefinitionPoConverter.toEntity(
                workflowDefinitionMapper.selectOne(ConditionUtil.lambda(WorkflowDefinitionPO.class)
                        .eq(WorkflowDefinitionPO::getId, id)
                        .eq(WorkflowDefinitionPO::getTenantId, tenant)));
    }

    /** {@inheritDoc} */
    @Override
    public Mono<WorkflowDefinition> findByCode(String code) {
        return WorkflowTenantAccess.execute(tenant -> workflowDefinitionPoConverter.toEntity(
                workflowDefinitionMapper.selectOne(ConditionUtil.lambda(WorkflowDefinitionPO.class)
                        .eq(WorkflowDefinitionPO::getTenantId, tenant)
                        .eq(WorkflowDefinitionPO::getWorkflowCode, code)
                        .eq(WorkflowDefinitionPO::getEnabled, 1)
                        .eq(WorkflowDefinitionPO::getStatus, WorkflowDefinitionStatus.PUBLISHED.name())
                        .orderByDesc(WorkflowDefinitionPO::getVersion)
                        .last("LIMIT 1"))));
    }

    /** {@inheritDoc} */
    @Override
    public Mono<List<WorkflowDefinition>> findVersionsByCode(String code) {
        return WorkflowTenantAccess.execute(tenant -> workflowDefinitionPoConverter.toEntityList(
                workflowDefinitionMapper.selectList(ConditionUtil.lambda(WorkflowDefinitionPO.class)
                        .eq(WorkflowDefinitionPO::getTenantId, tenant)
                        .eq(WorkflowDefinitionPO::getWorkflowCode, code)
                        .orderByDesc(WorkflowDefinitionPO::getVersion))));
    }

    /** {@inheritDoc} */
    @Override
    public Mono<List<WorkflowDefinition>> findPage(
            String workflowCode, WorkflowDefinitionStatus status, int offset, int limit) {
        return WorkflowTenantAccess.execute(tenant -> workflowDefinitionPoConverter.toEntityList(
                workflowDefinitionMapper.selectList(pageCondition(tenant, workflowCode, status)
                        .orderByDesc(WorkflowDefinitionPO::getUpdateTime)
                        .last("LIMIT " + limit + " OFFSET " + offset))));
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Long> count(String workflowCode, WorkflowDefinitionStatus status) {
        return WorkflowTenantAccess.execute(
                tenant -> workflowDefinitionMapper.selectCount(pageCondition(tenant, workflowCode, status)));
    }

    private LambdaQueryWrapper<WorkflowDefinitionPO> pageCondition(
            String tenant, String workflowCode, WorkflowDefinitionStatus status) {
        return ConditionUtil.lambda(WorkflowDefinitionPO.class)
                .eq(WorkflowDefinitionPO::getTenantId, tenant)
                .eq(
                        workflowCode != null && !workflowCode.isBlank(),
                        WorkflowDefinitionPO::getWorkflowCode,
                        workflowCode)
                .eq(status != null, WorkflowDefinitionPO::getStatus, status == null ? null : status.name());
    }

    /** {@inheritDoc} */
    @Override
    public Mono<List<WorkflowDefinition>> findAll() {
        return WorkflowTenantAccess.execute(tenant -> workflowDefinitionPoConverter.toEntityList(
                workflowDefinitionMapper.selectList(ConditionUtil.lambda(WorkflowDefinitionPO.class)
                        .eq(WorkflowDefinitionPO::getTenantId, tenant)
                        .orderByDesc(WorkflowDefinitionPO::getCreateTime))));
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Integer> insert(WorkflowDefinition entity) {
        return WorkflowTenantAccess.execute(tenant -> {
            WorkflowDefinitionPO po = workflowDefinitionPoConverter.toPo(entity);
            po.setTenantId(tenant);
            int inserted = workflowDefinitionMapper.insert(po);
            entity.setId(po.getId());
            return inserted;
        });
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Integer> updateById(WorkflowDefinition entity) {
        return WorkflowTenantAccess.execute(tenant -> {
            WorkflowDefinitionPO po = workflowDefinitionPoConverter.toPo(entity);
            po.setTenantId(null);
            return workflowDefinitionMapper.update(
                    po,
                    ConditionUtil.lambda(WorkflowDefinitionPO.class)
                            .eq(WorkflowDefinitionPO::getId, entity.getId())
                            .eq(WorkflowDefinitionPO::getTenantId, tenant));
        });
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Integer> deleteById(Long id) {
        return WorkflowTenantAccess.execute(
                tenant -> workflowDefinitionMapper.delete(ConditionUtil.lambda(WorkflowDefinitionPO.class)
                        .eq(WorkflowDefinitionPO::getId, id)
                        .eq(WorkflowDefinitionPO::getTenantId, tenant)));
    }
}
