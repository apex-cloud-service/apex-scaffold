package com.apex.scaffold.interfaces.aspect;

import com.apex.scaffold.common.annotation.HotSpotRateLimit;
import com.apex.scaffold.common.exception.BusinessException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 热点参数限流切面
 *
 * <p>拦截标注 {@link HotSpotRateLimit} 注解的方法，对高频访问的热点参数
 * 进行精细化限流保护。通过 Redis 滑动窗口计数器实时监测每个参数值的访问频率，
 * 当达到热点阈值时自动应用更严格的限流规则。
 *
 * <h2>实现原理</h2>
 * <ol>
 * <li><strong>参数监控</strong>：解析 SpEL 表达式获取方法参数值</li>
 * <li><strong>滑动窗口计数</strong>：每个参数值在 Redis 中维护独立的
 *     滑动窗口计数器，使用原子操作保证并发安全</li>
 * <li><strong>热点判定</strong>：当窗口内请求总数超过 {@code hotThreshold} 时，
 *     该参数值被标记为"热点"</li>
 * <li><strong>热点限流</strong>：对热点参数应用 {@code limit} 指定的严格阈值</li>
 * <li><strong>自动降温</strong>：Redis 键自动过期，热点标记自动解除</li>
 * </ol>
 *
 * <h2>存储机制</h2>
 * <ul>
 * <li>使用 {@link RedissonClient} Redis 分布式存储，支持多实例共享热点状态</li>
 * <li>每个热点 Key 维护两个计数器：槽位计数器和窗口总计数器</li>
 * <li>Redis TTL 自动清理过期的热点数据，避免内存泄露</li>
 * </ul>
 *
 * <h2>线程安全设计</h2>
 * <ul>
 * <li>使用 Redis 原子操作（INCR）保证计数的原子性</li>
 * <li>Redis 天然支持分布式锁，无需额外锁机制</li>
 * <li>定时任务定期清理过期的槽位数据</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 * @see HotSpotRateLimit
 * @see RedissonClient
 */
@Slf4j
@Aspect
@Component
@Order(2)
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "apex.security.ip-rate-limit",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class HotSpotRateLimitAspect {

    /** SpEL 表达式前缀标识 */
    private static final String SPEL_PREFIX = "#";

    /** 滑动窗口槽位 Key 前缀 */
    private static final String SLOT_KEY_PREFIX = "hotspot:slot:";

    /** 窗口总计数 Key 前缀 */
    private static final String WINDOW_KEY_PREFIX = "hotspot:window:";

    /** 热点标记 Key 前缀 */
    private static final String HOT_KEY_PREFIX = "hotspot:flag:";

    /**
     * Lua脚本：批量求窗口内所有槽位计数之和。
     * KEYS[1] = 槽位Key前缀 (SLOT_KEY_PREFIX + baseKey)
     * ARGV[1] = 窗口起始秒 (startSecond)
     * ARGV[2] = 窗口结束秒 (currentSecond)
     */
    private static final String LUA_SUM_SLOTS = "local total = 0\n"
            + "local startSecond = tonumber(ARGV[1])\n"
            + "local endSecond = tonumber(ARGV[2])\n"
            + "local keyPrefix = KEYS[1]\n"
            + "for i = startSecond, endSecond do\n"
            + "    local val = redis.call('GET', keyPrefix .. ':' .. i)\n"
            + "    if val then\n"
            + "        total = total + tonumber(val)\n"
            + "    end\n"
            + "end\n"
            + "return total";

    /** Redis 客户端 */
    private final RedissonClient redissonClient;

    /** SpEL 表达式解析器 */
    private final SpelExpressionParser parser = new SpelExpressionParser();

    /** 参数名发现器，用于解析 SpEL 表达式中的方法参数名以提取热点参数值。 */
    private final ParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    /**
     * 热点参数限流环绕通知
     *
     * <p>执行流程：
     * <ol>
     * <li>解析 SpEL 表达式获取热点参数值</li>
     * <li>更新滑动窗口计数器（Redis 原子操作）</li>
     * <li>判断是否达到热点阈值</li>
     * <li>若为热点数据，检查是否超过热点限流阈值</li>
     * <li>限流通过或非热点时执行目标方法</li>
     * </ol>
     *
     * @param joinPoint 连接点
     * @param annotation 热点限流注解
     * @return 目标方法执行结果
     * @throws Throwable 执行异常或限流拒绝
     */
    @Around("@annotation(annotation)")
    public Object around(ProceedingJoinPoint joinPoint, HotSpotRateLimit annotation) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();

        String keyValue = parseKey(annotation.key(), method, args);
        String baseKey = annotation.prefix() + ":" + keyValue;

        long now = System.currentTimeMillis();
        int windowSeconds = annotation.window();
        int hotThreshold = annotation.hotThreshold();
        int limit = annotation.limit();

        // 获取当前槽位索引（每秒一个槽位）
        int slotIndex = (int) (now / 1000);
        String slotKey = SLOT_KEY_PREFIX + baseKey + ":" + slotIndex;
        String windowKey = WINDOW_KEY_PREFIX + baseKey;

        // 递增当前槽位计数
        RAtomicLong slotCounter = redissonClient.getAtomicLong(slotKey);
        int currentCount = (int) slotCounter.incrementAndGet();

        // 设置槽位过期时间（窗口时间 + 1秒缓冲）
        slotCounter.expire(Duration.ofSeconds(windowSeconds + 1));

        // 计算窗口内总请求数（通过遍历所有有效槽位）
        int windowCount = calculateWindowCount(baseKey, now, windowSeconds);

        // 更新窗口总计数（用于快速判断热点）
        RAtomicLong windowCounter = redissonClient.getAtomicLong(windowKey);
        windowCounter.set(windowCount);
        windowCounter.expire(Duration.ofSeconds(windowSeconds + 1));

        // 判定是否为热点数据
        boolean isHotSpot = windowCount > hotThreshold;

        if (isHotSpot) {
            // 热点数据：应用严格限流
            if (currentCount > limit) {
                log.warn(
                        "热点参数限流拒绝, key: {}, windowCount: {}, hotThreshold: {}, limit: {}",
                        baseKey,
                        windowCount,
                        hotThreshold,
                        limit);
                return Mono.error(new BusinessException(annotation.message()));
            }
            // 标记为热点，设置过期时间
            redissonClient.getBucket(HOT_KEY_PREFIX + baseKey).set(true, Duration.ofSeconds(windowSeconds));
            log.debug("热点参数限流通过, key: {}, windowCount: {}, currentSlotCount: {}", baseKey, windowCount, currentCount);
        }

        // 执行目标方法
        return joinPoint.proceed();
    }

    /**
     * 计算窗口内的请求总数（Redis Lua脚本批量求和，单次网络往返）。
     *
     * <p>使用 Lua EVAL 在 Redis 服务端遍历窗口内所有槽位并累加计数，
     * 替代原有的 O(n) 次网络往返，显著降低热点限流对 Redis 的网络开销。</p>
     *
     * @param baseKey      热点参数基础键
     * @param now          当前时间戳（毫秒）
     * @param windowSeconds 窗口大小（秒）
     * @return 窗口内的请求总数
     */
    private int calculateWindowCount(String baseKey, long now, int windowSeconds) {
        long currentSecond = now / 1000;
        long startSecond = currentSecond - windowSeconds + 1;

        RScript script = redissonClient.getScript();
        List<Object> keys = Collections.singletonList(SLOT_KEY_PREFIX + baseKey);
        Long total = script.eval(
                RScript.Mode.READ_ONLY, LUA_SUM_SLOTS, RScript.ReturnType.INTEGER, keys, startSecond, currentSecond);
        return total != null ? total.intValue() : 0;
    }

    /**
     * 解析 SpEL 热点键表达式
     *
     * @param key    注解中的 key 表达式
     * @param method 目标方法
     * @param args   方法参数值数组
     * @return 解析后的热点键字符串
     */
    private String parseKey(String key, Method method, Object[] args) {
        if (!key.contains(SPEL_PREFIX)) {
            return key;
        }
        EvaluationContext context = new StandardEvaluationContext();
        String[] parameterNames = nameDiscoverer.getParameterNames(method);
        if (parameterNames != null) {
            IntStream.range(0, parameterNames.length).forEach(i -> context.setVariable(parameterNames[i], args[i]));
        }
        Expression expression = parser.parseExpression(key);
        Object value = expression.getValue(context);
        return value != null ? value.toString() : "null";
    }

    /**
     * 定时清理过期的热点标记（每5分钟执行一次）
     *
     * <p>虽然 Redis 会自动过期，但此方法作为额外的安全保障，
     * 清理可能由于某种原因未自动过期的键。
     */
    @Scheduled(fixedRate = 300000, initialDelay = 60000)
    public void cleanExpiredHotFlags() {
        try {
            Iterable<String> keys = redissonClient.getKeys().getKeysByPattern(HOT_KEY_PREFIX + "*");
            for (String key : keys) {
                if (redissonClient.getBucket(key).get() == null) {
                    redissonClient.getBucket(key).delete();
                }
            }
            log.debug("热点标记清理任务执行完成");
        } catch (Exception e) {
            log.error("热点标记清理任务执行失败", e);
        }
    }

    /**
     * 获取热点参数的当前窗口计数
     *
     * <p>供监控和管理接口使用，查询指定热点参数的当前访问频率。
     *
     * @param baseKey 热点参数基础键
     * @return 当前窗口内的请求数
     */
    public int getWindowCount(String baseKey) {
        RAtomicLong counter = redissonClient.getAtomicLong(WINDOW_KEY_PREFIX + baseKey);
        return (int) counter.get();
    }

    /**
     * 判断指定参数是否为热点
     *
     * @param baseKey 热点参数基础键
     * @return 如果是热点返回 true
     */
    public boolean isHotSpot(String baseKey) {
        return Boolean.TRUE.equals(
                redissonClient.getBucket(HOT_KEY_PREFIX + baseKey).get());
    }

    /**
     * 手动清除指定热点参数的限流状态
     *
     * <p>供管理接口使用，手动解除热点标记。
     *
     * @param baseKey 热点参数基础键
     */
    public void clearHotSpot(String baseKey) {
        redissonClient.getKeys().delete(SLOT_KEY_PREFIX + baseKey + ":*");
        redissonClient.getBucket(WINDOW_KEY_PREFIX + baseKey).delete();
        redissonClient.getBucket(HOT_KEY_PREFIX + baseKey).delete();
        log.info("热点参数状态已清除: {}", baseKey);
    }
}
