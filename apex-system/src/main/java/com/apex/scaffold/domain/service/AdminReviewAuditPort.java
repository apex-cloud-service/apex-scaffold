package com.apex.scaffold.domain.service;

import reactor.core.publisher.Mono;

/**
 * 只追加的管理端审核操作审计端口。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface AdminReviewAuditPort {

    /**
     * 追加一条审计记录。
     *
     * @param record 待追加的审计记录
     */
    Mono<Void> append(AuditRecord record);

    /**
     * 一条管理审核操作的审计记录。
     *
     * @param aggregateType 被审核对象的聚合类型
     * @param aggregateId 被审核对象标识
     * @param action 审核动作
     * @param actorId 操作人标识
     * @param beforeState 变更前状态摘要
     * @param afterState 变更后状态摘要
     * @param reasonCode 选择的审核原因编码
     * @param comment 审核备注
     * @param traceId 链路追踪标识
     * @author Apex Team
     * @version 1.0.0
     * @since 1.0.0
     */
    record AuditRecord(
            String aggregateType,
            String aggregateId,
            String action,
            String actorId,
            String beforeState,
            String afterState,
            String reasonCode,
            String comment,
            String traceId) {}
}
