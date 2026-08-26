package com.apex.scaffold.infrastructure.persistence.service;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.service.AdminReviewAuditPort;
import com.apex.scaffold.infrastructure.persistence.mapper.AdminReviewAuditMapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * JDBC adapter that only exposes append semantics for the review audit table.
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class AdminReviewAuditService implements AdminReviewAuditPort {

    private final AdminReviewAuditMapper mapper;

    /**
     * 追加一条审核审计记录。
     *
     * <p>主键由 {@link com.baomidou.mybatisplus.core.toolkit.IdWorker} 生成，
     * 写入失败（返回行数非 1）时抛出 {@code IllegalStateException} 防止静默丢失审计。</p>
     *
     * @param record 审计记录内容
     * @return 完成信号
     * @throws IllegalStateException 当审计写入影响行数不为 1 时抛出
     */
    @Override
    public Mono<Void> append(AuditRecord record) {
        return Mono.fromCallable(() -> mapper.append(
                        IdWorker.getId(),
                        record.aggregateType(),
                        record.aggregateId(),
                        record.action(),
                        record.actorId(),
                        record.beforeState(),
                        record.afterState(),
                        record.reasonCode(),
                        record.comment(),
                        record.traceId()))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(changed ->
                        changed == 1 ? Mono.<Void>empty() : Mono.error(new IllegalStateException("审核审计写入失败")));
    }
}
