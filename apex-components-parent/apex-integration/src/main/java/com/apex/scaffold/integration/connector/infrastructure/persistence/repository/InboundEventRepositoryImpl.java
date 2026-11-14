package com.apex.scaffold.integration.connector.infrastructure.persistence.repository;

import com.apex.scaffold.common.persistence.util.ConditionUtil;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.integration.connector.domain.InboundEvent;
import com.apex.scaffold.integration.connector.domain.repository.InboundEventRepository;
import com.apex.scaffold.integration.connector.infrastructure.persistence.mapper.InboundEventMapper;
import com.apex.scaffold.integration.connector.infrastructure.persistence.po.InboundEventPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * {@link InboundEventRepository} 的 MyBatis-Plus 实现（方案 M5 / 批次 E2）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Repository
@RequiredArgsConstructor
public class InboundEventRepositoryImpl implements InboundEventRepository {

    private final InboundEventMapper mapper;

    @Override
    public Mono<InboundEvent> insert(InboundEvent event) {
        return Mono.fromCallable(() -> {
                    InboundEventPO po = new InboundEventPO();
                    po.setTenantId(event.getTenantId());
                    po.setConnectorType(event.getConnectorType());
                    po.setEventType(event.getEventType());
                    po.setPayload(event.getPayload());
                    po.setSignatureValid(event.getSignatureValid() == null ? 0 : event.getSignatureValid());
                    po.setProcessed(event.getProcessed() == null ? 0 : event.getProcessed());
                    mapper.insert(po);
                    event.setId(po.getId());
                    event.setCreateTime(po.getCreateTime());
                    return event;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Void> markProcessed(String tenantId, Long id) {
        return Mono.fromCallable(() -> {
                    InboundEventPO patch = new InboundEventPO();
                    patch.setProcessed(1);
                    return mapper.update(
                            patch,
                            ConditionUtil.lambda(InboundEventPO.class)
                                    .eq(InboundEventPO::getTenantId, tenantId)
                                    .eq(InboundEventPO::getId, id));
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .then();
    }
}
