package com.apex.scaffold.office.domain.approval;

import reactor.core.publisher.Mono;

/**
 * 审批撤销操作编排状态仓储接口
 *
 * <p>记录撤销操作的幂等键与编排状态，用于重复请求重放、远端已撤销但本地落库失败时的补偿，
 * 以及失败后的审计追溯。所有方法返回 Mono，实现类需要使用 {@code BlockingIoSchedulers} 隔离阻塞 I/O。
 *
 * @author Apex Team
 * @since 1.4.0
 */
public interface ApprovalCancelOperationRepository {

    /**
     * 按租户与幂等键查询编排记录
     *
     * @param tenantId 租户ID
     * @param idempotencyKey 幂等键
     * @return 编排记录，不存在时返回空 Mono
     */
    Mono<ApprovalCancelOperation> findByTenantAndKey(Long tenantId, String idempotencyKey);

    /**
     * 新增编排记录
     *
     * @param operation 编排记录
     * @return 保存后的编排记录（包含生成的ID）
     */
    Mono<ApprovalCancelOperation> save(ApprovalCancelOperation operation);

    /**
     * 更新编排记录
     *
     * @param operation 编排记录
     * @return 更新后的编排记录
     */
    Mono<ApprovalCancelOperation> update(ApprovalCancelOperation operation);

    /**
     * 完成撤销：同一同步事务内将审批流程置为已撤销，并将编排记录置为完成
     *
     * @param tenantId 租户ID
     * @param processInstanceId 业务流程实例ID
     * @param operationId 编排记录ID
     * @return 事务内是否全部更新成功（乐观锁冲突返回 false）
     */
    Mono<Boolean> completeCancel(Long tenantId, String processInstanceId, Long operationId);
}
