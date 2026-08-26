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
     * @return 追加完成信号
     */
    Mono<Void> append(AuditRecord record);

    /**
     * 一条管理审核操作的审计记录。
     *
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
