package com.apex.scaffold.interfaces.aspect;

import static com.apex.scaffold.common.scheduler.BlockingIoSchedulers.cache;

import com.apex.scaffold.common.annotation.UserRateLimit;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.util.ratelimit.LocalRateLimiter;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 用户级别限流切面（支持 Redis 降级到本地限流）
 *
 * <p>基于 Redisson {@link RRateLimiter} 实现声明式用户级别速率限制。
 * 当 Redis 连接异常或 Redisson 不可用时，自动降级到 {@link LocalRateLimiter}
 * 本地限流方案，确保限流能力不因外部依赖故障而完全失效。
 *
 * <h2>降级策略</h2>
 * <ul>
 *   <li><strong>正常模式</strong>：基于 Redis 的分布式限流，精确控制用户级别速率</li>
 *   <li><strong>降级模式</strong>：基于 {@link LocalRateLimiter} 的本地限流，
 *       使用滑动窗口 + Semaphore 实现进程内限流</li>
 *   <li><strong>降级判定</strong>：捕获 {@link RedisException} 异常后自动降级，
 *       每分钟尝试恢复 Redis 连接</li>
 * </ul>
 *
 * <h2>线程模型</h2>
 * <p>Redisson 的 {@link RRateLimiter#tryAcquire()} 为同步阻塞 API，
 * 本切面通过 {@link Mono#fromCallable(java.util.concurrent.Callable)} 与
 * {@link com.apex.scaffold.common.scheduler.BlockingIoSchedulers#cache()} 缓存专用池
 * 将阻塞操作隔离执行，避免阻塞 WebFlux 事件循环线程，也不挤占 JDBC 核心池。
 * 本地限流降级路径同样使用 cache() 池承载。</p>
 *
 * @author Apex Team
 * @version 3.1.0
 * @since 1.0.0
 * @see UserRateLimit
 * @see LocalRateLimiter
 * @see RedissonClient
 */
@Slf4j
@Aspect
@Component
@Order(1)
@ConditionalOnProperty(
        prefix = "apex.security.ip-rate-limit",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class UserRateLimitAspect {

    /** SpEL 表达式前缀标识 */
    private static final String SPEL_PREFIX = "#";

    /** Redis 限流键前缀 */
    private static final String RATE_KEY_PREFIX = "rate:user:";

    /** SpEL 表达式解析器 */
    private final SpelExpressionParser parser = new SpelExpressionParser();

    /** 参数名发现器，用于解析 SpEL 表达式中的方法参数名。 */
    private final ParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    /** Redisson 客户端（可为 null，不依赖注入强制检查） */
    private final RedissonClient redissonClient;

    /**
     * 是否启用本地限流降级
     *
     * <p>通过配置项 {@code apex.ratelimit.local-fallback.enabled} 控制，
     * 默认开启。当 Redis 不可用时，自动降级到本地限流。
     */
    @Value("${apex.ratelimit.local-fallback.enabled:true}")
    private boolean localFallbackEnabled;

    /**
     * 构造用户级别限流切面
     *
     * @param redissonClient Redisson 客户端
     */
    public UserRateLimitAspect(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 用户限流环绕通知（支持自动降级）
     *
     * <p>拦截所有标注 {@link UserRateLimit} 注解的方法，按以下流程执行：
     * <ol>
     *   <li>优先尝试 Redisson 分布式限流</li>
     *   <li>若 Redis 异常且本地降级已启用，使用 {@link LocalRateLimiter} 本地限流</li>
     *   <li>失败时抛出 {@link BusinessException}</li>
     * </ol>
     *
     * @param joinPoint     连接点
     * @param userRateLimit 用户限流注解
     * @return 目标方法执行结果
     * @throws Throwable 执行异常或限流拒绝
     */
    @Around("@annotation(userRateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, UserRateLimit userRateLimit) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();

        String key = parseKey(userRateLimit.key(), method, args);
        String rateKey = RATE_KEY_PREFIX + userRateLimit.prefix() + ":" + key;
        long rate = (long) userRateLimit.rate();
        long rateInterval = userRateLimit.rateInterval();

        return Mono.fromCallable(() -> {
                    // Step 1: 尝试 Redis 分布式限流
                    boolean acquired = tryAcquireDistributed(rateKey, rate, rateInterval);
                    if (acquired) {
                        log.debug("分布式限流通过, key: {}", rateKey);
                        return true;
                    }
                    return false;
                })
                .subscribeOn(cache())
                .onErrorResume(throwable -> {
                    // Step 2: Redis 异常，降级到本地限流
                    if (localFallbackEnabled && isRedisException(throwable)) {
                        log.warn("Redis 不可用，降级到本地限流, key: {}", rateKey, throwable);
                        return Mono.fromCallable(() -> {
                                    LocalRateLimiter localLimiter =
                                            LocalRateLimiter.of(rateKey, (int) rate, rateInterval * 1000L);
                                    return localLimiter.tryAcquire();
                                })
                                .subscribeOn(cache())
                                .cast(Boolean.class);
                    }
                    log.error("限流检查异常, key: {}", rateKey, throwable);
                    return Mono.error(throwable);
                })
                .flatMap(acquired -> {
                    if (Boolean.FALSE.equals(acquired)) {
                        log.warn("用户限流拒绝, key: {}, rate: {}/{}s", rateKey, rate, rateInterval);
                        return Mono.error(new BusinessException(userRateLimit.message()));
                    }
                    try {
                        return Mono.fromCallable(() -> {
                            try {
                                return joinPoint.proceed();
                            } catch (Throwable e) {
                                throw new RuntimeException(e);
                            }
                        });
                    } catch (Throwable e) {
                        return Mono.error(e);
                    }
                });
    }

    /**
     * 尝试获取 Redis 分布式限流许可
     *
     * <p>通过 Redisson {@link RRateLimiter#tryAcquire()} 获取分布式限流许可。
     * 若 RedissonClient 为 null，直接降级（返回 false 以触发本地降级路径）。
     *
     * @param rateKey      限流键
     * @param rate         限流速率
     * @param rateInterval 时间窗口（秒）
     * @return 获取成功返回 true
     */
    private boolean tryAcquireDistributed(String rateKey, long rate, long rateInterval) {
        if (redissonClient == null) {
            return false;
        }
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(rateKey);
        rateLimiter.trySetRate(RateType.OVERALL, rate, rateInterval, RateIntervalUnit.SECONDS);
        return rateLimiter.tryAcquire();
    }

    /**
     * 判断异常是否为 Redis 连接异常
     *
     * <p>判断依据：异常类型为 {@link RedisException} 或其子类，
     * 此类异常表示 Redis 连接不可用或操作超时。
     *
     * @param throwable 待判断的异常
     * @return 如果是 Redis 异常返回 true
     */
    private boolean isRedisException(Throwable throwable) {
        if (throwable instanceof RedisException) {
            return true;
        }
        Throwable cause = throwable.getCause();
        return cause instanceof RedisException;
    }

    /**
     * 解析 SpEL 限流键表达式
     *
     * @param key    注解中的 key 表达式
     * @param method 目标方法
     * @param args   方法参数值数组
     * @return 解析后的限流键字符串
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
        String value = expression.getValue(context, String.class);
        if (value == null) {
            return UUID.randomUUID().toString().substring(0, 8);
        }
        return value;
    }
}
