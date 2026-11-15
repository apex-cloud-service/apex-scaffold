package com.apex.scaffold.officeservice.infrastructure.security;

import com.apex.scaffold.common.config.RedisKeyPrefix;
import com.apex.scaffold.common.dto.TokenStoreEntry;
import com.apex.scaffold.common.util.JsonUtil;
import com.apex.scaffold.common.util.JwtUtil;
import com.apex.scaffold.domain.service.TokenBlacklistService;
import com.apex.scaffold.domain.service.TokenService;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 办公服务 Token 服务实现（响应式 Redis 自包含实现）。
 *
 * <p>迁移方案 Phase 5：办公服务作为独立微服务运行后，无法复用单体
 * {@code apex-infrastructure} 中基于 Redisson 的 {@code TokenServiceImpl}。
 * 本实现基于 {@link ReactiveStringRedisTemplate} 重建等价的
 * {@link TokenService} 能力，成为办公服务内的自包含 Bean，满足
 * {@code OfficeApplicationService} 登录签发/登出吊销链路。</p>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li><b>JWT 生成/解析</b>：复用共享 {@link JwtUtil}，claims 语义一致
 *       （{@code type}/{@code deviceId}/{@code sid}/{@code clientType}）</li>
 *   <li><b>键值结构</b>：{@code token:access:{subject}}、{@code token:refresh:{subject}}
 *       及多会话槽 {@code ...:{sid}}、会话索引 {@code token:sessions:{subject}}</li>
 *   <li><b>兼容性</b>：同时兼容纯 JWT 字符串与 {@link TokenStoreEntry} JSON 两种存储格式</li>
 *   <li><b>黑名单</b>：复用 {@link TokenBlacklistService} 校验令牌吊销状态</li>
 * </ul>
 *
 * <h2>线程安全性</h2>
 * <p>单体实现的刷新令牌轮换使用 Redisson 分布式锁（阻塞式）；本实现改为
 * 基于 {@code SET NX PX} 的响应式自旋锁（{@code setIfAbsent}），在非阻塞链路上
 * 完成"读旧令牌 → 比对 → 写新令牌对 → 拉黑旧令牌"的原子轮换，杜绝刷新令牌并发重用。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.1
 * @see TokenService
 * @see TokenBlacklistService
 * @see JwtUtil
 */
@Slf4j
@Service
public class OfficeTokenServiceImpl implements TokenService {

    private static final String JSON_START_BRACE = "{";
    private static final String JWT_CLAIM_TYPE = "type";
    private static final String JWT_CLAIM_SUB = "sub";
    private static final String JWT_CLAIM_SID = "sid";
    private static final String JWT_CLAIM_CLIENT_TYPE = "clientType";

    /** AccessToken 的 JWT claims 中 type 字段值。 */
    private static final String TOKEN_TYPE_ACCESS = "access";

    /** RefreshToken 的 JWT claims 中 type 字段值。 */
    private static final String TOKEN_TYPE_REFRESH = "refresh";

    /** Redis 中 AccessToken 的键名前缀。 */
    private static final String REDIS_KEY_PREFIX_ACCESS = "token:access:";

    /** Redis 中 RefreshToken 的键名前缀。 */
    private static final String REDIS_KEY_PREFIX_REFRESH = "token:refresh:";

    /** Redis 中会话索引（Set）的键名前缀。 */
    private static final String REDIS_KEY_PREFIX_SESSIONS = "token:sessions:";

    /** 刷新轮换锁键名前缀。 */
    private static final String REDIS_KEY_PREFIX_ROTATE_LOCK = "token:refresh:rotate:";

    /** 刷新轮换锁的持有时长（毫秒）。 */
    private static final long REFRESH_LOCK_LEASE_MS = 10000L;

    /** AccessToken 默认过期时间（毫秒），默认 2 小时。 */
    private static final long DEFAULT_ACCESS_TOKEN_EXPIRE_MS = 7200000L;

    /** RefreshToken 默认过期时间（毫秒），默认 7 天。 */
    private static final long DEFAULT_REFRESH_TOKEN_EXPIRE_MS = 604800000L;

    /** AccessToken 过期时间（毫秒），默认 2 小时。 */
    private final long accessTokenExpireTime;

    /** RefreshToken 过期时间（毫秒），默认 7 天。 */
    private final long refreshTokenExpireTime;

    private final ReactiveStringRedisTemplate redisTemplate;

    private final RedisKeyPrefix redisKeyPrefix;

    private final TokenBlacklistService tokenBlacklistService;

    /**
     * 构造方法，注入办公服务 Token 管理所需的依赖。
     *
     * @param redisTemplate          响应式 Redis 客户端
     * @param redisKeyPrefix         Redis Key 版本前缀管理器
     * @param tokenBlacklistService  Token 黑名单服务
     * @param accessTokenExpireTime  AccessToken 过期时间（毫秒）
     * @param refreshTokenExpireTime RefreshToken 过期时间（毫秒）
     */
    public OfficeTokenServiceImpl(
            ReactiveStringRedisTemplate redisTemplate,
            RedisKeyPrefix redisKeyPrefix,
            TokenBlacklistService tokenBlacklistService,
            @Value("${jwt.access-token-expire:" + DEFAULT_ACCESS_TOKEN_EXPIRE_MS + "}") long accessTokenExpireTime,
            @Value("${jwt.refresh-token-expire:" + DEFAULT_REFRESH_TOKEN_EXPIRE_MS + "}") long refreshTokenExpireTime) {
        this.redisTemplate = redisTemplate;
        this.redisKeyPrefix = redisKeyPrefix;
        this.tokenBlacklistService = tokenBlacklistService;
        this.accessTokenExpireTime = accessTokenExpireTime;
        this.refreshTokenExpireTime = refreshTokenExpireTime;
    }

    /**
     * 构建带版本前缀的 AccessToken Redis Key。
     */
    private String accessKey(String subject) {
        return redisKeyPrefix.wrap(REDIS_KEY_PREFIX_ACCESS + subject);
    }

    /**
     * 构建带版本前缀的 RefreshToken Redis Key。
     */
    private String refreshKey(String subject) {
        return redisKeyPrefix.wrap(REDIS_KEY_PREFIX_REFRESH + subject);
    }

    /**
     * 构建多会话模型的 AccessToken Redis Key（{@code token:access:{subject}:{sid}}）。
     */
    private String sessionAccessKey(String subject, String sid) {
        return redisKeyPrefix.wrap(REDIS_KEY_PREFIX_ACCESS + subject + ":" + sid);
    }

    /**
     * 构建多会话模型的 RefreshToken Redis Key（{@code token:refresh:{subject}:{sid}}）。
     */
    private String sessionRefreshKey(String subject, String sid) {
        return redisKeyPrefix.wrap(REDIS_KEY_PREFIX_REFRESH + subject + ":" + sid);
    }

    /**
     * 构建用户会话索引 Redis Key（{@code token:sessions:{subject}}，Set 结构）。
     */
    private String sessionsKey(String subject) {
        return redisKeyPrefix.wrap(REDIS_KEY_PREFIX_SESSIONS + subject);
    }

    /**
     * 判断会话ID是否有效（非空且非空白）。
     */
    private boolean hasSid(String sid) {
        return sid != null && !sid.isBlank();
    }

    /**
     * 构建刷新令牌轮换使用的响应式锁 Key。
     *
     * <p>多会话模型下按会话隔离（{@code ...:{subject}:{sid}}），不同端刷新互不阻塞；
     * 无 sid 时按用户维度隔离。</p>
     */
    private String refreshLockKey(String subject, String sid) {
        return hasSid(sid)
                ? redisKeyPrefix.wrap(REDIS_KEY_PREFIX_ROTATE_LOCK + subject + ":" + sid)
                : redisKeyPrefix.wrap(REDIS_KEY_PREFIX_ROTATE_LOCK + subject);
    }

    @Override
    public String generateAccessToken(String subject, String deviceId) {
        return generateAccessToken(subject, deviceId, null, null);
    }

    @Override
    public String generateAccessToken(String subject, String deviceId, String sid, String clientType) {
        Map<String, Object> claims = new HashMap<>(8);
        claims.put(JWT_CLAIM_TYPE, TOKEN_TYPE_ACCESS);
        if (deviceId != null && !deviceId.isBlank()) {
            claims.put("deviceId", deviceId);
        }
        if (hasSid(sid)) {
            claims.put(JWT_CLAIM_SID, sid);
        }
        if (clientType != null && !clientType.isBlank()) {
            claims.put(JWT_CLAIM_CLIENT_TYPE, clientType);
        }
        return JwtUtil.generateToken(subject, claims, accessTokenExpireTime);
    }

    @Override
    public String generateRefreshToken(String subject, String deviceId) {
        return generateRefreshToken(subject, deviceId, null, null);
    }

    @Override
    public String generateRefreshToken(String subject, String deviceId, String sid, String clientType) {
        Map<String, Object> claims = new HashMap<>(8);
        claims.put(JWT_CLAIM_TYPE, TOKEN_TYPE_REFRESH);
        if (deviceId != null && !deviceId.isBlank()) {
            claims.put("deviceId", deviceId);
        }
        if (hasSid(sid)) {
            claims.put(JWT_CLAIM_SID, sid);
        }
        if (clientType != null && !clientType.isBlank()) {
            claims.put(JWT_CLAIM_CLIENT_TYPE, clientType);
        }
        return JwtUtil.generateToken(subject, claims, refreshTokenExpireTime);
    }

    @Override
    public Map<String, Object> parseToken(String token) {
        return JwtUtil.parseToken(token);
    }

    @Override
    public Mono<Boolean> validateToken(String token) {
        try {
            JwtUtil.parseToken(token);
            return tokenBlacklistService.isBlacklisted(token).map(inBlacklist -> !inBlacklist);
        } catch (Exception e) {
            return Mono.just(false);
        }
    }

    @Override
    public Mono<Void> storeToken(String subject, String accessToken, String refreshToken) {
        return storeToken(subject, accessToken, refreshToken, null, null);
    }

    @Override
    public Mono<Void> storeToken(
            String subject,
            String accessToken,
            String refreshToken,
            TokenStoreEntry accessEntry,
            TokenStoreEntry refreshEntry) {
        return storeSessionToken(subject, null, accessToken, refreshToken, accessEntry, refreshEntry);
    }

    @Override
    public Mono<Void> storeSessionToken(
            String subject,
            String sid,
            String accessToken,
            String refreshToken,
            TokenStoreEntry accessEntry,
            TokenStoreEntry refreshEntry) {
        if (!hasSid(sid)) {
            return storeTokenLegacy(subject, accessToken, refreshToken, accessEntry, refreshEntry);
        }
        String accessValue = buildStoreValue(accessToken, accessEntry);
        String refreshValue = buildStoreValue(refreshToken, refreshEntry);
        Mono<Void> pair = redisTemplate
                .opsForValue()
                .set(sessionAccessKey(subject, sid), accessValue, Duration.ofMillis(accessTokenExpireTime))
                .zipWith(redisTemplate
                        .opsForValue()
                        .set(sessionRefreshKey(subject, sid), refreshValue, Duration.ofMillis(refreshTokenExpireTime)))
                .then();
        Mono<Void> index = redisTemplate
                .opsForSet()
                .add(sessionsKey(subject), sid)
                .zipWith(redisTemplate.expire(sessionsKey(subject), Duration.ofMillis(refreshTokenExpireTime)))
                .then();
        return pair.then(index);
    }

    /**
     * 存储 Token 对到 Redis（legacy 单会话槽，旧版行为）。
     */
    private Mono<Void> storeTokenLegacy(
            String subject,
            String accessToken,
            String refreshToken,
            TokenStoreEntry accessEntry,
            TokenStoreEntry refreshEntry) {
        String accessValue = buildStoreValue(accessToken, accessEntry);
        String refreshValue = buildStoreValue(refreshToken, refreshEntry);
        return redisTemplate
                .opsForValue()
                .set(accessKey(subject), accessValue, Duration.ofMillis(accessTokenExpireTime))
                .zipWith(redisTemplate
                        .opsForValue()
                        .set(refreshKey(subject), refreshValue, Duration.ofMillis(refreshTokenExpireTime)))
                .then();
    }

    /**
     * 构建 Redis 存储值：若 entry 非空则序列化为 JSON，否则回退为纯 Token 字符串。
     *
     * @param token JWT Token 字符串
     * @param entry 用户信息条目，可为 null
     * @return Redis 存储值
     */
    private String buildStoreValue(String token, TokenStoreEntry entry) {
        if (entry == null) {
            return token;
        }
        entry.setToken(token);
        return JsonUtil.toJson(entry);
    }

    @Override
    public Mono<Void> storeAccessToken(String subject, String accessToken, int ttlSeconds) {
        return redisTemplate
                .opsForValue()
                .set(accessKey(subject), accessToken, Duration.ofSeconds(ttlSeconds))
                .then();
    }

    @Override
    public Mono<Void> storeSessionAccessToken(String subject, String sid, String accessToken, int ttlSeconds) {
        if (!hasSid(sid)) {
            return storeAccessToken(subject, accessToken, ttlSeconds);
        }
        return redisTemplate
                .opsForValue()
                .set(sessionAccessKey(subject, sid), accessToken, Duration.ofSeconds(ttlSeconds))
                .then();
    }

    @Override
    public Mono<Boolean> validateRefreshToken(String subject, String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return Mono.just(false);
        }
        return tokenBlacklistService.isBlacklisted(refreshToken).flatMap(inBlacklist -> {
            if (inBlacklist) {
                return Mono.just(false);
            }
            try {
                Map<String, Object> claims = JwtUtil.parseToken(refreshToken);
                String tokenType = (String) claims.get(JWT_CLAIM_TYPE);
                if (!TOKEN_TYPE_REFRESH.equals(tokenType)) {
                    return Mono.just(false);
                }
                String tokenSubject = (String) claims.get(JWT_CLAIM_SUB);
                if (tokenSubject == null || !tokenSubject.equals(subject)) {
                    return Mono.just(false);
                }
                if (JwtUtil.isTokenExpired(refreshToken)) {
                    return Mono.just(false);
                }
                String sid = (String) claims.get(JWT_CLAIM_SID);
                String bucketKey = hasSid(sid) ? sessionRefreshKey(subject, sid) : refreshKey(subject);
                return redisTemplate
                        .opsForValue()
                        .get(bucketKey)
                        .map(storedValue -> tokenMatches(storedValue, refreshToken))
                        .defaultIfEmpty(false);
            } catch (Exception e) {
                return Mono.just(false);
            }
        });
    }

    /**
     * 判断 Redis 中存储的值是否与传入的 Token 匹配（兼容纯字符串与 JSON 两种格式）。
     *
     * @param storedValue Redis 中存储的值
     * @param token       待比较的 Token 字符串
     * @return true-匹配，false-不匹配
     */
    private boolean tokenMatches(String storedValue, String token) {
        if (storedValue == null) {
            return false;
        }
        if (storedValue.startsWith(JSON_START_BRACE)) {
            try {
                TokenStoreEntry entry = JsonUtil.fromJson(storedValue, TokenStoreEntry.class);
                return entry != null && token.equals(entry.getToken());
            } catch (Exception e) {
                log.warn("解析 Redis Token JSON 失败，回退为字符串比较: {}", e.getMessage());
                return token.equals(storedValue);
            }
        }
        return token.equals(storedValue);
    }

    @Override
    public Mono<Boolean> rotateRefreshToken(
            String subject,
            String oldRefreshToken,
            String newAccessToken,
            String newRefreshToken,
            TokenStoreEntry accessEntry,
            TokenStoreEntry refreshEntry) {
        String[] sidHolder = new String[1];
        if (!isEligibleForRotation(subject, oldRefreshToken, sidHolder)) {
            return Mono.just(false);
        }
        String sid = sidHolder[0];
        String lockKey = refreshLockKey(subject, sid);
        return redisTemplate
                .opsForValue()
                .setIfAbsent(lockKey, "1", Duration.ofMillis(REFRESH_LOCK_LEASE_MS))
                .flatMap(acquired -> {
                    if (!Boolean.TRUE.equals(acquired)) {
                        log.warn("刷新令牌轮换获取分布式锁失败(并发刷新), subject={}", subject);
                        return Mono.just(false);
                    }
                    return doRotateRefreshToken(
                                    subject,
                                    sid,
                                    oldRefreshToken,
                                    newAccessToken,
                                    newRefreshToken,
                                    accessEntry,
                                    refreshEntry)
                            .onErrorResume(e -> {
                                log.warn("刷新令牌轮换异常, subject={}, error={}", subject, e.getMessage());
                                return Mono.just(false);
                            })
                            .flatMap(result -> redisTemplate.delete(lockKey).thenReturn(result));
                })
                .defaultIfEmpty(false);
    }

    /**
     * 刷新令牌轮换资格校验（纯内存、无 I/O，在调用线程同步执行）。
     *
     * <p>依次校验：令牌非空、JWT 类型为 refresh、主题与用户一致、未过期；
     * 校验通过时同步提取旧令牌 claims 中的 {@code sid} 供轮换按会话槽隔离。</p>
     *
     * @param subject         用户唯一标识（OpenID）
     * @param oldRefreshToken 待轮换的旧刷新令牌
     * @param sidOut          会话ID输出容器（长度 1 数组），解析结果写入下标 0；可为 null
     * @return 通过校验返回 true，否则返回 false
     */
    private boolean isEligibleForRotation(String subject, String oldRefreshToken, String[] sidOut) {
        if (subject == null || subject.isBlank() || oldRefreshToken == null || oldRefreshToken.isBlank()) {
            return false;
        }
        try {
            Map<String, Object> claims = JwtUtil.parseToken(oldRefreshToken);
            if (!TOKEN_TYPE_REFRESH.equals(claims.get(JWT_CLAIM_TYPE))) {
                return false;
            }
            if (!subject.equals(claims.get(JWT_CLAIM_SUB))) {
                return false;
            }
            if (JwtUtil.isTokenExpired(oldRefreshToken)) {
                return false;
            }
            if (sidOut != null && sidOut.length > 0) {
                sidOut[0] = (String) claims.get(JWT_CLAIM_SID);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 在已持有响应式锁的前提下执行刷新令牌轮换。
     *
     * <p>顺序为"黑名单检查 → Redis 存储比对 → 写入新令牌对 → 拉黑旧令牌"；
     * 写入新令牌成功后才拉黑旧令牌，避免瞬时故障导致用户被登出。</p>
     */
    private Mono<Boolean> doRotateRefreshToken(
            String subject,
            String sid,
            String oldRefreshToken,
            String newAccessToken,
            String newRefreshToken,
            TokenStoreEntry accessEntry,
            TokenStoreEntry refreshEntry) {
        boolean multiSession = hasSid(sid);
        String accessBucketKey = multiSession ? sessionAccessKey(subject, sid) : accessKey(subject);
        String refreshBucketKey = multiSession ? sessionRefreshKey(subject, sid) : refreshKey(subject);
        return tokenBlacklistService.isBlacklisted(oldRefreshToken).flatMap(inBlacklist -> {
            if (Boolean.TRUE.equals(inBlacklist)) {
                log.warn("刷新令牌轮换失败: 旧令牌已在黑名单, subject={}", subject);
                return Mono.just(false);
            }
            return redisTemplate
                    .opsForValue()
                    .get(refreshBucketKey)
                    .flatMap(storedValue -> {
                        if (storedValue == null || !tokenMatches(storedValue, oldRefreshToken)) {
                            log.warn(
                                    "刷新令牌轮换失败: Redis存储令牌与请求不一致, subject={}, sid={}",
                                    subject,
                                    multiSession ? sid : "legacy");
                            return Mono.just(false);
                        }
                        String accessValue = buildStoreValue(newAccessToken, accessEntry);
                        String refreshValue = buildStoreValue(newRefreshToken, refreshEntry);
                        return redisTemplate
                                .opsForValue()
                                .set(accessBucketKey, accessValue, Duration.ofMillis(accessTokenExpireTime))
                                .zipWith(redisTemplate
                                        .opsForValue()
                                        .set(refreshBucketKey, refreshValue, Duration.ofMillis(refreshTokenExpireTime)))
                                .flatMap(pair -> tokenBlacklistService
                                        .add(oldRefreshToken, refreshTokenExpireTime)
                                        .thenReturn(true));
                    })
                    .defaultIfEmpty(false);
        });
    }

    @Override
    public Mono<Long> deleteToken(String subject) {
        Mono<Boolean> accessDeleted = redisTemplate.opsForValue().delete(accessKey(subject));
        Mono<Boolean> refreshDeleted = redisTemplate.opsForValue().delete(refreshKey(subject));
        return Mono.zip(accessDeleted, refreshDeleted)
                .map(result -> (result.getT1() ? 1L : 0L) + (result.getT2() ? 1L : 0L));
    }

    @Override
    public Mono<Long> deleteSessionToken(String subject, String sid) {
        if (!hasSid(sid)) {
            return deleteToken(subject);
        }
        Mono<Boolean> accessDeleted = redisTemplate.opsForValue().delete(sessionAccessKey(subject, sid));
        Mono<Boolean> refreshDeleted = redisTemplate.opsForValue().delete(sessionRefreshKey(subject, sid));
        return Mono.zip(accessDeleted, refreshDeleted)
                .map(result -> (result.getT1() ? 1L : 0L) + (result.getT2() ? 1L : 0L))
                .flatMap(count -> {
                    if (count > 0) {
                        return redisTemplate
                                .opsForSet()
                                .remove(sessionsKey(subject), sid)
                                .thenReturn(count);
                    }
                    return Mono.just(count);
                });
    }

    @Override
    public Mono<Long> deleteAllSessions(String subject) {
        if (subject == null || subject.isBlank()) {
            return Mono.just(0L);
        }
        return redisTemplate
                .opsForSet()
                .members(sessionsKey(subject))
                .collectList()
                .defaultIfEmpty(List.of())
                .flatMap(sids -> Flux.fromIterable(sids)
                        .flatMap(sid -> deleteSessionToken(subject, sid))
                        .reduce(0L, Long::sum)
                        .defaultIfEmpty(0L)
                        .zipWith(deleteToken(subject))
                        .map(tuple -> tuple.getT1() + tuple.getT2())
                        .flatMap(total -> redisTemplate
                                .opsForValue()
                                .delete(sessionsKey(subject))
                                .thenReturn(total)));
    }

    @Override
    public Flux<String> findSessions(String subject) {
        if (subject == null || subject.isBlank()) {
            return Flux.empty();
        }
        return redisTemplate.opsForSet().members(sessionsKey(subject)).onErrorResume(e -> {
            log.warn("查询用户会话索引失败, subject={}, error={}", subject, e.getMessage());
            return Flux.empty();
        });
    }

    @Override
    public Mono<String> findRefreshToken(String subject) {
        return findSessionRefreshToken(subject, null);
    }

    @Override
    public Mono<String> findSessionRefreshToken(String subject, String sid) {
        if (subject == null || subject.isBlank()) {
            return Mono.empty();
        }
        String bucketKey = hasSid(sid) ? sessionRefreshKey(subject, sid) : refreshKey(subject);
        return redisTemplate.opsForValue().get(bucketKey).flatMap(storedValue -> {
            if (storedValue == null) {
                return Mono.empty();
            }
            if (storedValue.startsWith(JSON_START_BRACE)) {
                try {
                    TokenStoreEntry entry = JsonUtil.fromJson(storedValue, TokenStoreEntry.class);
                    if (entry != null
                            && entry.getToken() != null
                            && !entry.getToken().isBlank()) {
                        return Mono.just(entry.getToken());
                    }
                } catch (Exception e) {
                    log.warn("解析Redis RefreshToken JSON失败: {}", e.getMessage());
                }
                return Mono.empty();
            }
            return Mono.just(storedValue);
        });
    }
}
