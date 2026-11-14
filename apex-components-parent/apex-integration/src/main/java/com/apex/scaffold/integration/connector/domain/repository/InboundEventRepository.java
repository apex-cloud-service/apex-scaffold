package com.apex.scaffold.integration.connector.domain.repository;

import com.apex.scaffold.integration.connector.domain.InboundEvent;
import reactor.core.publisher.Mono;

/**
 * 入站事件仓储端口（方案 M5 / 批次 E2）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public interface InboundEventRepository {

    /**
     * 落一条入站事件（签名失败投递以 {@code signatureValid=0} 记录）。
     *
     * @param event 入站事件
     * @return 落库后的记录
     */
    Mono<InboundEvent> insert(InboundEvent event);

    /**
     * 标记事件已处理。
     *
     * @param tenantId 租户编码
     * @param id       主键
     */
    Mono<Void> markProcessed(String tenantId, Long id);
}
