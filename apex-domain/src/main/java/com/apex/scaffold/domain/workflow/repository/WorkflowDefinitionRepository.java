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

    /**
     * 根据主键ID查询工作流定义。
     *
     * @param id 工作流定义ID
     * @return 工作流定义聚合根
     */
    Mono<WorkflowDefinitionAggregate> findById(Long id);

    /**
     * 根据工作流编码查询工作流定义。
     *
     * @param code 工作流编码
     * @return 工作流定义聚合根
     */
    Mono<WorkflowDefinitionAggregate> findByCode(String code);

    /**
     * 根据租户ID查询工作流定义列表。
     *
     * @param tenantId 租户ID
     * @return 工作流定义列表
     */
    Mono<List<WorkflowDefinitionAggregate>> findByTenantId(Long tenantId);

    /**
     * 根据租户ID查询已启用的工作流定义列表。
     *
     * @param tenantId 租户ID
     * @return 已启用的工作流定义列表
     */
    Mono<List<WorkflowDefinitionAggregate>> findEnabledByTenantId(Long tenantId);

    /**
     * 插入工作流定义。
     *
     * @param aggregate 工作流定义聚合根
     * @return 影响的行数
     */
    Mono<Integer> insert(WorkflowDefinitionAggregate aggregate);

    /**
     * 根据ID更新工作流定义。
     *
     * @param aggregate 工作流定义聚合根
     * @return 影响的行数
     */
    Mono<Integer> updateById(WorkflowDefinitionAggregate aggregate);

    /**
     * 根据ID删除工作流定义。
     *
     * @param id 工作流定义ID
     * @return 影响的行数
     */
    Mono<Integer> deleteById(Long id);
}
