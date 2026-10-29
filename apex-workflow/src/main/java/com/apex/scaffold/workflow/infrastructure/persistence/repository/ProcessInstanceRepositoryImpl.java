package com.apex.scaffold.workflow.infrastructure.persistence.repository;

import com.apex.scaffold.common.persistence.util.ConditionUtil;
import com.apex.scaffold.workflow.domain.model.ProcessInstance;
import com.apex.scaffold.workflow.domain.repository.ProcessInstanceRepository;
import com.apex.scaffold.workflow.infrastructure.persistence.converter.ProcessInstancePoConverter;
import com.apex.scaffold.workflow.infrastructure.persistence.mapper.ProcessInstanceMapper;
import com.apex.scaffold.workflow.infrastructure.persistence.po.ProcessInstancePO;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * 使用显式租户 SQL 条件的实例仓储。
 * @author Apex Team
 * @version 1.3.1
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class ProcessInstanceRepositoryImpl implements ProcessInstanceRepository {
    private final ProcessInstanceMapper processInstanceMapper;
    private final ProcessInstancePoConverter processInstancePoConverter;

    /** {@inheritDoc} */
    @Override
    public Mono<ProcessInstance> findById(Long id) {
        return WorkflowTenantAccess.execute(tenant -> findByIdSync(tenant, id));
    }

    /** 同步查询，供事务回调复用。 */
    public ProcessInstance findByIdSync(String tenant, Long id) {
        return processInstancePoConverter.toEntity(
                processInstanceMapper.selectOne(ConditionUtil.lambda(ProcessInstancePO.class)
                        .eq(ProcessInstancePO::getId, id)
                        .eq(ProcessInstancePO::getTenantId, tenant)));
    }

    /** {@inheritDoc} */
    @Override
    public Mono<ProcessInstance> findByBusinessKey(String key) {
        return WorkflowTenantAccess.execute(tenant -> processInstancePoConverter.toEntity(
                processInstanceMapper.selectOne(ConditionUtil.lambda(ProcessInstancePO.class)
                        .eq(ProcessInstancePO::getBusinessKey, key)
                        .eq(ProcessInstancePO::getTenantId, tenant))));
    }

    /** {@inheritDoc} */
    @Override
    public Mono<List<ProcessInstance>> findByInitiator(Long id) {
        return WorkflowTenantAccess.execute(tenant -> processInstancePoConverter.toEntityList(
                processInstanceMapper.selectList(ConditionUtil.lambda(ProcessInstancePO.class)
                        .eq(ProcessInstancePO::getTenantId, tenant)
                        .eq(ProcessInstancePO::getInitiatorId, id)
                        .orderByDesc(ProcessInstancePO::getCreateTime))));
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Integer> insert(ProcessInstance entity) {
        return WorkflowTenantAccess.execute(tenant -> insertSync(tenant, entity));
    }

    /** 同步新增，供事务回调复用。 */
    public int insertSync(String tenant, ProcessInstance entity) {
        ProcessInstancePO po = processInstancePoConverter.toPo(entity);
        po.setTenantId(tenant);
        po.setCreateTime(LocalDateTime.now());
        po.setUpdateTime(po.getCreateTime());
        po.setVersion(0);
        int inserted = processInstanceMapper.insert(po);
        entity.setId(po.getId());
        return inserted;
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Integer> updateById(ProcessInstance entity) {
        return WorkflowTenantAccess.execute(tenant -> updateByIdSync(tenant, entity));
    }

    /** 同步更新，供事务回调复用。 */
    public int updateByIdSync(String tenant, ProcessInstance entity) {
        ProcessInstancePO po = processInstancePoConverter.toPo(entity);
        po.setTenantId(null);
        return processInstanceMapper.update(
                po,
                ConditionUtil.lambda(ProcessInstancePO.class)
                        .eq(ProcessInstancePO::getId, entity.getId())
                        .eq(ProcessInstancePO::getTenantId, tenant));
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Integer> deleteById(Long id) {
        return WorkflowTenantAccess.execute(
                tenant -> processInstanceMapper.delete(ConditionUtil.lambda(ProcessInstancePO.class)
                        .eq(ProcessInstancePO::getId, id)
                        .eq(ProcessInstancePO::getTenantId, tenant)));
    }
}
