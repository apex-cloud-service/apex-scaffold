package com.apex.scaffold.interfaces.exception;

import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.interfaces.config.DegradeSnapshotProperties;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 熔断降级业务级兜底服务。
 *
 * <p>当 Sentinel 熔断或限流触发时，不再仅返回统一错误码，而是根据资源名称
 * 尝试返回缓存兜底值或"排队中"状态，提升用户体验。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li><strong>读接口</strong>：尝试从 Redis 获取上次成功响应的缓存快照，
 *       命中则返回缓存数据并标记 {@code degraded=true}</li>
 *   <li><strong>写接口</strong>：返回"排队中"状态，提示客户端稍后重试</li>
 *   <li><strong>兜底</strong>：缓存未命中或写接口无快照时，返回标准降级错误</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <p>业务服务可在正常响应成功后调用 {@link #saveSnapshot(String, Object)} 保存快照。
 * 降级时通过 {@link #getSnapshot(String)} 获取最近一次成功响应。
 * 快照 TTL 默认 5 分钟，避免返回过期数据。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li><strong>Redis 写入节流</strong>：同一资源在
 *       {@link DegradeSnapshotProperties#getRedisThrottleSeconds()} 窗口内最多写一次 Redis，
 *       本地缓存不受节流限制（始终更新，保证最新数据）；</li>
 *   <li><strong>本地缓存容量上限</strong>：超过
 *       {@link DegradeSnapshotProperties#getLocalMaxEntries()} 时淘汰最旧条目，防止内存积攒；</li>
 *   <li><strong>惰性过期</strong>：读取本地缓存时校验 TTL，过期条目即时清理。</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.1.0
 * @since 1.0.0
 * @see SentinelExceptionHandler
 */
@Slf4j
@Service
public class DegradeFallbackService {

    /** 快照缓存 Key 前缀 */
    private static final String SNAPSHOT_KEY_PREFIX = "degrade:snapshot:";

    /** 快照默认 TTL */
    private static final Duration SNAPSHOT_TTL = Duration.ofMinutes(5);

    /**
     * 内存级兜底缓存，Redis 不可达时使用。
     *
     * <p>值为带写入时间戳的 {@link LocalCacheEntry}，用于容量淘汰与惰性过期判断。</p>
     */
    private final Map<String, LocalCacheEntry> localFallbackCache = new ConcurrentHashMap<>();

    /**
     * 各资源最近一次成功写 Redis 的时间戳（毫秒），用于写入节流。
     *
     * <p>仅记录发起写入的时间点；写入失败不影响时间戳，下个窗口仍会重试。</p>
     */
    private final Map<String, Long> lastRedisWriteAt = new ConcurrentHashMap<>();

    /**
     * 响应式 Redis 模板，用于存储和读取快照；未配置通用模板时为 {@code null}，仅使用本地缓存。
     */
    private final ReactiveRedisTemplate<String, Object> redisTemplate;

    /**
     * 降级快照配置（总开关 / 节流窗口 / 容量上限），构造参数为 {@code null} 时使用默认值。
     */
    private final DegradeSnapshotProperties properties;

    /**
     * 构造降级兜底服务（构造器注入，Redis 与配置均可选）。
     *
     * <p>不强制要求通用 {@link ReactiveRedisTemplate} Bean。现有开发环境只提供
     * {@code ReactiveStringRedisTemplate} 时，{@link ObjectProvider#getIfAvailable()} 返回
     * {@code null}，服务仍可启动并使用本地快照兜底；若应用注册了通用模板，则启用 Redis 快照。
     * {@link DegradeSnapshotProperties} 未注册时同样使用默认值（开关开启、节流 5s、容量 256）。</p>
     *
     * @param redisTemplateProvider 通用响应式 Redis 模板提供器（可无匹配 Bean）
     * @param properties            降级快照配置属性（可为 {@code null}，使用默认值）
     */
    public DegradeFallbackService(
            ObjectProvider<ReactiveRedisTemplate<String, Object>> redisTemplateProvider,
            DegradeSnapshotProperties properties) {
        this.redisTemplate = redisTemplateProvider != null ? redisTemplateProvider.getIfAvailable() : null;
        this.properties = properties != null ? properties : new DegradeSnapshotProperties();
        if (this.redisTemplate == null) {
            log.info("未发现通用 ReactiveRedisTemplate，降级快照使用本地缓存");
        }
    }

    /**
     * 保存资源响应快照到本地缓存（节流后异步写 Redis）。
     *
     * <p>在正常请求成功后调用，为后续可能的降级场景准备兜底数据。
     * 保存失败不影响主流程。受 {@link DegradeSnapshotProperties#isEnabled()} 控制，
     * 关闭时直接返回。</p>
     *
     * @param resource 资源名称（Sentinel resource 名称）
     * @param data     响应数据
     */
    public void saveSnapshot(String resource, Object data) {
        if (!properties.isEnabled() || resource == null || data == null) {
            return;
        }
        String key = SNAPSHOT_KEY_PREFIX + resource;
        localFallbackCache.put(key, new LocalCacheEntry(data, System.currentTimeMillis()));
        evictIfOverCapacity();
        writeRedisThrottled(key, resource, data);
    }

    /**
     * 获取资源的降级快照。
     *
     * <p>优先从 Redis 获取，Redis 不可达时回退到本地内存缓存；本地缓存条目过期时
     * 即时清理并视为未命中。</p>
     *
     * @param resource 资源名称
     * @return 包含快照数据的 Mono，未命中返回空 Mono
     */
    public Mono<Object> getSnapshot(String resource) {
        if (resource == null) {
            return Mono.empty();
        }
        String key = SNAPSHOT_KEY_PREFIX + resource;
        if (redisTemplate == null) {
            return localSnapshot(key);
        }
        return Optional.ofNullable(readRedisSnapshot(key, resource))
                .orElseGet(Mono::empty)
                .doOnError(e -> log.warn("从 Redis 获取降级快照失败, resource: {}", resource, e))
                .onErrorResume(e -> localSnapshot(key))
                .switchIfEmpty(Mono.defer(() -> localSnapshot(key)));
    }

    /**
     * 从 Redis 读取快照。
     *
     * <p>同步读取异常时记录日志并返回 {@code null}，由调用方视作未命中并回退本地缓存。</p>
     *
     * @param key      快照 key
     * @param resource 资源名称
     * @return Redis 快照 Mono；读取异常时返回 {@code null}
     */
    private Mono<Object> readRedisSnapshot(String key, String resource) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("从 Redis 获取降级快照异常, resource: {}", resource, e);
            return null;
        }
    }

    /**
     * 从本地缓存读取快照。
     *
     * @param key 快照 key
     * @return 本地快照或空 Mono；条目已过期则移除并返回空
     */
    private Mono<Object> localSnapshot(String key) {
        return Optional.ofNullable(localFallbackCache.get(key))
                .filter(entry -> !isExpired(entry))
                .map(LocalCacheEntry::getData)
                .map(Mono::just)
                .orElseGet(() -> {
                    localFallbackCache.remove(key);
                    return Mono.empty();
                });
    }

    /**
     * 判断本地缓存条目是否已超过快照 TTL。
     *
     * @param entry 本地缓存条目
     * @return 过期返回 {@code true}
     */
    private static boolean isExpired(LocalCacheEntry entry) {
        return System.currentTimeMillis() - entry.getSavedAtMillis() > SNAPSHOT_TTL.toMillis();
    }

    /**
     * 本地缓存超过容量上限时，一次性淘汰最旧的超限条目。
     *
     * <p>并发写入下可能瞬时超限；按写入时间戳升序排序后仅淘汰最旧的超限条目，
     * 保证优先保留最新快照，复杂度为单次 O(n log n)。</p>
     */
    private void evictIfOverCapacity() {
        int maxEntries = properties.getLocalMaxEntries();
        int excess = localFallbackCache.size() - maxEntries;
        if (excess <= 0) {
            return;
        }
        localFallbackCache.entrySet().stream()
                .sorted(Comparator.comparingLong(entry -> entry.getValue().getSavedAtMillis()))
                .limit(excess)
                .map(Map.Entry::getKey)
                .forEach(localFallbackCache::remove);
    }

    /**
     * 按节流窗口异步写 Redis 快照。
     *
     * <p>同一资源在 {@link DegradeSnapshotProperties#getRedisThrottleSeconds()} 秒窗口内
     * 仅发起一次写入；窗口内再次保存只更新本地缓存。写入失败仅记日志，不影响主流程。</p>
     *
     * @param key      快照 key
     * @param resource 资源名称
     * @param data     响应数据
     */
    private void writeRedisThrottled(String key, String resource, Object data) {
        if (redisTemplate == null) {
            return;
        }
        long throttleMillis = properties.getRedisThrottleSeconds() * 1000L;
        long now = System.currentTimeMillis();
        boolean[] shouldWrite = {false};
        Long writeAt = lastRedisWriteAt.compute(resource, (ignored, lastWrite) -> {
            if (lastWrite == null || now - lastWrite >= throttleMillis) {
                shouldWrite[0] = true;
                return now;
            }
            return lastWrite;
        });
        if (!shouldWrite[0]) {
            return;
        }
        try {
            Mono<Boolean> setMono = redisTemplate.opsForValue().set(key, data, SNAPSHOT_TTL);
            if (setMono != null) {
                setMono.doOnSuccess(success -> {
                            if (!Boolean.TRUE.equals(success)) {
                                lastRedisWriteAt.remove(resource, writeAt);
                            }
                        })
                        .doOnError(e -> {
                            lastRedisWriteAt.remove(resource, writeAt);
                            log.warn("保存降级快照到 Redis 失败, resource: {}", resource, e);
                        })
                        .onErrorResume(e -> Mono.empty())
                        .subscribe();
            } else {
                lastRedisWriteAt.remove(resource, writeAt);
            }
        } catch (Exception e) {
            lastRedisWriteAt.remove(resource, writeAt);
            log.warn("保存降级快照到 Redis 异常, resource: {}", resource, e);
        }
    }

    /**
     * 构建降级响应，尝试返回缓存快照。
     *
     * <p>用于读接口的降级处理。如果快照命中，返回缓存数据并标记降级状态；
     * 如果未命中，返回标准降级错误。
     *
     * @param resource Sentinel 资源名称
     * @param traceId  链路追踪 ID
     * @return 包含降级响应的 Mono
     */
    public Mono<Result<Object>> buildDegradedReadResponse(String resource, String traceId) {
        return getSnapshot(resource)
                .map(snapshot -> {
                    log.info("降级快照命中, resource: {}, traceId: {}", resource, traceId);
                    return Result.success(snapshot, traceId);
                })
                .onErrorResume(e -> {
                    log.warn("降级快照获取异常, resource: {}, traceId: {}", resource, traceId, e);
                    return Mono.empty();
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("降级快照未命中, resource: {}, traceId: {}", resource, traceId);
                    return Mono.just(Result.error(2003, "服务暂时降级，请稍后重试", traceId));
                }));
    }

    /**
     * 构建写接口的降级响应，返回"排队中"状态。
     *
     * <p>写操作无法返回缓存数据，改为返回排队中状态，
     * 提示客户端请求已被接收但需稍后确认。
     *
     * @param resource Sentinel 资源名称
     * @param traceId  链路追踪 ID
     * @return 包含排队中状态的降级响应
     */
    public Mono<Result<Object>> buildDegradedWriteResponse(String resource, String traceId) {
        log.info("写接口降级, 返回排队中状态, resource: {}, traceId: {}", resource, traceId);
        return Mono.just(Result.error(2003, "系统繁忙，请求已排队，请稍后查询结果", traceId));
    }

    /**
     * 本地缓存条目：快照数据及其写入时间戳。
     *
     * <p>用于容量淘汰（按最旧时间戳淘汰）与惰性过期（读取时校验 TTL）。</p>
     */
    @lombok.Getter(lombok.AccessLevel.PRIVATE)
    private static final class LocalCacheEntry {

        /** 快照数据。 */
        private final Object data;

        /** 写入时间戳（毫秒）。 */
        private final long savedAtMillis;

        /**
         * 构造本地缓存条目。
         *
         * @param data          快照数据
         * @param savedAtMillis 写入时间戳（毫秒）
         */
        private LocalCacheEntry(Object data, long savedAtMillis) {
            this.data = data;
            this.savedAtMillis = savedAtMillis;
        }
    }
}
