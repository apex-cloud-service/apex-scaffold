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

    /**
     * 根据主键ID查询工作流定义。
     *
     * @param id 工作流定义ID
     * @return 工作流定义实体
     */
    Mono<WorkflowDefinition> findById(Long id);

    /**
     * 根据工作流编码查询工作流定义。
     *
     * @param workflowCode 工作流编码
     * @return 工作流定义实体
     */
    Mono<WorkflowDefinition> findByCode(String workflowCode);

    /**
     * 查询所有工作流定义。
     *
     * @return 工作流定义列表
     */
    Mono<List<WorkflowDefinition>> findAll();

    /**
     * 插入工作流定义。
     *
     * @param entity 工作流定义实体
     * @return 影响的行数
     */
    Mono<Integer> insert(WorkflowDefinition entity);

    /**
     * 根据ID更新工作流定义。
     *
     * @param entity 工作流定义实体
     * @return 影响的行数
     */
    Mono<Integer> updateById(WorkflowDefinition entity);

    /**
     * 根据ID删除工作流定义。
     *
     * @param id 工作流定义ID
     * @return 影响的行数
     */
    Mono<Integer> deleteById(Long id);
}
