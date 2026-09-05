package com.apex.scaffold.domain.workflow.repository;

import com.apex.scaffold.domain.workflow.WorkflowDefinitionAggregate;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 工作流定义仓储接口。
 *
 * <p>定义工作流定义聚合根的持久化操作契约，支持按编码、租户、启用状态等维度查询定义。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface WorkflowDefinitionRepository {

    Mono<WorkflowDefinitionAggregate> findById(Long id);

    Mono<WorkflowDefinitionAggregate> findByCode(String code);

    Mono<List<WorkflowDefinitionAggregate>> findByTenantId(Long tenantId);

    Mono<List<WorkflowDefinitionAggregate>> findEnabledByTenantId(Long tenantId);

    Mono<Integer> insert(WorkflowDefinitionAggregate aggregate);

    Mono<Integer> updateById(WorkflowDefinitionAggregate aggregate);

    Mono<Integer> deleteById(Long id);
}
