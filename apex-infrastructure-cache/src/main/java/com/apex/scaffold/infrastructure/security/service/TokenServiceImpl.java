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
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Token 服务实现类（基于 Redis 分布式存储）
 *
 * <p>实现 {@link TokenService} 接口，提供 JWT Token 的生成、验证、存储和销毁功能。
 * Token 存储使用 Redis（通过 Redisson 客户端）而非本地内存，确保多实例部署场景下
 * Token 状态一致共享，避免内存泄露问题。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li><b>分布式存储</b>：AccessToken 和 RefreshToken 均存储在 Redis 中，并跟随用户标识</li>
 *   <li><b>自动过期</b>：Redis 键的 TTL 与 JWT Token 的过期时间保持一致，过期后自动清理</li>
 *   <li><b>Token 类型标识</b>：在 JWT claims 中注入 {@code type} 字段区分 access/refresh 类型</li>
 *   <li><b>多会话槽</b>：claims 携带 {@code sid}（会话ID）的令牌按
 *       {@code (subject, sid)} 独立存储，多端同时在线互不覆盖；会话索引
 *       {@code token:sessions:{subject}} 登记全部有效会话，无 {@code sid} 的令牌
 *       走 legacy 单会话槽（兼容存量客户端，自然过期淘汰）</li>
 *   <li><b>黑名单联动</b>：Token 有效性验证时检查 {@link TokenBlacklistService} 是否将 Token 列入黑名单</li>
 *   <li><b>JSON 存储</b>：自 2.2.0 起，登录/刷新时将 Token 与用户信息一起序列化为
 *       {@link TokenStoreEntry} JSON 对象存储，替代纯字符串格式</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>{@code token:access:{subject}} → legacy 单会话槽（{@link TokenStoreEntry} JSON）</li>
 *   <li>{@code token:refresh:{subject}} → legacy 单会话槽（{@link TokenStoreEntry} JSON）</li>
 *   <li>{@code token:access:{subject}:{sid}} → 多会话槽（sid 来自 JWT claims）</li>
 *   <li>{@code token:refresh:{subject}:{sid}} → 多会话槽</li>
 *   <li>{@code token:sessions:{subject}} → 会话索引（{@code RSet<String>}，TTL=refreshTokenExpireTime）</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <p>{@link #validateRefreshToken} 同时兼容旧格式（纯 JWT 字符串）和新格式（JSON 对象），
 * 通过检测值是否以 JSON 对象起始符开头来判断格式；无 {@code sid} 的令牌校验与轮换
 * 自动落回 legacy 槽，存量客户端行为不变。
 *
 * @author Apex Team
 * @version 3.3.0
 * @since 1.0.0
 * @see TokenService
 * @see TokenBlacklistService
 * @see RedissonClient
 * @see JwtUtil
 */
@Slf4j
@Service
@lombok.RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {

    private static final String JSON_START_BRACE = "{";
    private static final String JWT_CLAIM_TYPE = "type";
    private static final String JWT_CLAIM_SUB = "sub";
    private static final String JWT_CLAIM_SID = "sid";
    private static final String JWT_CLAIM_CLIENT_TYPE = "clientType";

    /** AccessToken 的 JWT claims 中 type 字段值 */
    private static final String TOKEN_TYPE_ACCESS = "access";

    /** RefreshToken 的 JWT claims 中 type 字段值 */
    private static final String TOKEN_TYPE_REFRESH = "refresh";

    /** Redis 中 AccessToken 的键名前缀 */
    private static final String REDIS_KEY_PREFIX_ACCESS = "token:access:";

    /** Redis 中 RefreshToken 的键名前缀 */
    private static final String REDIS_KEY_PREFIX_REFRESH = "token:refresh:";

    /** Redis 中会话索引（Set）的键名前缀 */
    private static final String REDIS_KEY_PREFIX_SESSIONS = "token:sessions:";

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
     * 构建刷新令牌轮换使用的分布式锁 Key。
     *
     * <p>锁按用户维度隔离，不同用户的刷新操作互不阻塞；同一用户的并发刷新
     * 通过该锁串行化，保证旧令牌单次消费语义。多会话模型下锁进一步按会话
     * 隔离（{@code ...:{subject}:{sid}}），不同端的刷新互不阻塞。</p>
     */
    private String refreshLockKey(String subject, String sid) {
        return hasSid(sid)
                ? redisKeyPrefix.wrap("token:refresh:rotate:" + subject + ":" + sid)
                : redisKeyPrefix.wrap("token:refresh:rotate:" + subject);
    }

    /**
     * 生成 AccessToken（旧版单会话模型）。
     *
     * <p>使用 JWT 生成访问令牌，在 claims 中注入 {@code type=access} 标识，
     * 用于后续解析时区分 AccessToken 和 RefreshToken。
     * 可选的 deviceId 会写入 claims，用于 Token 刷新时的设备指纹校验。
     * 不携带 {@code sid}，存储与校验走 legacy 单会话槽。
     *
     * @param subject  Token 主题，通常为用户的 OpenID
     * @param deviceId 设备标识，绑定 Token 与设备（可选）
     * @return JWT AccessToken 字符串
     */
    @Override
    public String generateAccessToken(String subject, String deviceId) {
        return generateAccessToken(subject, deviceId, null, null);
    }

    /**
     * 生成 AccessToken（多会话模型）。
     *
     * <p>在 claims 中注入 {@code type=access}、可选 {@code deviceId}、
     * {@code sid}（会话ID）与 {@code clientType}（客户端类型）。
     * 携带 {@code sid} 的令牌按 {@code (subject, sid)} 独立存储，
     * 多端同时在线互不覆盖；{@code sid} 为空时退化为旧版单会话模型。
     *
     * @param subject    Token 主题，通常为用户的 OpenID
     * @param deviceId   设备标识，绑定 Token 与设备（可选）
     * @param sid        会话ID（UUID），为空时退化为旧版单会话模型
     * @param clientType 客户端类型编码（{@code ClientType}），可为空
     * @return JWT AccessToken 字符串
     */
    @Override
    public String generateAccessToken(String subject, String deviceId, String sid, String clientType) {
        Map<String, Object> claims = new HashMap<>(8);
        claims.put("type", TOKEN_TYPE_ACCESS);
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

    /**
     * 生成 RefreshToken（旧版单会话模型）。
     *
     * <p>使用 JWT 生成刷新令牌，在 claims 中注入 {@code type=refresh} 标识，
     * 过期时间通常比 AccessToken 长（默认 7 天）。
     * 可选的 deviceId 会写入 claims，用于 Token 刷新时的设备指纹校验。
     * 不携带 {@code sid}，存储与校验走 legacy 单会话槽。
     *
     * @param subject  Token 主题，通常为用户的 OpenID
     * @param deviceId 设备标识，绑定 Token 与设备（可选）
     * @return JWT RefreshToken 字符串
     */
    @Override
    public String generateRefreshToken(String subject, String deviceId) {
        return generateRefreshToken(subject, deviceId, null, null);
    }

    /**
     * 生成 RefreshToken（多会话模型）。
     *
     * <p>{@code sid} 必须与同会话 AccessToken 一致，刷新轮换按会话槽隔离；
     * {@code sid} 为空时退化为旧版单会话模型。
     *
     * @param subject    Token 主题，通常为用户的 OpenID
     * @param deviceId   设备标识，绑定 Token 与设备（可选）
     * @param sid        会话ID（UUID），为空时退化为旧版单会话模型
     * @param clientType 客户端类型编码（{@code ClientType}），可为空
     * @return JWT RefreshToken 字符串
     */
    @Override
    public String generateRefreshToken(String subject, String deviceId, String sid, String clientType) {
        Map<String, Object> claims = new HashMap<>(8);
        claims.put("type", TOKEN_TYPE_REFRESH);
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
     */
    @Override
    public Mono<Void> storeToken(
            String subject,
            String accessToken,
            String refreshToken,
            TokenStoreEntry accessEntry,
            TokenStoreEntry refreshEntry) {
        return storeSessionToken(subject, null, accessToken, refreshToken, accessEntry, refreshEntry);
    }

    /**
     * 存储 Token 对（多会话模型，携带会话上下文）。
     *
     * <p>{@code sid} 非空时按 {@code (subject, sid)} 写入独立会话槽并登记会话索引
     * （Set，TTL 与 RefreshToken 一致），实现全局多会话互不覆盖；
     * {@code sid} 为空时退化为 legacy 单会话槽，行为与改造前一致。</p>
     *
     * @param subject       用户标识（OpenID）
     * @param sid           会话ID（UUID）；为空时走 legacy 单会话槽
     * @param accessToken   AccessToken 字符串
     * @param refreshToken  RefreshToken 字符串
     * @param accessEntry   AccessToken 对应的用户信息条目，可为 null
     * @param refreshEntry  RefreshToken 对应的用户信息条目，可为 null
     */
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
        RBucket<String> accessBucket = redissonClient.getBucket(sessionAccessKey(subject, sid), StringCodec.INSTANCE);
        RBucket<String> refreshBucket = redissonClient.getBucket(sessionRefreshKey(subject, sid), StringCodec.INSTANCE);
        // 会话索引与槽位同步登记；索引 TTL 与 RefreshToken 一致，过期后自动收拢
        RSet<String> sessions = redissonClient.getSet(sessionsKey(subject), StringCodec.INSTANCE);
        return Mono.when(
                Mono.fromCompletionStage(accessBucket.setAsync(accessValue, Duration.ofMillis(accessTokenExpireTime))),
                Mono.fromCompletionStage(
                        refreshBucket.setAsync(refreshValue, Duration.ofMillis(refreshTokenExpireTime))),
                Mono.fromCompletionStage(sessions.addAsync(sid))
                        .then(Mono.fromCompletionStage(sessions.expireAsync(Duration.ofMillis(refreshTokenExpireTime))))
                        .then());
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
     */
    @Override
    public Mono<Void> storeAccessToken(String subject, String accessToken, int ttlSeconds) {
        RBucket<String> accessBucket = redissonClient.getBucket(accessKey(subject), StringCodec.INSTANCE);
        return Mono.fromCompletionStage(accessBucket.setAsync(accessToken, Duration.ofSeconds(ttlSeconds)));
    }

    /**
     * 单独存储 AccessToken 到指定会话槽（多会话滑动续期场景）。
     *
     * <p>鉴权过滤器滑动续期时使用：按 {@code (subject, sid)} 覆盖写入
     * 新 AccessToken，不影响其他会话；{@code sid} 为空时回退到
     * legacy 单会话槽（{@link #storeAccessToken(String, String, int)}）。</p>
     *
     * @param subject     用户唯一标识
     * @param sid         会话ID（UUID）
     * @param accessToken 新 AccessToken 字符串
     * @param ttlSeconds  Redis 键过期时间（秒）
     */
    @Override
    public Mono<Void> storeSessionAccessToken(String subject, String sid, String accessToken, int ttlSeconds) {
        if (!hasSid(sid)) {
            return storeAccessToken(subject, accessToken, ttlSeconds);
        }
        RBucket<String> accessBucket = redissonClient.getBucket(sessionAccessKey(subject, sid), StringCodec.INSTANCE);
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
     *   <li>Redis 中存储的 Token 与传入的 Token 一致（多会话模型下按 claims.sid
     *       定位会话槽，无 sid 的令牌校验 legacy 单会话槽）</li>
     * </ol>
     *
     * <h2>设计说明</h2>
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
                // 多会话模型：按 claims.sid 定位会话槽；无 sid 的旧令牌校验 legacy 槽
                String sid = (String) claims.get(JWT_CLAIM_SID);
                String bucketKey = hasSid(sid) ? sessionRefreshKey(subject, sid) : refreshKey(subject);
                RBucket<String> refreshBucket = redissonClient.getBucket(bucketKey, StringCodec.INSTANCE);
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
        if (storedValue.startsWith(JSON_START_BRACE)) {
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
        // 会话ID在此提取并传入轮换流程，保证锁与存储按会话槽隔离。
        String[] sidHolder = new String[1];
        if (!isEligibleForRotation(subject, oldRefreshToken, sidHolder)) {
            return Mono.just(false);
        }
        return Mono.fromCallable(() -> rotateRefreshTokenSync(
                        subject,
                        sidHolder[0],
                        oldRefreshToken,
                        newAccessToken,
                        newRefreshToken,
                        accessEntry,
                        refreshEntry))
                .subscribeOn(BlockingIoSchedulers.cache());
    }

    /**
     * 刷新令牌轮换资格校验（纯内存、无 I/O，在调用线程执行）。
     *
     * <p>依次校验：令牌非空、JWT 类型为 refresh、主题与用户一致、未过期。
     * 任一项不满足即返回 false，作为轮换的快速失败前置条件。
     * 校验通过时同步提取旧令牌 claims 中的 {@code sid}，供轮换按会话槽隔离。</p>
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
     * 本方法锁内按"黑名单检查 → Redis 存储比对 → 写入新令牌对 → 拉黑旧令牌"顺序执行。
     * {@code sid} 非空时锁与读写均按会话槽隔离，多端并发刷新互不阻塞。</p>
     */
    private boolean rotateRefreshTokenSync(
            String subject,
            String sid,
            String oldRefreshToken,
            String newAccessToken,
            String newRefreshToken,
            TokenStoreEntry accessEntry,
            TokenStoreEntry refreshEntry) {
        boolean multiSession = hasSid(sid);
        RLock lock = redissonClient.getLock(refreshLockKey(subject, sid));
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
            String accessBucketKey = multiSession ? sessionAccessKey(subject, sid) : accessKey(subject);
            String refreshBucketKey = multiSession ? sessionRefreshKey(subject, sid) : refreshKey(subject);
            RBucket<String> refreshBucket = redissonClient.getBucket(refreshBucketKey, StringCodec.INSTANCE);
            String storedValue = refreshBucket.get();
            if (storedValue == null || !tokenMatches(storedValue, oldRefreshToken)) {
                log.warn("刷新令牌轮换失败: Redis存储令牌与请求不一致, subject={}, sid={}", subject, multiSession ? sid : "legacy");
                return false;
            }
            String accessValue = buildStoreValue(newAccessToken, accessEntry);
            String refreshValue = buildStoreValue(newRefreshToken, refreshEntry);
            RBucket<String> accessBucket = redissonClient.getBucket(accessBucketKey, StringCodec.INSTANCE);
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
     * 删除指定会话的 Token（多会话模型，单会话登出）。
     *
     * <p>仅清除 {@code (subject, sid)} 会话槽并从会话索引摘除登记，
     * 其他端会话不受影响；{@code sid} 为空时等价于 {@link #deleteToken(String)}。</p>
     *
     * @param subject 用户唯一标识
     * @param sid     会话ID（UUID）
     * @return 删除的 Token 数量（0-2）
     */
    @Override
    public Mono<Long> deleteSessionToken(String subject, String sid) {
        if (!hasSid(sid)) {
            return deleteToken(subject);
        }
        Mono<Boolean> accessDeleted = Mono.fromCompletionStage(redissonClient
                .getBucket(sessionAccessKey(subject, sid), StringCodec.INSTANCE)
                .deleteAsync());
        Mono<Boolean> refreshDeleted = Mono.fromCompletionStage(redissonClient
                .getBucket(sessionRefreshKey(subject, sid), StringCodec.INSTANCE)
                .deleteAsync());
        RSet<String> sessions = redissonClient.getSet(sessionsKey(subject), StringCodec.INSTANCE);
        return Mono.zip(accessDeleted, refreshDeleted)
                .map(result -> (result.getT1() ? 1L : 0L) + (result.getT2() ? 1L : 0L))
                .doOnNext(count -> {
                    if (count > 0) {
                        sessions.removeAsync(sid);
                    }
                });
    }

    /**
     * 删除用户全部会话（多会话模型，全端登出）。
     *
     * <p>遍历会话索引清除所有会话槽，并一并清理 legacy 单会话槽与索引本身；
     * 用户无会话索引时仍通过 {@link #deleteToken(String)} 清理 legacy 槽。</p>
     *
     * @param subject 用户唯一标识
     * @return 删除的 Token 数量（会话数 × 2 + legacy 槽 0-2）
     */
    @Override
    public Mono<Long> deleteAllSessions(String subject) {
        if (subject == null || subject.isBlank()) {
            return Mono.just(0L);
        }
        RSet<String> sessions = redissonClient.getSet(sessionsKey(subject), StringCodec.INSTANCE);
        return Mono.fromCompletionStage(sessions.readAllAsync())
                .flatMap(sids -> Flux.fromIterable(sids == null ? java.util.Set.<String>of() : sids)
                        .flatMap(sid -> deleteSessionToken(subject, sid))
                        .reduce(0L, Long::sum)
                        .defaultIfEmpty(0L)
                        .zipWith(deleteToken(subject))
                        .map(tuple -> tuple.getT1() + tuple.getT2())
                        .flatMap(total ->
                                Mono.fromCompletionStage(sessions.deleteAsync()).thenReturn(total)));
    }

    /**
     * 查询用户当前全部有效会话。
     *
     * <p>返回会话索引中登记的全部 {@code sid}（legacy 单会话槽不登记索引，
     * 由登出链路通过 {@link #findRefreshToken(String)} 单独处理）。</p>
     *
     * @param subject 用户唯一标识
     * @return 会话ID集合
     */
    @Override
    public Flux<String> findSessions(String subject) {
        if (subject == null || subject.isBlank()) {
            return Flux.empty();
        }
        RSet<String> sessions = redissonClient.getSet(sessionsKey(subject), StringCodec.INSTANCE);
        return Mono.fromCompletionStage(sessions.readAllAsync())
                .flatMapMany(sids -> Flux.fromIterable(sids == null ? java.util.Set.<String>of() : sids))
                .onErrorResume(e -> {
                    log.warn("查询用户会话索引失败, subject={}, error={}", subject, e.getMessage());
                    return Flux.empty();
                });
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
        return findSessionRefreshToken(subject, null);
    }

    /**
     * 获取指定会话已存储的 RefreshToken 字符串（多会话模型）。
     *
     * <p>{@code sid} 非空时读取会话槽 {@code token:refresh:{subject}:{sid}}；
     * 为空时读取 legacy 单会话槽 {@code token:refresh:{subject}}。
     * 兼容两种存储格式：{@link TokenStoreEntry} JSON 对象提取其 {@code token} 字段，
     * 纯 JWT 字符串直接返回。未命中或解析失败时返回空 Mono。</p>
     *
     * @param subject 用户唯一标识
     * @param sid     会话ID（UUID），可为空
     * @return RefreshToken 字符串
     */
    @Override
    public Mono<String> findSessionRefreshToken(String subject, String sid) {
        if (subject == null || subject.isBlank()) {
            return Mono.empty();
        }
        String bucketKey = hasSid(sid) ? sessionRefreshKey(subject, sid) : refreshKey(subject);
        RBucket<String> refreshBucket = redissonClient.getBucket(bucketKey, StringCodec.INSTANCE);
        return Mono.fromCompletionStage(refreshBucket.getAsync()).flatMap(storedValue -> {
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
