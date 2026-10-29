package com.apex.scaffold.auth.domain.service;

import java.time.Duration;
import reactor.core.publisher.Mono;

/**
 * 恢复凭证存取端口（短期验证码与确认令牌）。
 *
 * <p>凭证只保存摘要并与恢复单绑定用途，TTL 到期自动失效；
 * 校验与消费必须原子完成，保证并发下最多一次成功。</p>
 *
 * @author Apex Security Team
 * @since 1.0.0
 */
public interface RecoveryCredentialStore {

    /** 存储验证码摘要，覆盖同恢复单旧值。 */
    Mono<Boolean> storeCode(String recoveryId, String codeHash, Duration ttl);

    /** 原子校验并消费验证码摘要；摘要不匹配时返回 false 且不删除。 */
    Mono<Boolean> verifyAndConsumeCode(String recoveryId, String codeHash);

    /** 存储确认令牌摘要，覆盖同恢复单旧值。 */
    Mono<Boolean> storeConfirmToken(String recoveryId, String tokenHash, Duration ttl);

    /** 原子消费确认令牌摘要；摘要不匹配时返回 false 且不删除。 */
    Mono<Boolean> consumeConfirmToken(String recoveryId, String tokenHash);

    /** 固定窗口限流：窗口内首次计数时设置过期时间，返回是否仍在限额内。 */
    Mono<Boolean> tryAcquireRateLimit(String bucketKey, int limit, Duration window);
}
