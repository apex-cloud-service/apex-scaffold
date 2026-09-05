package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.workflow.ApprovalRecord;
import com.apex.scaffold.domain.repository.ApprovalRecordRepository;
import com.apex.scaffold.infrastructure.persistence.converter.ApprovalRecordPoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.ApprovalRecordMapper;
import com.apex.scaffold.infrastructure.persistence.po.ApprovalRecordPO;
import com.apex.scaffold.infrastructure.persistence.util.ConditionUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * 审批记录仓储实现。
 *
 * <p>基于 MyBatis-Plus 实现审批记录的持久化操作，
 * 负责审批记录与数据库之间的映射与转换。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class ApprovalRecordRepositoryImpl implements ApprovalRecordRepository {

    private final ApprovalRecordMapper approvalRecordMapper;

    private final ApprovalRecordPoConverter approvalRecordPoConverter;

    @Override
    public Mono<ApprovalRecord> findById(Long id) {
        return Mono.fromCallable(() -> approvalRecordMapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(approvalRecordPoConverter.toEntity(po)));
    }

    @Override
    public Mono<List<ApprovalRecord>> findByProcessInstanceId(Long processInstanceId) {
        return Mono.fromCallable(() -> approvalRecordMapper.selectList(ConditionUtil.lambda(ApprovalRecordPO.class)
                        .eq(ApprovalRecordPO::getProcessInstanceId, processInstanceId)
                        .orderByAsc(ApprovalRecordPO::getCreateTime)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(approvalRecordPoConverter::toEntityList);
    }

    @Override
    public Mono<List<ApprovalRecord>> findByTaskId(Long taskId) {
        return Mono.fromCallable(() -> approvalRecordMapper.selectList(ConditionUtil.lambda(ApprovalRecordPO.class)
                        .eq(ApprovalRecordPO::getTaskId, taskId)
                        .orderByAsc(ApprovalRecordPO::getCreateTime)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(approvalRecordPoConverter::toEntityList);
    }

    @Override
    public Mono<Integer> insert(ApprovalRecord entity) {
        return Mono.fromCallable(() -> approvalRecordMapper.insert(approvalRecordPoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Integer> updateById(ApprovalRecord entity) {
        return Mono.fromCallable(() -> approvalRecordMapper.updateById(approvalRecordPoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Integer> deleteById(Long id) {
        return Mono.fromCallable(() -> approvalRecordMapper.deleteById(id)).subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
