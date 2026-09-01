package com.apex.scaffold.infrastructure.security.service;

import com.apex.scaffold.common.config.RedisKeyPrefix;
import com.apex.scaffold.common.dto.TokenStoreEntry;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.common.util.JsonUtil;
import com.apex.scaffold.common.util.JwtUtil;
import com.apex.scaffold.domain.service.TokenBlacklistService;
import com.apex.scaffold.domain.service.TokenService;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Token 服务实现类（基于 Redis 分布式存储）
 *
 * <p>实现 {@link TokenService} 接口，提供 JWT Token 的生成、验证、存储和销毁功能。
 * Token 存储使用 Redis（通过 Redisson 客户端）而非本地内存，确保多实例部署场景下
 * Token 状态一致共享，避免内存泄露问题。
 *
 * <h2>核心设计</h2>
 * <ul>
 *   <li><b>分布式存储</b>：AccessToken 和 RefreshToken 均存储在 Redis 中，键名前缀分别为
 *       {@code token:access:} 和 {@code token:refresh:}，并跟随用户标识</li>
 *   <li><b>自动过期</b>：Redis 键的 TTL 与 JWT Token 的过期时间保持一致，过期后自动清理</li>
 *   <li><b>Token 类型标识</b>：在 JWT claims 中注入 {@code type} 字段区分 access/refresh 类型</li>
 *   <li><b>黑名单联动</b>：Token 有效性验证时检查 {@link TokenBlacklistService} 是否将 Token 列入黑名单</li>
 *   <li><b>JSON 存储</b>：自 2.2.0 起，登录/刷新时将 Token 与用户信息一起序列化为
 *       {@link TokenStoreEntry} JSON 对象存储，替代纯字符串格式</li>
 * </ul>
 *
 * <h2>键值映射</h2>
 * <ul>
 *   <li>{@code token:access:{subject}} → {@link TokenStoreEntry} JSON（TTL=accessTokenExpireTime）</li>
 *   <li>{@code token:refresh:{subject}} → {@link TokenStoreEntry} JSON（TTL=refreshTokenExpireTime）</li>
 * </ul>
 *
 * <h2>向后兼容</h2>
 * <p>{@link #validateRefreshToken} 同时兼容旧格式（纯 JWT 字符串）和新格式（JSON 对象），
 * 通过检测值是否以 JSON 对象起始符开头来判断格式。
 *
 * @author Apex Team
 * @version 2.1.0
 * @since 1.0.0
 * @see TokenService
 * @see TokenBlacklistService
 * @see RedissonClient
 * @see JwtUtil
 */
@Slf4j
@Service
public class TokenServiceImpl implements TokenService {

    /** AccessToken 的 JWT claims 中 type 字段值 */
    private static final String TOKEN_TYPE_ACCESS = "access";

    /** RefreshToken 的 JWT claims 中 type 字段值 */
    private static final String TOKEN_TYPE_REFRESH = "refresh";

    /** Redis 中 AccessToken 的键名前缀 */
    private static final String REDIS_KEY_PREFIX_ACCESS = "token:access:";

    /** Redis 中 RefreshToken 的键名前缀 */
    private static final String REDIS_KEY_PREFIX_REFRESH = "token:refresh:";

    /** AccessToken 默认过期时间（毫秒），默认 2 小时 */
    private static final long DEFAULT_ACCESS_TOKEN_EXPIRE_MS = 7200000L;

    /** RefreshToken 默认过期时间（毫秒），默认 7 天 */
    private static final long DEFAULT_REFRESH_TOKEN_EXPIRE_MS = 604800000L;

    /** AccessToken 过期时间（毫秒），默认 2 小时 */
    @Value("${jwt.access-token-expire:" + DEFAULT_ACCESS_TOKEN_EXPIRE_MS + "}")
    private long accessTokenExpireTime;

    /** RefreshToken 过期时间（毫秒），默认 7 天 */
    @Value("${jwt.refresh-token-expire:" + DEFAULT_REFRESH_TOKEN_EXPIRE_MS + "}")
    private long refreshTokenExpireTime;

    /** Token 黑名单服务，用于使特定 Token 主动失效 */
    private final TokenBlacklistService tokenBlacklistService;

    /** Redisson 客户端，提供分布式 Redis 存储能力 */
    private final RedissonClient redissonClient;

    /** Redis Key 版本前缀管理器 */
    private final RedisKeyPrefix redisKeyPrefix;

    /**
     * 构造方法，注入 Token 管理所需的依赖。
     *
     * @param tokenBlacklistService Token 黑名单服务
     * @param redissonClient Redisson 客户端
     * @param redisKeyPrefix Redis Key 版本前缀管理器
     */
    public TokenServiceImpl(
            TokenBlacklistService tokenBlacklistService, RedissonClient redissonClient, RedisKeyPrefix redisKeyPrefix) {
        this.tokenBlacklistService = tokenBlacklistService;
        this.redissonClient = redissonClient;
        this.redisKeyPrefix = redisKeyPrefix;
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
     * 构建刷新令牌轮换使用的分布式锁 Key。
     *
     * <p>锁按用户维度隔离，不同用户的刷新操作互不阻塞；同一用户的并发刷新
     * 通过该锁串行化，保证旧令牌单次消费语义。</p>
     */
    private String refreshLockKey(String subject) {
        return redisKeyPrefix.wrap("token:refresh:rotate:" + subject);
    }

    /**
     * 生成 AccessToken
     *
     * <p>使用 JWT 生成访问令牌，在 claims 中注入 {@code type=access} 标识，
     * 用于后续解析时区分 AccessToken 和 RefreshToken。
     * 可选的 deviceId 会写入 claims，用于 Token 刷新时的设备指纹校验。
     *
     * @param subject  Token 主题，通常为用户的 OpenID
     * @param deviceId 设备标识，绑定 Token 与设备（可选）
     * @return JWT AccessToken 字符串
     */
    @Override
    public String generateAccessToken(String subject, String deviceId) {
        Map<String, Object> claims = new HashMap<>(8);
        claims.put("type", TOKEN_TYPE_ACCESS);
        if (deviceId != null && !deviceId.isBlank()) {
            claims.put("deviceId", deviceId);
        }
        return JwtUtil.generateToken(subject, claims, accessTokenExpireTime);
    }

    /**
     * 生成 RefreshToken
     *
     * <p>使用 JWT 生成刷新令牌，在 claims 中注入 {@code type=refresh} 标识，
     * 过期时间通常比 AccessToken 长（默认 7 天）。
     * 可选的 deviceId 会写入 claims，用于 Token 刷新时的设备指纹校验。
     *
     * @param subject  Token 主题，通常为用户的 OpenID
     * @param deviceId 设备标识，绑定 Token 与设备（可选）
     * @return JWT RefreshToken 字符串
     */
    @Override
    public String generateRefreshToken(String subject, String deviceId) {
        Map<String, Object> claims = new HashMap<>(8);
        claims.put("type", TOKEN_TYPE_REFRESH);
        if (deviceId != null && !deviceId.isBlank()) {
            claims.put("deviceId", deviceId);
        }
        return JwtUtil.generateToken(subject, claims, refreshTokenExpireTime);
    }

    /**
     * 解析 JWT Token
     *
     * <p>解析 Token 并返回其 claims 内容，不校验 Token 是否在黑名单中。
     * 若 Token 已过期或签名无效，将抛出 JWT 解析异常。
     *
     * @param token JWT Token 字符串
     * @return Token 的 claims 键值对
     * @throws io.jsonwebtoken.JwtException 当 Token 无效或已过期时抛出
     */
    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> parseToken(String token) {
        return JwtUtil.parseToken(token);
    }

    /**
     * 验证 Token 是否有效
     *
     * <p>检查 Token 的 JWT 签名是否有效，且未被列入黑名单。
     *
     * @param token JWT Token 字符串
     * @return 响应式校验结果：true-有效且不在黑名单中，false-无效或已在黑名单中
     */
    @Override
    public Mono<Boolean> validateToken(String token) {
        try {
            JwtUtil.parseToken(token);
            return tokenBlacklistService.isBlacklisted(token).map(inBlacklist -> !inBlacklist);
        } catch (Exception e) {
            return Mono.just(false);
        }
    }

    /**
     * 存储 Token 对到 Redis（纯 JWT 字符串格式，无用户信息）。
     *
     * <p>将生成的 AccessToken 和 RefreshToken 分别存入 Redis，
     * 并设置与 Token 过期时间一致的 TTL，Redis 会自动清理过期键。
     *
     * @param subject      用户标识（OpenID）
     * @param accessToken  AccessToken 字符串
     * @param refreshToken RefreshToken 字符串
     * @return 空的 Mono，表示存储完成
     */
    @Override
    public Mono<Void> storeToken(String subject, String accessToken, String refreshToken) {
        return storeToken(subject, accessToken, refreshToken, null, null);
    }

    /**
     * 存储 Token 对到 Redis（携带用户信息的 JSON 格式）。
     *
     * <p>将生成的 AccessToken 和 RefreshToken 分别与用户信息组合为
     * {@link TokenStoreEntry} JSON 对象存入 Redis。若 entry 参数为 null，
     * 则退化为纯 JWT 字符串存储。
     *
     * @param subject       用户标识（OpenID）
     * @param accessToken   AccessToken 字符串
     * @param refreshToken  RefreshToken 字符串
     * @param accessEntry   AccessToken 对应的用户信息条目，可为 null
     * @param refreshEntry  RefreshToken 对应的用户信息条目，可为 null
     * @return 空的 Mono，表示存储完成
     */
    @Override
    public Mono<Void> storeToken(
            String subject,
            String accessToken,
            String refreshToken,
            TokenStoreEntry accessEntry,
            TokenStoreEntry refreshEntry) {
        String accessValue = buildStoreValue(accessToken, accessEntry);
        String refreshValue = buildStoreValue(refreshToken, refreshEntry);
        RBucket<String> accessBucket = redissonClient.getBucket(accessKey(subject), StringCodec.INSTANCE);
        RBucket<String> refreshBucket = redissonClient.getBucket(refreshKey(subject), StringCodec.INSTANCE);
        return Mono.when(
                Mono.fromCompletionStage(accessBucket.setAsync(accessValue, Duration.ofMillis(accessTokenExpireTime))),
                Mono.fromCompletionStage(
                        refreshBucket.setAsync(refreshValue, Duration.ofMillis(refreshTokenExpireTime))));
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

    /**
     * 单独存储 AccessToken 到 Redis（用于新用户注册前临时 Token 场景）。
     *
     * <p>仅存储 AccessToken，不设置 RefreshToken。主要用于短信登录新用户
     * 收到 REGISTER_REQUIRED 时发放的临时 Token，避免客户端在注册流程中
     * 因缺少 Token 而被 JwtAuthenticationFilter 拦截返回 401。</p>
     *
     * @param subject      用户标识（新用户为手机号）
     * @param accessToken  AccessToken 字符串
     * @param ttlSeconds   Redis 键过期时间（秒）
     * @return 空的 Mono，表示存储完成
     */
    @Override
    public Mono<Void> storeAccessToken(String subject, String accessToken, int ttlSeconds) {
        RBucket<String> accessBucket = redissonClient.getBucket(accessKey(subject), StringCodec.INSTANCE);
        return Mono.fromCompletionStage(accessBucket.setAsync(accessToken, Duration.ofSeconds(ttlSeconds)));
    }

    /**
     * 校验 RefreshToken 的有效性
     *
     * <p>对 RefreshToken 进行多维度校验，只有当所有条件都满足时才返回 true：
     * <ol>
     *   <li>Token 不在黑名单中</li>
     *   <li>JWT 解析成功，且 type 字段为 refresh</li>
     *   <li>subject 与传入的 subject 匹配</li>
     *   <li>Token 未过期</li>
     *   <li>Redis 中存储的 Token 与传入的 Token 一致</li>
     * </ol>
     *
     * <h2>向后兼容</h2>
     * <p>Redis 中存储的值可能是旧格式（纯 JWT 字符串）或新格式（{@link TokenStoreEntry} JSON）。
     * 新格式以 JSON 对象起始符开头，通过此特征判断并提取其中的 token 字段进行比较。
     *
     * @param subject      用户标识（OpenID）
     * @param refreshToken 需要校验的 RefreshToken 字符串
     * @return {@code Mono.just(true)}-校验通过，{@code Mono.just(false)}-校验失败
     */
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
                String tokenType = (String) claims.get("type");
                if (!TOKEN_TYPE_REFRESH.equals(tokenType)) {
                    return Mono.just(false);
                }
                String tokenSubject = (String) claims.get("sub");
                if (tokenSubject == null || !tokenSubject.equals(subject)) {
                    return Mono.just(false);
                }
                if (JwtUtil.isTokenExpired(refreshToken)) {
                    return Mono.just(false);
                }
                RBucket<String> refreshBucket = redissonClient.getBucket(refreshKey(subject), StringCodec.INSTANCE);
                return Mono.fromCompletionStage(refreshBucket.getAsync())
                        .map(storedValue -> tokenMatches(storedValue, refreshToken))
                        .defaultIfEmpty(false);
            } catch (Exception e) {
                return Mono.just(false);
            }
        });
    }

    /**
     * 判断 Redis 中存储的值是否与传入的 Token 匹配。
     *
     * <p>兼容两种存储格式：
     * <ul>
     *   <li><b>旧格式</b>：纯 JWT 字符串，直接字符串比较</li>
     *   <li><b>新格式</b>：{@link TokenStoreEntry} JSON 对象，以对象起始符开头，
     *       反序列化后提取 {@code token} 字段比较</li>
     * </ul>
     *
     * @param storedValue Redis 中存储的值
     * @param token       待比较的 Token 字符串
     * @return true-匹配，false-不匹配
     */
    private boolean tokenMatches(String storedValue, String token) {
        if (storedValue == null) {
            return false;
        }
        // 新格式：以 '{' 开头的 JSON 对象
        if (storedValue.startsWith("{")) {
            try {
                TokenStoreEntry entry = JsonUtil.fromJson(storedValue, TokenStoreEntry.class);
                return entry != null && token.equals(entry.getToken());
            } catch (Exception e) {
                log.warn("解析 Redis Token JSON 失败，回退为字符串比较: {}", e.getMessage());
                return token.equals(storedValue);
            }
        }
        // 旧格式：纯 JWT 字符串
        return token.equals(storedValue);
    }

    /**
     * 原子轮换刷新令牌（分布式锁保护）。
     *
     * <p>在按用户维度的 Redisson 分布式锁内完成"读取旧令牌 → 比对旧令牌 →
     * 写入新令牌对 → 拉黑旧令牌"全过程。同一刷新令牌只能被一个请求消费：
     * 并发携带同一旧令牌的刷新请求中仅第一个成功，其余因旧令牌已被轮换而失败，
     * 杜绝刷新令牌并发重用竞态。</p>
     *
     * <p>写入新令牌成功后才拉黑旧令牌；若写入失败，旧令牌仍保持可用，
     * 用户不会被瞬时故障登出。获取锁超时或旧令牌不匹配时返回 false。</p>
     *
     * <p>锁与 Redis 读写均使用 Redisson 同步 API，并调度到
     * {@code BlockingIoSchedulers#cache()} 隔离线程池，避免阻塞 Netty 事件循环；
     * 释放锁沿用项目既有 {@code isHeldByCurrentThread + unlock} 模式。</p>
     *
     * @param subject          用户唯一标识（OpenID）
     * @param oldRefreshToken  待轮换的旧刷新令牌
     * @param newAccessToken   新生成的 AccessToken
     * @param newRefreshToken  新生成的 RefreshToken
     * @param accessEntry      AccessToken 对应的用户信息条目，可为 null
     * @param refreshEntry     RefreshToken 对应的用户信息条目，可为 null
     * @return 轮换成功返回 true；旧令牌不匹配、已失效、已在黑名单或获取锁超时返回 false
     */
    @Override
    public Mono<Boolean> rotateRefreshToken(
            String subject,
            String oldRefreshToken,
            String newAccessToken,
            String newRefreshToken,
            TokenStoreEntry accessEntry,
            TokenStoreEntry refreshEntry) {
        // JWT 快检在调用线程同步执行（纯 CPU、无 I/O），不匹配时直接失败，
        // 避免无效刷新请求占用分布式锁与调度线程；也便于单元测试验证。
        if (!isEligibleForRotation(subject, oldRefreshToken)) {
            return Mono.just(false);
        }
        return Mono.fromCallable(() -> rotateRefreshTokenSync(
                        subject, oldRefreshToken, newAccessToken, newRefreshToken, accessEntry, refreshEntry))
                .subscribeOn(BlockingIoSchedulers.cache());
    }

    /**
     * 刷新令牌轮换资格校验（纯内存、无 I/O，在调用线程执行）。
     *
     * <p>依次校验：令牌非空、JWT 类型为 refresh、主题与用户一致、未过期。
     * 任一项不满足即返回 false，作为轮换的快速失败前置条件。</p>
     *
     * @param subject         用户唯一标识（OpenID）
     * @param oldRefreshToken 待轮换的旧刷新令牌
     * @return 通过校验返回 true，否则返回 false
     */
    private boolean isEligibleForRotation(String subject, String oldRefreshToken) {
        if (subject == null || subject.isBlank() || oldRefreshToken == null || oldRefreshToken.isBlank()) {
            return false;
        }
        try {
            Map<String, Object> claims = JwtUtil.parseToken(oldRefreshToken);
            if (!TOKEN_TYPE_REFRESH.equals(claims.get("type"))) {
                return false;
            }
            if (!subject.equals(claims.get("sub"))) {
                return false;
            }
            return !JwtUtil.isTokenExpired(oldRefreshToken);
        } catch (Exception e) {
            return false;
        }
    }

    /** 刷新令牌轮换分布式锁的等待时间（秒）。 */
    private static final long REFRESH_LOCK_WAIT_SECONDS = 5;

    /** 刷新令牌轮换分布式锁的持有时间（秒）。 */
    private static final long REFRESH_LOCK_LEASE_SECONDS = 10;

    /** 黑名单操作的阻塞等待上限（秒）。 */
    private static final long BLACKLIST_OP_TIMEOUT_SECONDS = 2;

    /**
     * 同步执行刷新令牌轮换（在 {@code BlockingIoSchedulers#cache()} 线程上调用）。
     *
     * <p>资格校验已由 {@link #isEligibleForRotation} 在调用线程完成，
     * 本方法锁内按"黑名单检查 → Redis 存储比对 → 写入新令牌对 → 拉黑旧令牌"顺序执行。</p>
     */
    private boolean rotateRefreshTokenSync(
            String subject,
            String oldRefreshToken,
            String newAccessToken,
            String newRefreshToken,
            TokenStoreEntry accessEntry,
            TokenStoreEntry refreshEntry) {
        RLock lock = redissonClient.getLock(refreshLockKey(subject));
        boolean acquired = false;
        try {
            try {
                acquired = lock.tryLock(REFRESH_LOCK_WAIT_SECONDS, REFRESH_LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException interruptedException) {
                // 线程被中断时不重试，恢复中断标志后按获取锁失败处理，避免虚假成功。
                Thread.currentThread().interrupt();
                log.warn("刷新令牌轮换等待锁被中断, subject={}", subject);
                return false;
            }
            if (!acquired) {
                log.warn("刷新令牌轮换获取分布式锁超时, subject={}", subject);
                return false;
            }
            if (Boolean.TRUE.equals(tokenBlacklistService
                    .isBlacklisted(oldRefreshToken)
                    .block(Duration.ofSeconds(BLACKLIST_OP_TIMEOUT_SECONDS)))) {
                log.warn("刷新令牌轮换失败: 旧令牌已在黑名单, subject={}", subject);
                return false;
            }
            RBucket<String> refreshBucket = redissonClient.getBucket(refreshKey(subject), StringCodec.INSTANCE);
            String storedValue = refreshBucket.get();
            if (storedValue == null || !tokenMatches(storedValue, oldRefreshToken)) {
                log.warn("刷新令牌轮换失败: Redis存储令牌与请求不一致, subject={}", subject);
                return false;
            }
            String accessValue = buildStoreValue(newAccessToken, accessEntry);
            String refreshValue = buildStoreValue(newRefreshToken, refreshEntry);
            RBucket<String> accessBucket = redissonClient.getBucket(accessKey(subject), StringCodec.INSTANCE);
            // 先写入新令牌对，成功后才拉黑旧令牌，避免失败导致用户被登出。
            accessBucket.set(accessValue, Duration.ofMillis(accessTokenExpireTime));
            refreshBucket.set(refreshValue, Duration.ofMillis(refreshTokenExpireTime));
            tokenBlacklistService
                    .add(oldRefreshToken, refreshTokenExpireTime)
                    .block(Duration.ofSeconds(BLACKLIST_OP_TIMEOUT_SECONDS));
            return true;
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                try {
                    lock.unlock();
                } catch (IllegalMonitorStateException e) {
                    log.warn("释放刷新轮换锁异常（可能已自动过期）: subject={}", subject, e);
                }
            }
        }
    }

    /**
     * 删除用户的所有 Token
     *
     * <p>从 Redis 中清除指定用户的 AccessToken 和 RefreshToken，
     * 通常在用户退出登录或修改密码时调用。
     *
     * @param subject 用户标识（OpenID）
     * @return {@code Mono.just(2L)} 固定返回 2，表示删除了 access 和 refresh 两条记录
     */
    @Override
    public Mono<Long> deleteToken(String subject) {
        Mono<Boolean> accessDeleted = Mono.fromCompletionStage(redissonClient
                .getBucket(accessKey(subject), StringCodec.INSTANCE)
                .deleteAsync());
        Mono<Boolean> refreshDeleted = Mono.fromCompletionStage(redissonClient
                .getBucket(refreshKey(subject), StringCodec.INSTANCE)
                .deleteAsync());
        return Mono.zip(accessDeleted, refreshDeleted)
                .map(result -> (result.getT1() ? 1L : 0L) + (result.getT2() ? 1L : 0L));
    }

    /**
     * 获取用户已存储的RefreshToken字符串。
     *
     * <p>从 Redis 中读取 {@code token:refresh:{subject}} 键的值，兼容两种存储格式：
     * <ul>
     *   <li>新格式：以 <code>{}</code> 开头的 {@link TokenStoreEntry} JSON 对象，提取其 {@code token} 字段</li>
     *   <li>旧格式：纯 JWT 字符串，直接返回</li>
     * </ul>
     *
     * <p>未命中或解析失败时返回空 Mono。</p>
     *
     * @param subject 用户唯一标识
     * @return RefreshToken 字符串
     */
    @Override
    public Mono<String> findRefreshToken(String subject) {
        if (subject == null || subject.isBlank()) {
            return Mono.empty();
        }
        RBucket<String> refreshBucket = redissonClient.getBucket(refreshKey(subject), StringCodec.INSTANCE);
        return Mono.fromCompletionStage(refreshBucket.getAsync()).flatMap(storedValue -> {
            if (storedValue == null) {
                return Mono.empty();
            }
            if (storedValue.startsWith("{")) {
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
