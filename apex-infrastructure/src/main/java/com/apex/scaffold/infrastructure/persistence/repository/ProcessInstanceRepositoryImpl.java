package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.workflow.ProcessInstance;
import com.apex.scaffold.domain.repository.ProcessInstanceRepository;
import com.apex.scaffold.infrastructure.persistence.converter.ProcessInstancePoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.ProcessInstanceMapper;
import com.apex.scaffold.infrastructure.persistence.po.ProcessInstancePO;
import com.apex.scaffold.infrastructure.persistence.util.ConditionUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * 流程实例仓储实现。
 *
 * <p>基于 MyBatis-Plus 实现流程实例的持久化操作，
 * 负责流程实例与数据库之间的映射与转换。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class ProcessInstanceRepositoryImpl implements ProcessInstanceRepository {

    private final ProcessInstanceMapper processInstanceMapper;

    private final ProcessInstancePoConverter processInstancePoConverter;

    @Override
    public Mono<ProcessInstance> findById(Long id) {
        return Mono.fromCallable(() -> processInstanceMapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(processInstancePoConverter.toEntity(po)));
    }

    @Override
    public Mono<ProcessInstance> findByBusinessKey(String businessKey) {
        return Mono.fromCallable(() -> processInstanceMapper.selectOne(ConditionUtil.lambda(ProcessInstancePO.class)
                        .eq(ProcessInstancePO::getBusinessKey, businessKey)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(processInstancePoConverter.toEntity(po)));
    }

    @Override
    public Mono<List<ProcessInstance>> findByInitiator(Long initiatorId) {
        return Mono.fromCallable(() -> processInstanceMapper.selectList(ConditionUtil.lambda(ProcessInstancePO.class)
                        .eq(ProcessInstancePO::getInitiatorId, initiatorId)
                        .orderByDesc(ProcessInstancePO::getCreateTime)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(processInstancePoConverter::toEntityList);
    }

    @Override
    public Mono<Integer> insert(ProcessInstance entity) {
        return Mono.fromCallable(() -> processInstanceMapper.insert(processInstancePoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Integer> updateById(ProcessInstance entity) {
        return Mono.fromCallable(() -> processInstanceMapper.updateById(processInstancePoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Integer> deleteById(Long id) {
        return Mono.fromCallable(() -> processInstanceMapper.deleteById(id)).subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
