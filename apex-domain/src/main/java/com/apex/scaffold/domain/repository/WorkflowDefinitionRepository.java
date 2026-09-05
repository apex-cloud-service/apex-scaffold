package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.workflow.WorkflowDefinition;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 工作流定义仓储接口。
 *
 * <p>定义工作流定义的持久化操作契约，由基础设施层实现。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface WorkflowDefinitionRepository {

    Mono<WorkflowDefinition> findById(Long id);

    Mono<WorkflowDefinition> findByCode(String workflowCode);

    Mono<List<WorkflowDefinition>> findAll();

    Mono<Integer> insert(WorkflowDefinition entity);

    Mono<Integer> updateById(WorkflowDefinition entity);

    Mono<Integer> deleteById(Long id);
}
