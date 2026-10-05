package com.apex.scaffold.workflow.domain.repository;

import com.apex.scaffold.workflow.domain.model.ProcessInstance;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 流程实例仓储接口。
 *
 * <p>定义流程实例（{@link ProcessInstance}）的持久化访问契约，包括按主键、业务主键、
 * 发起人查询，以及新增、更新、删除操作。该接口由基础设施层的
 * {@code ProcessInstanceRepositoryImpl} 提供 MyBatis-Plus 实现。</p>
 *
 * @author Apex Team
 * @version 1.3.0
 * @since 1.0.0
 * @see com.apex.scaffold.workflow.domain.model.ProcessInstance
 */
public interface ProcessInstanceRepository {

    /**
     * 按主键 ID 查询流程实例。
     *
     * @param id 流程实例主键 ID
     * @return 返回包含流程实例的 {@link Mono}；不存在时为空
     */
    Mono<ProcessInstance> findById(Long id);

    /**
     * 按业务主键查询流程实例。
     *
     * @param businessKey 业务主键
     * @return 返回包含流程实例的 {@link Mono}；不存在时为空
     */
    Mono<ProcessInstance> findByBusinessKey(String businessKey);

    /**
     * 按发起人查询流程实例列表。
     *
     * @param initiatorId 发起人用户 ID
     * @return 返回包含流程实例列表的 {@link Mono}；无数据时为空列表
     */
    Mono<List<ProcessInstance>> findByInitiator(Long initiatorId);

    /**
     * 新增一条流程实例。
     *
     * @param instance 待新增的流程实例
     * @return 返回携带自增主键的回填结果 {@link Mono}
     */
    Mono<Integer> insert(ProcessInstance instance);

    /**
     * 按主键更新流程实例。
     *
     * @param instance 待更新的流程实例，主键不能为空
     * @return 返回带影响行数的 {@link Mono}
     */
    Mono<Integer> updateById(ProcessInstance instance);

    /**
     * 按主键删除流程实例。
     *
     * <p>采用逻辑删除。</p>
     *
     * @param id 流程实例主键 ID
     * @return 返回带影响行数的 {@link Mono}
     */
    Mono<Integer> deleteById(Long id);
}