package com.apex.scaffold.integration.connector.domain.repository;

import com.apex.scaffold.integration.connector.domain.OutboundCallLog;
import reactor.core.publisher.Mono;

/**
 * 出站调用审计仓储端口（方案 M5 / 批次 E2）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public interface OutboundCallLogRepository {

    /**
     * 落一条出站审计记录（失败同样记录；审计失败不阻断调用结果）。
     *
     * @param log 审计记录
     * @return 落库后的记录
     */
    Mono<OutboundCallLog> insert(OutboundCallLog log);
}
