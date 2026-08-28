package com.apex.example.service;

import com.apex.scaffold.common.util.DataMaskUtil;
import com.apex.scaffold.infrastructure.cache.service.MultiLevelCacheService;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 示例能力演示服务
 *
 * <p>集中演示脚手架核心通用能力的标准用法，每个方法对应一类能力：
 * <ul>
 *   <li>{@link #getCachedValue} - 多级缓存（Caffeine + Redis）读穿/加载，含缓存击穿 Single-Flight 合并</li>
 *   <li>{@link #evictCache} - 缓存主动失效</li>
 *   <li>{@link #maskDemo} - 敏感数据脱敏（手机号/身份证/邮箱/姓名）</li>
 *   <li>{@link #streamHeartbeat} - 响应式 SSE 实时推送</li>
 * </ul>
 *
 * <p>本服务仅作演示：真实业务应将 {@link MultiLevelCacheService} 等
 * 基础设施能力封装在各自的领域服务/仓储中，保持 DDD 分层约束。</p>
 *
 * @author Apex Team
 * @version 1.1.4
 * @since 1.1.4
 */
@Service
public class ExampleCapabilityService {

    /** 缓存键前缀，演示数据与业务数据隔离。 */
    private static final String CACHE_KEY_PREFIX = "example:capability:";

    /** 缓存过期时间（秒）：演示读穿场景下数据兜底。 */
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    /** 模拟底层存储的计数器：每次"未命中缓存"都会递增，用于观察缓存命中。 */
    private final AtomicLong storageCounter = new AtomicLong(0);

    /** 脚手架多级缓存服务（Caffeine 一级 + Redis 二级，含击穿合并）。 */
    private final MultiLevelCacheService cacheService;

    /**
     * 构造能力演示服务。
     *
     * @param cacheService 多级缓存服务
     */
    public ExampleCapabilityService(MultiLevelCacheService cacheService) {
        this.cacheService = cacheService;
    }

    /**
     * 读取演示计数器（多级缓存读穿示例）。
     *
     * <p>首次调用会执行 {@code loader}（模拟查库），结果写入缓存；
     * 后续调用直接命中缓存，不再递增 {@code storageCounter}。
     * 并发首次访问由 {@code InFlightRequestMerge} 合并为单次加载，防止缓存击穿。</p>
     *
     * @param key 业务键（如 demo）
     * @return 响应式结果：{@code value} 当前计数，{@code hits} 底层加载次数
     */
    public Mono<Map<String, Object>> getCachedValue(String key) {
        String cacheKey = CACHE_KEY_PREFIX + key;
        // getWithLoader：缓存未命中时执行 loader 并回填；同一合并窗口内并发请求共享一次加载
        return cacheService
                .getWithLoader(
                        cacheKey,
                        Map.class,
                        () -> {
                            long current = storageCounter.incrementAndGet();
                            Map<String, Object> value = new LinkedHashMap<>();
                            value.put("value", current);
                            value.put("loadedAt", System.currentTimeMillis());
                            return Mono.just(value);
                        },
                        CACHE_TTL)
                .map(value -> {
                    Map<String, Object> result = new LinkedHashMap<>(value);
                    result.put("storageHits", storageCounter.get());
                    return result;
                });
    }

    /**
     * 主动失效指定缓存键（缓存一致性示例）。
     *
     * @param key 业务键（与 {@link #getCachedValue} 对应）
     * @return 响应式结果：删除的缓存键数量
     */
    public Mono<Long> evictCache(String key) {
        return cacheService.delete(CACHE_KEY_PREFIX + key);
    }

    /**
     * 敏感数据脱敏演示。
     *
     * <p>演示 {@link DataMaskUtil} 对手机号、身份证、邮箱、姓名的标准脱敏输出，
     * 供日志输出、列表展示等场景直接复用。</p>
     *
     * @param phone    手机号
     * @param idCard   身份证号
     * @param email    邮箱
     * @param name     姓名
     * @return 脱敏后的字段映射
     */
    public Map<String, String> maskDemo(String phone, String idCard, String email, String name) {
        Map<String, String> masked = new LinkedHashMap<>();
        masked.put("phone", DataMaskUtil.maskPhone(phone));
        masked.put("idCard", DataMaskUtil.maskIdCard(idCard));
        masked.put("email", DataMaskUtil.maskEmail(email));
        masked.put("name", DataMaskUtil.maskName(name));
        return masked;
    }

    /**
     * 响应式 SSE 实时推送演示。
     *
     * <p>以固定间隔推送心跳消息（{@code text/event-stream}），展示 WebFlux
     * 响应式流在服务端推送场景的应用。真实场景可替换为业务事件源
     * （如通知、进度、行情），并配合 {@code GlobalResponseTimeoutWebFilter}
     * 的 SSE 豁免策略使用。</p>
     *
     * @return 心跳消息流（调用方取消订阅时自动终止）
     */
    public Flux<String> streamHeartbeat() {
        return Flux.interval(Duration.ofSeconds(1))
                .map(seq -> "heartbeat-%d".formatted(seq))
                .take(10)
                .doOnCancel(() -> {
                    /* 客户端断开时清理资源（演示为空实现） */
                });
    }
}
