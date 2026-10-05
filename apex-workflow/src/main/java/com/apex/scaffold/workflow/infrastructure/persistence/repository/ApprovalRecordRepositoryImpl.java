package com.apex.scaffold.workflow.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
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
 * 审批记录仓储实现。
 *
 * <p>基于 MyBatis-Plus 实现审批记录仓储接口 {@link ApprovalRecordRepository}，
 * 负责审批记录领域对象与数据库表 {@code wf_approval_record} 之间的映射与转换。</p>
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
public class ApprovalRecordRepositoryImpl implements ApprovalRecordRepository {

    /** 审批记录 MyBatis Mapper。 */
    private final ApprovalRecordMapper approvalRecordMapper;

    /** 审批记录 PO 转换器。 */
    private final ApprovalRecordPoConverter approvalRecordPoConverter;

    /** {@inheritDoc} */
    @Override
    public Mono<ApprovalRecord> findById(Long id) {
        return Mono.fromCallable(() -> approvalRecordMapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(approvalRecordPoConverter.toEntity(po)));
    }

    /** {@inheritDoc} */
    @Override
    public Mono<List<ApprovalRecord>> findByProcessInstanceId(Long processInstanceId) {
        return Mono.fromCallable(() -> approvalRecordMapper.selectList(ConditionUtil.lambda(ApprovalRecordPO.class)
                        .eq(ApprovalRecordPO::getProcessInstanceId, processInstanceId)
                        .orderByAsc(ApprovalRecordPO::getCreateTime)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(approvalRecordPoConverter::toEntityList);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<List<ApprovalRecord>> findByTaskId(Long taskId) {
        return Mono.fromCallable(() -> approvalRecordMapper.selectList(ConditionUtil.lambda(ApprovalRecordPO.class)
                        .eq(ApprovalRecordPO::getTaskId, taskId)
                        .orderByAsc(ApprovalRecordPO::getCreateTime)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(approvalRecordPoConverter::toEntityList);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Integer> insert(ApprovalRecord entity) {
        return Mono.fromCallable(() -> approvalRecordMapper.insert(approvalRecordPoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Integer> updateById(ApprovalRecord entity) {
        return Mono.fromCallable(() -> approvalRecordMapper.updateById(approvalRecordPoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Integer> deleteById(Long id) {
        return Mono.fromCallable(() -> approvalRecordMapper.deleteById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }
}