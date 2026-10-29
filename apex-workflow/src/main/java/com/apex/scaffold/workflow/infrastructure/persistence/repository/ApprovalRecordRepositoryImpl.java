package com.apex.scaffold.workflow.infrastructure.persistence.repository;

import com.apex.scaffold.common.persistence.util.ConditionUtil;
import com.apex.scaffold.workflow.domain.model.ApprovalRecord;
import com.apex.scaffold.workflow.domain.repository.ApprovalRecordRepository;
import com.apex.scaffold.workflow.infrastructure.persistence.converter.ApprovalRecordPoConverter;
import com.apex.scaffold.workflow.infrastructure.persistence.mapper.ApprovalRecordMapper;
import com.apex.scaffold.workflow.infrastructure.persistence.po.ApprovalRecordPO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * 审批记录仓储实现，所有普通访问必须携带可信 Reactor 租户。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Repository
@RequiredArgsConstructor
public class ApprovalRecordRepositoryImpl implements ApprovalRecordRepository {
    private final ApprovalRecordMapper approvalRecordMapper;
    private final ApprovalRecordPoConverter approvalRecordPoConverter;

    @Override
    public Mono<ApprovalRecord> findById(Long id) {
        return WorkflowTenantAccess.execute(tenant -> approvalRecordPoConverter.toEntity(
                approvalRecordMapper.selectOne(ConditionUtil.lambda(ApprovalRecordPO.class)
                        .eq(ApprovalRecordPO::getId, id)
                        .eq(ApprovalRecordPO::getTenantId, tenant))));
    }

    @Override
    public Mono<List<ApprovalRecord>> findByProcessInstanceId(Long processInstanceId) {
        return WorkflowTenantAccess.execute(tenant -> approvalRecordPoConverter.toEntityList(
                approvalRecordMapper.selectList(ConditionUtil.lambda(ApprovalRecordPO.class)
                        .eq(ApprovalRecordPO::getTenantId, tenant)
                        .eq(ApprovalRecordPO::getProcessInstanceId, processInstanceId)
                        .orderByAsc(ApprovalRecordPO::getCreateTime))));
    }

    @Override
    public Mono<List<ApprovalRecord>> findByTaskId(Long taskId) {
        return WorkflowTenantAccess.execute(tenant -> approvalRecordPoConverter.toEntityList(
                approvalRecordMapper.selectList(ConditionUtil.lambda(ApprovalRecordPO.class)
                        .eq(ApprovalRecordPO::getTenantId, tenant)
                        .eq(ApprovalRecordPO::getTaskId, taskId)
                        .orderByAsc(ApprovalRecordPO::getCreateTime))));
    }

    @Override
    public Mono<Integer> insert(ApprovalRecord entity) {
        return WorkflowTenantAccess.execute(tenant -> {
            ApprovalRecordPO po = approvalRecordPoConverter.toPo(entity);
            po.setTenantId(tenant);
            return approvalRecordMapper.insert(po);
        });
    }

    @Override
    public Mono<Integer> updateById(ApprovalRecord entity) {
        return WorkflowTenantAccess.execute(tenant -> {
            ApprovalRecordPO po = approvalRecordPoConverter.toPo(entity);
            po.setTenantId(null);
            return approvalRecordMapper.update(
                    po,
                    ConditionUtil.lambda(ApprovalRecordPO.class)
                            .eq(ApprovalRecordPO::getId, entity.getId())
                            .eq(ApprovalRecordPO::getTenantId, tenant));
        });
    }

    @Override
    public Mono<Integer> deleteById(Long id) {
        return WorkflowTenantAccess.execute(
                tenant -> approvalRecordMapper.delete(ConditionUtil.lambda(ApprovalRecordPO.class)
                        .eq(ApprovalRecordPO::getId, id)
                        .eq(ApprovalRecordPO::getTenantId, tenant)));
    }
}
