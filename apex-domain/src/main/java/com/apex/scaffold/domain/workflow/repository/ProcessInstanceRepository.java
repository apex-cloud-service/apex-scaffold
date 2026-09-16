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

    /**
     * 根据主键ID查询流程实例。
     *
     * @param id 流程实例ID
     * @return 流程实例实体
     */
    Mono<ProcessInstanceEntity> findById(Long id);

    /**
     * 根据业务键查询流程实例。
     *
     * @param businessKey 业务键
     * @return 流程实例实体
     */
    Mono<ProcessInstanceEntity> findByBusinessKey(String businessKey);

    /**
     * 根据发起人ID查询流程实例列表。
     *
     * @param initiatorId 发起人ID
     * @return 流程实例列表
     */
    Mono<List<ProcessInstanceEntity>> findByInitiatorId(Long initiatorId);

    /**
     * 根据流程状态查询流程实例列表。
     *
     * @param status 流程状态
     * @return 流程实例列表
     */
    Mono<List<ProcessInstanceEntity>> findByStatus(ProcessStatus status);

    /**
     * 根据租户ID和流程状态查询流程实例列表。
     *
     * @param tenantId 租户ID
     * @param status 流程状态
     * @return 流程实例列表
     */
    Mono<List<ProcessInstanceEntity>> findByTenantIdAndStatus(Long tenantId, ProcessStatus status);

    /**
     * 插入流程实例。
     *
     * @param entity 流程实例实体
     * @return 影响的行数
     */
    Mono<Integer> insert(ProcessInstanceEntity entity);

    /**
     * 根据ID更新流程实例。
     *
     * @param entity 流程实例实体
     * @return 影响的行数
     */
    Mono<Integer> updateById(ProcessInstanceEntity entity);

    /**
     * 根据ID删除流程实例。
     *
     * @param id 流程实例ID
     * @return 影响的行数
     */
    Mono<Integer> deleteById(Long id);
}
