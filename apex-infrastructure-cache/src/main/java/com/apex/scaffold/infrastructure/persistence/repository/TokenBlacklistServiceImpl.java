package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.util.JwtUtil;
import com.apex.scaffold.common.util.SensitiveDataUtil;
import com.apex.scaffold.domain.service.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.time.Duration;
import java.util.Date;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Token 黑名单服务实现类
 *
 * <p>基于 Redis 实现 Token 黑名单管理，提供退出登录、强制下线等场景下的 Token 注销能力。
 * 使用 Token 的 jti（JWT ID）作为 Redis 键，利用 Redis 的 TTL 自动过期能力，
 * 无需额外的定时任务清理过期数据。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>键格式：token:blacklist:{jti}</li>
 *   <li>值固定为 "1"，仅作标记位使用</li>
 *   <li>TTL 根据 Token 的过期时间动态计算，确保黑名单记录在 Token 自然过期后自动清理</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 */
@Slf4j
@Service
@lombok.RequiredArgsConstructor
public class TokenBlacklistServiceImpl implements TokenBlacklistService {

    /** Redis 键前缀 */
    private static final String BLACKLIST_PREFIX = "token:blacklist:";

    /** 身份级吊销标记前缀；subject 只以摘要形式进入 Redis 键。 */
    private static final String SUBJECT_REVOKED_PREFIX = "token:blacklist:subject:";

    /** Redis 响应式字符串模板 */
    private final ReactiveStringRedisTemplate redisTemplate;

    /**
     * 将 Token 加入黑名单
     *
     * <p>解析 Token 获取 jti（JWT ID），以 "token:blacklist:{jti}" 为键存入 Redis，
     * TTL 设置为 Token 的剩余有效期。Token 自然过期后，Redis 自动清理对应黑名单记录。
     *
     * @param token Token 字符串
     * @param expireTime 过期时间（毫秒），此参数保留用于接口兼容，实际以 Token 自身过期时间为准
     */
    @Override
    public Mono<Void> add(String token, long expireTime) {
        try {
            Claims claims = JwtUtil.parseToken(token);
            String jti = claims.getId();
            if (jti == null || jti.isBlank()) {
                log.warn("Token中未包含jti字段，无法加入黑名单");
                return Mono.empty();
            }
            String key = BLACKLIST_PREFIX + jti;
            Date expiration = claims.getExpiration();
            long remainingTtl = expiration.getTime() - System.currentTimeMillis();
            if (remainingTtl <= 0) {
                log.warn("Token已过期，无需加入黑名单, jti: {}", jti);
                return Mono.empty();
            }
            return redisTemplate
                    .opsForValue()
                    .set(key, "1", Duration.ofMillis(remainingTtl))
                    .doOnSuccess(success -> {
                        if (Boolean.TRUE.equals(success)) {
                            log.debug("Token已加入黑名单, jti: {}, remainingTtl: {}ms", jti, remainingTtl);
                        }
                    })
                    .then();
        } catch (ExpiredJwtException e) {
            log.debug("Token已过期，无需加入黑名单: {}", e.getMessage());
            return Mono.empty();
        } catch (Exception e) {
            log.error("Token加入黑名单失败: {}", e.getMessage(), e);
            return Mono.empty();
        }
    }

    /**
     * 判断 Token 是否已加入黑名单
     *
     * <p>从 Token 中解析 jti，查询 Redis 中是否存在对应的黑名单记录。
     *
     * @param token Token 字符串
     * @return true-在黑名单中，false-不在黑名单中
     */
    @Override
    public Mono<Boolean> isBlacklisted(String token) {
        try {
            Claims claims = JwtUtil.parseToken(token);
            String jti = claims.getId();
            if (jti == null || jti.isBlank()) {
                return Mono.just(false);
            }
            String key = BLACKLIST_PREFIX + jti;
            return redisTemplate.opsForValue().get(key).map(Objects::nonNull).defaultIfEmpty(false);
        } catch (ExpiredJwtException e) {
            log.debug("Token已过期，视为不在黑名单: {}", e.getMessage());
            return Mono.just(false);
        } catch (JwtException e) {
            log.debug("Token无效，视为不在黑名单: {}", e.getMessage());
            return Mono.just(false);
        } catch (Exception e) {
            log.error("检查Token黑名单状态失败: {}", e.getMessage(), e);
            return Mono.just(false);
        }
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Void> revokeSubject(String subject, long expireTime) {
        if (subject == null || subject.isBlank()) {
            return Mono.empty();
        }
        long ttl = expireTime > 0 ? expireTime : Duration.ofDays(7).toMillis();
        String key = subjectRevokedKey(subject);
        // 记录毫秒级时间戳；鉴权时只比较令牌 iat，不存储手机号或 openId 明文。
        return redisTemplate
                .opsForValue()
                .set(key, String.valueOf(System.currentTimeMillis()), Duration.ofMillis(ttl))
                .then();
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Boolean> isSubjectRevoked(String subject, Date issuedAt) {
        if (subject == null || subject.isBlank() || issuedAt == null) {
            return Mono.just(false);
        }
        return redisTemplate
                .opsForValue()
                .get(subjectRevokedKey(subject))
                .map(value -> isIssuedBeforeRevocation(value, issuedAt))
                .defaultIfEmpty(false)
                .onErrorResume(error -> {
                    // Redis 故障时不把所有请求误判为注销；用户行的 deleted/status 仍是第二道门。
                    log.error("检查身份级Token吊销状态失败: {}", error.getMessage(), error);
                    return Mono.just(false);
                });
    }

    /**
     * 构造身份级吊销标记的 Redis 键。
     *
     * <p>subject 先经指纹摘要（截取 {@code sha256:} 前缀之后的十六进制部分）
     * 再进入键名，避免原始身份信息出现在 Redis 键中。</p>
     *
     * @param subject 待吊销的身份标识
     * @return Redis 键
     */
    private String subjectRevokedKey(String subject) {
        return SUBJECT_REVOKED_PREFIX + SensitiveDataUtil.fingerprint(subject).substring("sha256:".length());
    }

    /**
     * 判断 Token 签发时间是否早于身份吊销时间。
     *
     * <p>吊销标记值为吊销时刻的毫秒时间戳；签发时间更早即视为已吊销。
     * 标记格式非法时按未吊销处理并记录警告。</p>
     *
     * @param value    吊销标记原始值
     * @param issuedAt Token 签发时间
     * @return 已吊销返回 {@code true}，否则返回 {@code false}
     */
    private boolean isIssuedBeforeRevocation(String value, Date issuedAt) {
        try {
            return issuedAt.getTime() < Long.parseLong(value);
        } catch (NumberFormatException error) {
            log.warn("身份级Token吊销标记格式异常");
            return false;
        }
    }
}
