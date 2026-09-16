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

    /**
     * 根据主键ID查询流程实例。
     *
     * @param id 流程实例ID
     * @return 流程实例实体
     */
    Mono<ProcessInstance> findById(Long id);

    /**
     * 根据业务键查询流程实例。
     *
     * @param businessKey 业务键
     * @return 流程实例实体
     */
    Mono<ProcessInstance> findByBusinessKey(String businessKey);

    /**
     * 根据发起人ID查询流程实例列表。
     *
     * @param initiatorId 发起人ID
     * @return 流程实例列表
     */
    Mono<List<ProcessInstance>> findByInitiator(Long initiatorId);

    /**
     * 插入流程实例。
     *
     * @param entity 流程实例实体
     * @return 影响的行数
     */
    Mono<Integer> insert(ProcessInstance entity);

    /**
     * 根据ID更新流程实例。
     *
     * @param entity 流程实例实体
     * @return 影响的行数
     */
    Mono<Integer> updateById(ProcessInstance entity);

    /**
     * 根据ID删除流程实例。
     *
     * @param id 流程实例ID
     * @return 影响的行数
     */
    Mono<Integer> deleteById(Long id);
}
