package com.apex.scaffold.auth.infrastructure.security;

import com.apex.scaffold.auth.domain.service.RecoveryCredentialStore;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 恢复凭证存取实现（Redis）。
 *
 * <p>验证码与确认令牌只存 SHA-256 摘要，键绑定恢复单 ID（用途绑定），TTL 到期自动失效；
 * 校验与消费使用 Lua 脚本原子完成（比较后删除），保证并发下最多一次成功；
 * 限流使用固定窗口计数（Lua 原子递增 + 首次设置过期）。</p>
 *
 * @author Apex Security Team
 * @since 1.0.0
 */
@Component
@RequiredArgsConstructor
public class RecoveryCredentialStoreImpl implements RecoveryCredentialStore {

    private static final String CODE_KEY_PREFIX = "auth:pwd-recovery:code:";

    private static final String CONFIRM_KEY_PREFIX = "auth:pwd-recovery:confirm:";

    /** 原子比较删除：值匹配才删除并返回 1，否则返回 0。 */
    private static final DefaultRedisScript<Long> COMPARE_AND_DELETE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end",
            Long.class);

    /** 固定窗口限流：首次计数设置过期时间，超出限额返回 0。 */
    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>(
            "local current = redis.call('INCR', KEYS[1]) "
                    + "if current == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[2]) end "
                    + "if current <= tonumber(ARGV[1]) then return 1 else return 0 end",
            Long.class);

    private final ReactiveStringRedisTemplate redisTemplate;

    @Override
    public Mono<Boolean> storeCode(String recoveryId, String codeHash, Duration ttl) {
        return redisTemplate.opsForValue().set(CODE_KEY_PREFIX + recoveryId, codeHash, ttl);
    }

    @Override
    public Mono<Boolean> verifyAndConsumeCode(String recoveryId, String codeHash) {
        return compareAndDelete(CODE_KEY_PREFIX + recoveryId, codeHash);
    }

    @Override
    public Mono<Boolean> storeConfirmToken(String recoveryId, String tokenHash, Duration ttl) {
        return redisTemplate.opsForValue().set(CONFIRM_KEY_PREFIX + recoveryId, tokenHash, ttl);
    }

    @Override
    public Mono<Boolean> consumeConfirmToken(String recoveryId, String tokenHash) {
        return compareAndDelete(CONFIRM_KEY_PREFIX + recoveryId, tokenHash);
    }

    @Override
    public Mono<Boolean> tryAcquireRateLimit(String bucketKey, int limit, Duration window) {
        return redisTemplate
                .execute(
                        RATE_LIMIT_SCRIPT, List.of(bucketKey), String.valueOf(limit), String.valueOf(window.toMillis()))
                .next()
                .map(result -> result != null && result == 1L)
                .defaultIfEmpty(Boolean.FALSE);
    }

    private Mono<Boolean> compareAndDelete(String key, String expectedHash) {
        return redisTemplate
                .execute(COMPARE_AND_DELETE_SCRIPT, List.of(key), expectedHash)
                .next()
                .map(result -> result != null && result == 1L)
                .defaultIfEmpty(Boolean.FALSE);
    }
}
