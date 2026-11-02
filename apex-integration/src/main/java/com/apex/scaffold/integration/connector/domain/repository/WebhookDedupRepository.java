package com.apex.scaffold.integration.connector.domain.repository;

import java.time.LocalDateTime;
import reactor.core.publisher.Mono;

/**
 * 入站 webhook 幂等去重仓储端口（方案 M5 / 批次 E2）。
 *
 * <p>以 {@code (tenant_id, dedup_key)} 唯一键实现投递幂等：
 * 唯一键冲突即重复投递，调用方直接幂等返回成功。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public interface WebhookDedupRepository {

    /**
     * 尝试登记去重键。
     *
     * @param tenantId  租户编码
     * @param dedupKey  去重键（签名+时间戳+事件ID 摘要）
     * @param expireAt  过期时间（过期行可由维护任务清理）
     * @return true-首次投递；false-重复投递（唯一键冲突）
     */
    Mono<Boolean> tryInsert(String tenantId, String dedupKey, LocalDateTime expireAt);
}
