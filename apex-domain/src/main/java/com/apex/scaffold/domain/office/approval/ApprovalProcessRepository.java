package com.apex.scaffold.domain.office.approval;

import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 审批流程仓储接口
 *
 * <p>定义审批流程聚合根的持久化操作，遵循DDD仓储模式。
 * 所有方法返回Mono类型以支持响应式编程，实现类需要使用BlockingIoSchedulers隔离阻塞I/O操作。
 *
 * @author Apex Scaffold
 * @since 1.0.0
 */
public interface ApprovalProcessRepository {

    /**
     * 保存审批流程
     *
     * @param process 审批流程聚合根
     * @return 保存后的审批流程（包含生成的ID）
     */
    Mono<ApprovalProcess> save(ApprovalProcess process);

    /**
     * 根据ID查询审批流程
     *
     * @param id 审批流程ID
     * @return 审批流程，如果不存在则返回空Mono
     */
    Mono<ApprovalProcess> findById(Long id);

    /**
     * 根据业务流程实例ID查询审批流程
     *
     * @param processInstanceId 业务流程实例ID
     * @return 审批流程，如果不存在则返回空Mono
     */
    Mono<ApprovalProcess> findByProcessInstanceId(String processInstanceId);

    /**
     * 根据平台和平台实例ID查询审批流程
     *
     * @param platform 办公平台类型
     * @param platformInstanceId 平台中的审批实例ID
     * @return 审批流程，如果不存在则返回空Mono
     */
    Mono<ApprovalProcess> findByPlatformAndInstanceId(String platform, String platformInstanceId);

    /**
     * 查询指定发起人的审批流程列表
     *
     * @param initiatorId 发起人ID
     * @return 审批流程列表
     */
    Mono<List<ApprovalProcess>> findByInitiator(String initiatorId);

    /**
     * 查询指定审批人的待审批流程列表
     *
     * @param approverId 审批人ID
     * @return 待审批流程列表
     */
    Mono<List<ApprovalProcess>> findPendingByApprover(String approverId);

    /**
     * 更新审批流程
     *
     * @param process 审批流程聚合根
     * @return 更新后的审批流程
     */
    Mono<ApprovalProcess> update(ApprovalProcess process);

    /**
     * 删除审批流程（逻辑删除）
     *
     * @param id 审批流程ID
     * @return 删除成功返回true，否则返回false
     */
    Mono<Boolean> deleteById(Long id);
}
