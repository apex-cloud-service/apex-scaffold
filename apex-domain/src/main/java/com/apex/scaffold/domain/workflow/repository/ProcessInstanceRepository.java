package com.apex.scaffold.domain.workflow.repository;

import com.apex.scaffold.domain.workflow.ProcessInstanceEntity;
import com.apex.scaffold.domain.workflow.ProcessStatus;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 流程实例仓储接口。
 *
 * <p>定义流程实例的持久化操作契约，支持按业务键、发起人、状态、租户等维度查询实例。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface ProcessInstanceRepository {

    Mono<ProcessInstanceEntity> findById(Long id);

    Mono<ProcessInstanceEntity> findByBusinessKey(String businessKey);

    Mono<List<ProcessInstanceEntity>> findByInitiatorId(Long initiatorId);

    Mono<List<ProcessInstanceEntity>> findByStatus(ProcessStatus status);

    Mono<List<ProcessInstanceEntity>> findByTenantIdAndStatus(Long tenantId, ProcessStatus status);

    Mono<Integer> insert(ProcessInstanceEntity entity);

    Mono<Integer> updateById(ProcessInstanceEntity entity);

    Mono<Integer> deleteById(Long id);
}
