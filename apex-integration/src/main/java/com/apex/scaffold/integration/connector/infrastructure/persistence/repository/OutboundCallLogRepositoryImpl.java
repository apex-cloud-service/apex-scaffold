package com.apex.scaffold.integration.connector.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.integration.connector.domain.OutboundCallLog;
import com.apex.scaffold.integration.connector.domain.repository.OutboundCallLogRepository;
import com.apex.scaffold.integration.connector.infrastructure.persistence.mapper.OutboundCallLogMapper;
import com.apex.scaffold.integration.connector.infrastructure.persistence.po.OutboundCallLogPO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * {@link OutboundCallLogRepository} 的 MyBatis-Plus 实现（方案 M5 / 批次 E2）。
 *
 * <p>审计失败仅记录告警日志，不阻断业务调用结果（审计为旁路）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class OutboundCallLogRepositoryImpl implements OutboundCallLogRepository {

    private final OutboundCallLogMapper mapper;

    @Override
    public Mono<OutboundCallLog> insert(OutboundCallLog entry) {
        return Mono.fromCallable(() -> {
                    OutboundCallLogPO po = new OutboundCallLogPO();
                    po.setTenantId(entry.getTenantId());
                    po.setConnectorType(entry.getConnectorType());
                    po.setOperation(entry.getOperation());
                    po.setIdempotencyKey(entry.getIdempotencyKey());
                    po.setStatus(entry.getStatus());
                    po.setDurationMs(entry.getDurationMs());
                    po.setErrorMessage(entry.getErrorMessage());
                    mapper.insert(po);
                    entry.setId(po.getId());
                    entry.setCreateTime(po.getCreateTime());
                    return entry;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .onErrorResume(e -> {
                    log.warn("出站审计落库失败: type={}, operation={}", entry.getConnectorType(), entry.getOperation(), e);
                    return Mono.just(entry);
                });
    }
}
