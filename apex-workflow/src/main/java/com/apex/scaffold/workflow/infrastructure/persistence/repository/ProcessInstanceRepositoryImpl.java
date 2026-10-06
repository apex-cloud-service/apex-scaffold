package com.apex.scaffold.workflow.infrastructure.persistence.repository;

import com.apex.scaffold.common.persistence.util.ConditionUtil;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.workflow.domain.model.ProcessInstance;
import com.apex.scaffold.workflow.domain.repository.ProcessInstanceRepository;
import com.apex.scaffold.workflow.infrastructure.persistence.converter.ProcessInstancePoConverter;
import com.apex.scaffold.workflow.infrastructure.persistence.mapper.ProcessInstanceMapper;
import com.apex.scaffold.workflow.infrastructure.persistence.po.ProcessInstancePO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * 流程实例仓储实现。
 *
 * <p>基于 MyBatis-Plus 实现流程实例仓储接口 {@link ProcessInstanceRepository}，
 * 负责流程实例领域对象与数据库表 {@code wf_process_instance} 之间的映射与转换。</p>
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
public class ProcessInstanceRepositoryImpl implements ProcessInstanceRepository {

    /** 流程实例 MyBatis Mapper。 */
    private final ProcessInstanceMapper processInstanceMapper;

    /** 流程实例 PO 转换器。 */
    private final ProcessInstancePoConverter processInstancePoConverter;

    /** {@inheritDoc} */
    @Override
    public Mono<ProcessInstance> findById(Long id) {
        return Mono.fromCallable(() -> processInstanceMapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(processInstancePoConverter.toEntity(po)));
    }

    /** {@inheritDoc} */
    @Override
    public Mono<ProcessInstance> findByBusinessKey(String businessKey) {
        return Mono.fromCallable(() -> processInstanceMapper.selectOne(ConditionUtil.lambda(ProcessInstancePO.class)
                        .eq(ProcessInstancePO::getBusinessKey, businessKey)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(processInstancePoConverter.toEntity(po)));
    }

    /** {@inheritDoc} */
    @Override
    public Mono<List<ProcessInstance>> findByInitiator(Long initiatorId) {
        return Mono.fromCallable(() -> processInstanceMapper.selectList(ConditionUtil.lambda(ProcessInstancePO.class)
                        .eq(ProcessInstancePO::getInitiatorId, initiatorId)
                        .orderByDesc(ProcessInstancePO::getCreateTime)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(processInstancePoConverter::toEntityList);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Integer> insert(ProcessInstance entity) {
        return Mono.fromCallable(() -> processInstanceMapper.insert(processInstancePoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Integer> updateById(ProcessInstance entity) {
        return Mono.fromCallable(() -> processInstanceMapper.updateById(processInstancePoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Integer> deleteById(Long id) {
        return Mono.fromCallable(() -> processInstanceMapper.deleteById(id)).subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
