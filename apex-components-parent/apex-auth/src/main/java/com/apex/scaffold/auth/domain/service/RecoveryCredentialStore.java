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

    /**
     * 存储验证码摘要，覆盖同恢复单旧值。
     *
     * @param recoveryId 恢复单主键
     * @param codeHash 验证码摘要
     * @param ttl 有效期
     * @return 是否写入成功
     */
    Mono<Boolean> storeCode(String recoveryId, String codeHash, Duration ttl);

    /**
     * 原子校验并消费验证码摘要；摘要不匹配时返回 false 且不删除。
     *
     * @param recoveryId 恢复单主键
     * @param codeHash 待校验的验证码摘要
     * @return 摘要匹配并成功消费时为true，不匹配时为false
     */
    Mono<Boolean> verifyAndConsumeCode(String recoveryId, String codeHash);

    /**
     * 存储确认令牌摘要，覆盖同恢复单旧值。
     *
     * @param recoveryId 恢复单主键
     * @param tokenHash 确认令牌摘要
     * @param ttl 有效期
     * @return 是否写入成功
     */
    Mono<Boolean> storeConfirmToken(String recoveryId, String tokenHash, Duration ttl);

    /**
     * 原子消费确认令牌摘要；摘要不匹配时返回 false 且不删除。
     *
     * @param recoveryId 恢复单主键
     * @param tokenHash 待校验的确认令牌摘要
     * @return 摘要匹配并成功消费时为true，不匹配时为false
     */
    Mono<Boolean> consumeConfirmToken(String recoveryId, String tokenHash);

    /**
     * 固定窗口限流：窗口内首次计数时设置过期时间，返回是否仍在限额内。
     *
     * @param bucketKey 限流桶键
     * @param limit 窗口内允许的次数上限
     * @param window 固定窗口长度
     * @return 仍在限额内时为true，超过限额时为false
     */
    Mono<Boolean> tryAcquireRateLimit(String bucketKey, int limit, Duration window);
}
