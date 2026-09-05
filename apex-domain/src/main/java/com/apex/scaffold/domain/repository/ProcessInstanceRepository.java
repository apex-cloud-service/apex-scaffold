package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.workflow.ProcessInstance;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 流程实例仓储接口。
 *
 * <p>定义流程实例的持久化操作契约，由基础设施层实现。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface ProcessInstanceRepository {

    Mono<ProcessInstance> findById(Long id);

    Mono<ProcessInstance> findByBusinessKey(String businessKey);

    Mono<List<ProcessInstance>> findByInitiator(Long initiatorId);

    Mono<Integer> insert(ProcessInstance entity);

    Mono<Integer> updateById(ProcessInstance entity);

    Mono<Integer> deleteById(Long id);
}
