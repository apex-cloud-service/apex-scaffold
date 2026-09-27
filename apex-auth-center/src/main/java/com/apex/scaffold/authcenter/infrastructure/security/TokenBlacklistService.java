package com.apex.scaffold.authcenter.infrastructure.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Set;

/**
 * Token黑名单服务
 * 
 * 功能：
 * 1. 将已撤销的Token加入黑名单
 * 2. 验证Token是否在黑名单中
 * 3. 支持按用户批量撤销Token
 * 4. 自动清理过期Token（基于Token过期时间）
 * @author Apex Team
 * @since 1.2.0
 */
@Slf4j
@Service
public class TokenBlacklistService {

    private final ReactiveStringRedisTemplate redisTemplate;
    
    // Redis Key前缀
    private static final String BLACKLIST_PREFIX = "token:blacklist:";
    private static final String USER_TOKENS_PREFIX = "token:user:";
    
    public TokenBlacklistService(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 将Token加入黑名单
     * 
     * @param jti Token唯一标识
     * @param userId 用户ID
     * @param ttl Token剩余有效时间
     * @return 是否成功
     */
    public Mono<Boolean> addToBlacklist(String jti, String userId, Duration ttl) {
        String blacklistKey = BLACKLIST_PREFIX + jti;
        String userTokensKey = USER_TOKENS_PREFIX + userId;
        
        return redisTemplate.opsForValue()
            .set(blacklistKey, userId, ttl)
            .flatMap(success -> {
                if (success) {
                    // 将Token添加到用户Token集合中
                    return redisTemplate.opsForSet()
                        .add(userTokensKey, jti)
                        .flatMap(added -> {
                            // 设置用户Token集合的过期时间
                            return redisTemplate.expire(userTokensKey, ttl.plusDays(7))
                                .doOnSuccess(v -> log.info(
                                    "Token已加入黑名单: jti={}, userId={}, ttl={}秒", 
                                    jti, userId, ttl.getSeconds()
                                ));
                        });
                }
                return Mono.just(false);
            });
    }

    /**
     * 检查Token是否在黑名单中
     * 
     * @param jti Token唯一标识
     * @return 是否在黑名单中
     */
    public Mono<Boolean> isBlacklisted(String jti) {
        return redisTemplate.hasKey(BLACKLIST_PREFIX + jti);
    }

    /**
     * 从黑名单移除Token（用于误操作恢复）
     * 
     * @param jti Token唯一标识
     * @return 是否成功
     */
    public Mono<Boolean> removeFromBlacklist(String jti) {
        return redisTemplate.delete(BLACKLIST_PREFIX + jti)
            .map(count -> {
                if (count > 0) {
                    log.info("Token已从黑名单移除: jti={}", jti);
                    return true;
                }
                return false;
            });
    }

    /**
     * 撤销用户的所有Token
     * 
     * @param userId 用户ID
     * @param reason 撤销原因
     * @param ttl Token剩余有效时间（用于黑名单过期）
     * @return 撤销的Token数量
     */
    public Mono<Long> revokeAllUserTokens(String userId, String reason, Duration ttl) {
        String userTokensKey = USER_TOKENS_PREFIX + userId;
        
        return redisTemplate.opsForSet()
            .members(userTokensKey)
            .flatMap(jti -> addToBlacklist(jti, userId, ttl))
            .count()
            .doOnSuccess(count -> {
                if (count > 0) {
                    log.warn("已撤销用户所有Token: userId={}, count={}, reason={}", 
                        userId, count, reason);
                }
            });
    }

    /**
     * 批量将Token加入黑名单
     * 
     * @param jtis Token唯一标识集合
     * @param userId 用户ID
     * @param ttl Token剩余有效时间
     * @return 成功加入黑名单的数量
     */
    public Mono<Long> addBatchToBlacklist(Set<String> jtis, String userId, Duration ttl) {
        return Flux.fromIterable(jtis)
            .flatMap(jti -> addToBlacklist(jti, userId, ttl))
            .filter(success -> success)
            .count()
            .doOnSuccess(count -> log.info(
                "批量加入黑名单完成: userId={}, total={}, success={}", 
                userId, jtis.size(), count
            ));
    }

    /**
     * 获取用户的所有黑名单Token
     * 
     * @param userId 用户ID
     * @return Token JTI集合
     */
    public Flux<String> getUserBlacklistedTokens(String userId) {
        return redisTemplate.opsForSet()
            .members(USER_TOKENS_PREFIX + userId);
    }

    /**
     * 清理用户的过期黑名单记录
     * 
     * @param userId 用户ID
     * @return 清理的数量
     */
    public Mono<Long> cleanupUserExpiredTokens(String userId) {
        String userTokensKey = USER_TOKENS_PREFIX + userId;
        
        return redisTemplate.opsForSet()
            .members(userTokensKey)
            .filterWhen(jti -> redisTemplate.hasKey(BLACKLIST_PREFIX + jti).map(exists -> !exists))
            .flatMap(expiredJti -> redisTemplate.opsForSet().remove(userTokensKey, expiredJti))
            .reduce(0L, Long::sum)
            .doOnSuccess(count -> {
                if (count > 0) {
                    log.debug("已清理用户过期Token: userId={}, count={}", userId, count);
                }
            });
    }

    /**
     * 获取Token黑名单信息
     * 
     * @param jti Token唯一标识
     * @return 黑名单信息
     */
    public Mono<BlacklistInfo> getBlacklistInfo(String jti) {
        return isBlacklisted(jti)
            .flatMap(blacklisted -> {
                if (!blacklisted) {
                    return Mono.just(BlacklistInfo.notBlacklisted());
                }
                
                String blacklistKey = BLACKLIST_PREFIX + jti;
                return Mono.zip(
                    redisTemplate.opsForValue().get(blacklistKey),
                    redisTemplate.getExpire(blacklistKey)
                ).map(tuple -> BlacklistInfo.builder()
                    .blacklisted(true)
                    .userId(tuple.getT1())
                    .remainingTtl(tuple.getT2().getSeconds())
                    .build()
                );
            });
    }

    /**
     * 获取用户黑名单Token统计
     * 
     * @param userId 用户ID
     * @return 统计信息
     */
    public Mono<BlacklistStats> getUserBlacklistStats(String userId) {
        String userTokensKey = USER_TOKENS_PREFIX + userId;
        
        return redisTemplate.opsForSet()
            .size(userTokensKey)
            .flatMap(totalCount -> {
                if (totalCount == 0) {
                    return Mono.just(BlacklistStats.builder()
                        .userId(userId)
                        .totalCount(0L)
                        .activeCount(0L)
                        .expiredCount(0L)
                        .build());
                }
                
                // 统计仍在黑名单中的Token数量
                return redisTemplate.opsForSet()
                    .members(userTokensKey)
                    .filterWhen(jti -> redisTemplate.hasKey(BLACKLIST_PREFIX + jti))
                    .count()
                    .map(activeCount -> BlacklistStats.builder()
                        .userId(userId)
                        .totalCount(totalCount)
                        .activeCount(activeCount)
                        .expiredCount(totalCount - activeCount)
                        .build());
            });
    }

    /**
     * 黑名单信息
     */
    @lombok.Data
    @lombok.Builder
    public static class BlacklistInfo {
        private boolean blacklisted;      // 是否在黑名单中
        private String userId;            // 用户ID
        private Long remainingTtl;        // 剩余TTL（秒）
        
        public static BlacklistInfo notBlacklisted() {
            return BlacklistInfo.builder()
                .blacklisted(false)
                .build();
        }
    }

    /**
     * 黑名单统计信息
     */
    @lombok.Data
    @lombok.Builder
    public static class BlacklistStats {
        private String userId;            // 用户ID
        private Long totalCount;          // 总Token数量
        private Long activeCount;         // 仍在黑名单中的数量
        private Long expiredCount;        // 已过期的数量
    }

    /**
     * 撤销原因枚举
     */
    public enum RevokeReason {
        USER_LOGOUT("用户主动登出"),
        PASSWORD_CHANGED("密码已修改"),
        ACCOUNT_LOCKED("账户被锁定"),
        SECURITY_BREACH("安全事件"),
        TOKEN_COMPROMISED("Token泄露"),
        ADMIN_REVOKED("管理员撤销"),
        EXPIRED("Token过期");
        
        private final String description;
        
        RevokeReason(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
}
