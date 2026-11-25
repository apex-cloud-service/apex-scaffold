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

    /**
     * 同步按主键查询流程实例，供事务回调复用。
     *
     * @param tenant 租户标识，由受信任上下文传入，不接受客户端覆盖
     * @param id 流程实例主键
     * @return 流程实例；不存在时返回 {@code null}
     */
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

    /**
     * 同步新增流程实例，供事务回调复用。
     *
     * <p>写入时补齐租户、创建/更新时间与初始版本号，并把数据库生成的主键回填到实体。
     *
     * @param tenant 租户标识，写入 {@code tenant_id} 列
     * @param entity 待新增的流程实例；调用后其 {@code id} 被回填
     * @return 受影响行数
     */
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

    /**
     * 同步按主键更新流程实例，供事务回调复用。
     *
     * <p>更新条件同时限定主键与租户；转换出的持久化对象会清空租户字段，避免误改归属。
     *
     * @param tenant 租户标识，作为更新条件的一部分
     * @param entity 待更新的流程实例，需携带主键
     * @return 受影响行数
     */
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
