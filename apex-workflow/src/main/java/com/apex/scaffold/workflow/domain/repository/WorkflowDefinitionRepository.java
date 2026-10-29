package com.apex.scaffold.workflow.domain.repository;

import com.apex.scaffold.workflow.domain.model.WorkflowDefinition;
import com.apex.scaffold.workflow.domain.model.WorkflowDefinitionStatus;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 工作流定义仓储接口。
 *
 * <p>定义工作流定义（{@link WorkflowDefinition}）的持久化访问契约，包括按主键、
 * 编码查询，列表查询以及新增、更新、删除操作。该接口由基础设施层的
 * {@code WorkflowDefinitionRepositoryImpl} 提供 MyBatis-Plus 实现。</p>
 *
 * <p>仓储方法返回响应式类型（{@link Mono}/{@link Flux}），由调用方决定是否调度到
 * 阻塞 I/O 线程池执行。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see com.apex.scaffold.workflow.domain.model.WorkflowDefinition
 */
public interface WorkflowDefinitionRepository {

    /**
     * 按主键 ID 查询工作流定义。
     *
     * @param id 流程定义主键 ID
     * @return 返回包含工作流定义的 {@link Mono}；不存在时为空
     */
    Mono<WorkflowDefinition> findById(Long id);

    /**
     * 按流程编码查询已发布且启用的工作流定义。
     *
     * <p>同一编码存在多个已发布版本时，返回版本号最大的一条。</p>
     *
     * @param code 流程编码
     * @return 返回包含工作流定义的 {@link Mono}；不存在时为空
     */
    Mono<WorkflowDefinition> findByCode(String code);

    /**
     * 查询指定编码的全部版本，按版本号降序排列。
     *
     * @param code 流程编码
     * @return 返回版本列表的 {@link Mono}；无数据时为空列表
     */
    Mono<List<WorkflowDefinition>> findVersionsByCode(String code);

    /**
     * 分页查询工作流定义。
     *
     * @param workflowCode 流程编码过滤条件，为空表示不过滤
     * @param status       生命周期状态过滤条件，为空表示不过滤
     * @param offset       起始偏移量
     * @param limit        本页最大条数
     * @return 返回本页定义列表的 {@link Mono}
     */
    Mono<List<WorkflowDefinition>> findPage(
            String workflowCode, WorkflowDefinitionStatus status, int offset, int limit);

    /**
     * 统计满足过滤条件的定义数量。
     *
     * @param workflowCode 流程编码过滤条件，为空表示不过滤
     * @param status       生命周期状态过滤条件，为空表示不过滤
     * @return 返回记录数的 {@link Mono}
     */
    Mono<Long> count(String workflowCode, WorkflowDefinitionStatus status);

    /**
     * 查询全部工作流定义。
     *
     * @return 返回包含工作流定义列表的 {@link Mono}；无数据时为空列表
     */
    Mono<List<WorkflowDefinition>> findAll();

    /**
     * 新增一条工作流定义。
     *
     * <p>插入成功后会把生成的主键回填到传入的领域对象。</p>
     *
     * @param definition 待新增的工作流定义
     * @return 返回带影响行数的 {@link Mono}
     */
    Mono<Integer> insert(WorkflowDefinition definition);

    /**
     * 按主键更新工作流定义。
     *
     * @param definition 待更新的工作流定义，主键不能为空
     * @return 返回带影响行数的 {@link Mono}
     */
    Mono<Integer> updateById(WorkflowDefinition definition);

    /**
     * 按主键删除工作流定义。
     *
     * <p>采用逻辑删除。</p>
     *
     * @param id 流程定义主键 ID
     * @return 返回带影响行数的 {@link Mono}
     */
    Mono<Integer> deleteById(Long id);
}
