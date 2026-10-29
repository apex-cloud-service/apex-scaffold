package com.apex.scaffold.officeservice.infrastructure.security;

import com.apex.scaffold.common.config.RedisKeyPrefix;
import com.apex.scaffold.domain.service.TokenBlacklistService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 办公服务 Token 黑名单服务（响应式 Redis 实现）。
 *
 * <p>迁移方案 Phase 5：办公服务脱离单体后不再具备 Redisson 客户端，本实现基于
 * {@link ReactiveStringRedisTemplate} 提供与单体等价的 Token 吊销能力，作为
 * {@link TokenBlacklistService} 在办公服务内的自包含实现。</p>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>{@code token:blacklist:{token}} → 占位值 {@code 1}，TTL 与 Token 剩余有效期一致</li>
 * </ul>
 *
 * <p>利用 Redis 键过期机制自动清理，避免黑名单无限增长。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.1
 * @see TokenBlacklistService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfficeTokenBlacklistServiceImpl implements TokenBlacklistService {

    /** 黑名单键名前缀。 */
    private static final String REDIS_KEY_PREFIX_BLACKLIST = "token:blacklist:";

    /** 黑名单占位值。 */
    private static final String BLACKLIST_VALUE = "1";

    /** 黑名单默认过期时间（毫秒），默认 7 天，与 RefreshToken 有效期对齐。 */
    private static final long DEFAULT_BLACKLIST_EXPIRE_MS = 604800000L;

    private final ReactiveStringRedisTemplate redisTemplate;

    private final RedisKeyPrefix redisKeyPrefix;

    /**
     * 将 Token 加入黑名单。
     *
     * <p>{@code expireTime} 为 0 或负数时退化为默认 7 天，避免写入永不过期的键。
     *
     * @param token      Token 字符串
     * @param expireTime 过期时间（毫秒），0 表示使用默认值
     */
    @Override
    public Mono<Void> add(String token, long expireTime) {
        if (token == null || token.isBlank()) {
            return Mono.empty();
        }
        long ttlMillis = expireTime > 0 ? expireTime : DEFAULT_BLACKLIST_EXPIRE_MS;
        return redisTemplate
                .opsForValue()
                .set(blacklistKey(token), BLACKLIST_VALUE, Duration.ofMillis(ttlMillis))
                .then();
    }

    /**
     * 判断 Token 是否在黑名单中。
     *
     * @param token Token 字符串
     * @return true-在黑名单中（已吊销），false-不在黑名单中
     */
    @Override
    public Mono<Boolean> isBlacklisted(String token) {
        if (token == null || token.isBlank()) {
            return Mono.just(false);
        }
        return redisTemplate
                .opsForValue()
                .get(blacklistKey(token))
                .map(value -> value != null)
                .defaultIfEmpty(false);
    }

    /**
     * 构建带版本前缀的黑名单 Redis Key。
     *
     * @param token Token 字符串
     * @return 完整 Redis Key
     */
    private String blacklistKey(String token) {
        return redisKeyPrefix.wrap(REDIS_KEY_PREFIX_BLACKLIST + token);
    }
}
