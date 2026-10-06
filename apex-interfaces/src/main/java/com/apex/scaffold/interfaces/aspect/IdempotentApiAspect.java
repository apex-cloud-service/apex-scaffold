package com.apex.scaffold.interfaces.aspect;

import com.apex.scaffold.common.annotation.IdempotentApi;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.interfaces.monitor.SecurityMetricsRecorder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * HTTP 写接口幂等切面。
 *
 * <p>拦截标注 {@link IdempotentApi} 的方法，基于 Redis SETNX 原子操作实现分布式幂等去重：
 * 同一幂等键（默认取自 {@code Idempotency-Key} 请求头）在 TTL 窗口内的重复请求直接返回
 * {@link ResultCode#REPEAT_SUBMIT}，业务方法不会被执行第二次，解决客户端网络超时重试
 * 导致的重复扣款/重复下单等副作用。
 *
 * <h2>幂等键合成</h2>
 * <pre>
 *   apex:idem:{sha256Hex(请求路径 + "|" + 幂等键头值 + "|" + 客户端IP)}
 * </pre>
 * 路径维度隔离不同接口；IP 维度防止不同客户端复用同一键值；业务动作唯一性由客户端
 * 为每个动作生成唯一 UUID 放入请求头保证。
 *
 * <h2>并发与失败语义</h2>
 * <ul>
 *   <li><b>并发保护</b>：SETNX 原子性保证同一幂等键并发到达时仅一个请求获得执行权，
 *       其余立即收到 {@code REPEAT_SUBMIT}</li>
 *   <li><b>5xx 异常保留键</b>：业务方法抛出 {@code IllegalStateException}/DAO/IO 等瞬态异常时，
 *       数据库写入可能已部分落盘，<b>绝不删除幂等键</b>，由 TTL 兜底保证窗口内绝对不可重入；
 *       避免"写已落盘 → delete(key) → 下一请求穿过 setIfAbsent 再次写库"的竞态窗口</li>
 *   <li><b>4xx 客户端异常释放键</b>：仅当方法抛 {@link BusinessException}（客户端可通过换参/换幂等键
 *       重试的业务错误）时删除幂等键，允许下一次合法重试</li>
 *   <li><b>成功保留</b>：业务成功后幂等键保留至 TTL 过期，窗口内重复请求被拒绝
 *       （此时操作已生效，客户端应查询结果而非重放）</li>
 *   <li><b>Redis 不可用</b>：记录错误日志并放行（fail-open），避免写接口整体不可用；
 *       数据库唯一约束作为最终兜底</li>
 * </ul>
 *
 * <h2>响应式适配</h2>
 * <p>兼容函数式端点（{@link ServerRequest}）与注解式端点（{@link ServerWebExchange}）两种
 * 方法参数形态；目标方法返回 {@link Mono} 时在其响应流上串联去重/释放逻辑，返回同步值时
 * 直接放行。与 {@code AntiReplayAspect} 同样采用「方法参数中查找请求对象」的模式，
 * 未找到请求对象时记录 WARN 并放行。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.2.0
 * @see IdempotentApi
 * @see AntiReplayAspect
 */
@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class IdempotentApiAspect {

    private final AtomicLong rejected = new AtomicLong();

    private void recordRejection(String reason) {
        securityMetricsRecorder.recordRepeatSubmitRejected();
        long count = rejected.incrementAndGet();
        if (count > 0 && (count & (count - 1)) == 0) {
            log.warn("HTTP 幂等拒绝: reason={}, total={}", reason, count);
        }
    }

    /** Redis 幂等键前缀。 */
    private static final String IDEMPOTENT_KEY_PREFIX = "apex:idem:";

    /** 占位值：键存在即表示处理中或已完成。 */
    private static final String PLACEHOLDER_VALUE = "1";

    /** 响应式 Redis 模板，用于幂等键的原子写入与释放。 */
    private final ReactiveStringRedisTemplate redisTemplate;

    /** 安全指标记录器，记录幂等拦截事件到 Prometheus 指标。 */
    private final SecurityMetricsRecorder securityMetricsRecorder;

    /**
     * 构造 HTTP 幂等切面。
     *
     * @param redisTemplate 响应式 Redis 字符串模板
     * @param securityMetricsRecorder 安全指标记录器
     */
    public IdempotentApiAspect(
            ReactiveStringRedisTemplate redisTemplate, SecurityMetricsRecorder securityMetricsRecorder) {
        this.redisTemplate = redisTemplate;
        this.securityMetricsRecorder = securityMetricsRecorder;
    }

    /**
     * HTTP 写接口幂等环绕通知。
     *
     * <p>从方法参数中解析请求对象（{@link ServerWebExchange} 或 {@link ServerRequest}），
     * 提取幂等键头并执行 Redis SETNX 去重。重复请求抛 {@link ResultCode#REPEAT_SUBMIT}；
     * 首次请求执行业务方法，失败时删除幂等键允许重试。</p>
     *
     * @param joinPoint 连接点
     * @param idempotentApi 幂等注解实例
     * @return 目标方法执行结果（Mono 或同步值）
     * @throws Throwable 幂等校验失败抛 {@link BusinessException}，或目标方法执行异常
     */
    @Around("@annotation(idempotentApi)")
    public Object around(ProceedingJoinPoint joinPoint, IdempotentApi idempotentApi) throws Throwable {
        RequestContext context = resolveRequestContext(joinPoint.getArgs());
        if (context == null) {
            // 防御性日志：AOP 代理下签名恒非空，但单元测试 mock 场景可能为空
            log.warn("未找到 ServerWebExchange/ServerRequest 参数，跳过 HTTP 幂等校验: {}", signatureText(joinPoint));
            return joinPoint.proceed();
        }

        String headerValue = context.header(idempotentApi.header());
        if (headerValue == null || headerValue.isBlank()) {
            if (idempotentApi.required()) {
                recordRejection("missing-key");
                throw new BusinessException(ResultCode.IDEMPOTENCY_KEY_MISSING);
            }
            // 渐进接入：缺头放行，带上头即获得幂等保护
            return joinPoint.proceed();
        }

        String cacheKey = buildCacheKey(context.path(), headerValue, context.clientIp());
        long expireSeconds = idempotentApi.expireTime() > 0 ? idempotentApi.expireTime() : 600L;

        return redisTemplate
                .opsForValue()
                .setIfAbsent(cacheKey, PLACEHOLDER_VALUE, Duration.ofSeconds(expireSeconds))
                .flatMap(acquired -> {
                    if (Boolean.FALSE.equals(acquired)) {
                        recordRejection("duplicate-key");
                        return Mono.error(new BusinessException(ResultCode.REPEAT_SUBMIT));
                    }
                    try {
                        Object result = joinPoint.proceed();
                        if (result instanceof Mono<?> mono) {
                            return mono.cast(Object.class)
                                    .onErrorResume(error -> isClientError(error)
                                            ? release(cacheKey).then(Mono.error(error))
                                            : Mono.error(error));
                        }
                        // 同步返回：无异常即视为成功，保留幂等键至 TTL 过期
                        return Mono.just(result);
                    } catch (Throwable error) {
                        return isClientError(error)
                                ? release(cacheKey).then(Mono.error(unwrap(error)))
                                : Mono.error(unwrap(error));
                    }
                })
                .onErrorResume(
                        error -> isRedisUnavailable(error) ? fallbackToProceed(joinPoint, error) : Mono.error(error));
    }

    /**
     * Redis 不可用时放行（fail-open），记录错误日志避免写接口整体不可用。
     *
     * @param joinPoint 连接点
     * @param error Redis 访问异常
     * @return 目标方法执行结果
     */
    private Mono<Object> fallbackToProceed(ProceedingJoinPoint joinPoint, Throwable error) {
        log.error("Redis 不可用，HTTP 幂等校验降级放行: type={}", error.getClass().getSimpleName());
        try {
            Object result = joinPoint.proceed();
            if (result instanceof Mono<?> mono) {
                return mono.cast(Object.class);
            }
            return Mono.just(result);
        } catch (Throwable proceedError) {
            return Mono.error(unwrap(proceedError));
        }
    }

    /**
     * 判断异常是否为 Redis 访问异常（幂等键 SETNX 失败）。
     *
     * @param error 异常
     * @return 是否为 Redis 异常
     */
    private boolean isRedisUnavailable(Throwable error) {
        if (error instanceof BusinessException) {
            return false;
        }
        Throwable cause = error;
        while (cause != null) {
            String name = cause.getClass().getName();
            if (name.contains("Redis")
                    || name.contains("RedisConnection")
                    || cause instanceof org.springframework.data.redis.RedisConnectionFailureException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    /**
     * 判断异常是否属于"客户端可换参重试"的确定性 4xx 业务错误。
     *
     * <p>仅 {@link BusinessException} 通过——意味着请求语义不合法（缺少参数、状态错、
     * 权限不足等），幂等键不会掩盖数据，允许释放以便客户端使用合法参数或新键重试。
     * 任何 IO/DB/框架级瞬态异常一律视为"可能已写库"的 5xx，保留幂等键到 TTL。</p>
     *
     * @param error 错误
     * @return {@code true} 表示确定性客户端错误，可安全释放幂等键
     */
    private boolean isClientError(Throwable error) {
        return unwrap(error) instanceof BusinessException;
    }

    /**
     * 删除幂等键，允许失败后的重试。
     *
     * @param cacheKey 幂等键
     * @return 删除完成信号
     */
    private Mono<Void> release(String cacheKey) {
        return redisTemplate.opsForValue().delete(cacheKey).then();
    }

    /**
     * 解包 {@link RuntimeException} 为原始异常。
     *
     * @param error 异常
     * @return 原始异常
     */
    private Throwable unwrap(Throwable error) {
        return error instanceof RuntimeException runtimeException && runtimeException.getCause() != null
                ? runtimeException.getCause()
                : error;
    }

    /**
     * 构建 Redis 幂等键：{@code apex:idem:{sha256Hex(path|header|ip)}}。
     *
     * @param path 请求路径
     * @param headerValue 幂等键头值
     * @param clientIp 客户端 IP
     * @return 完整 Redis 键
     */
    private String buildCacheKey(String path, String headerValue, String clientIp) {
        String raw = path + "|" + headerValue + "|" + clientIp;
        return IDEMPOTENT_KEY_PREFIX + sha256Hex(raw);
    }

    /**
     * 计算 SHA-256 十六进制摘要。
     *
     * @param input 输入字符串
     * @return 十六进制摘要
     */
    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 在 JDK 中必然存在，此处仅作防御
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }

    /**
     * 从方法参数中解析请求上下文，兼容函数式端点与注解式端点。
     *
     * <p>基于 {@link AspectRequestUtil#resolveRequestMeta(Object[])} 将任意请求形态
     * 归一化为 {@link AspectRequestUtil.RequestMeta}，统一提取路径、请求头与客户端 IP；
     * 函数式端点直接消费 {@link ServerRequest} 自身的请求信息，不依赖
     * {@code exchange()} 转换。</p>
     *
     * @param args 方法参数数组
     * @return 请求上下文，未找到请求对象时返回 {@code null}
     */
    private static RequestContext resolveRequestContext(Object[] args) {
        AspectRequestUtil.RequestMeta meta = AspectRequestUtil.resolveRequestMeta(args);
        if (meta == null) {
            return null;
        }
        return new RequestContext(meta.path(), meta.headers(), AspectRequestUtil.resolveClientIp(meta));
    }

    /** 请求上下文：路径、请求头、客户端 IP 的轻量快照。 */
    private record RequestContext(String path, HttpHeaders headers, String clientIp) {

        /** 读取指定请求头首值。 */
        String header(String name) {
            return headers.getFirst(name);
        }
    }

    /**
     * 安全获取连接点方法签名文本（用于日志）。
     *
     * <p>AOP 运行时签名恒非空；此方法防御 mock 或异常场景下
     * {@code getSignature()} 返回 {@code null} 导致的 NPE。</p>
     *
     * @param joinPoint 连接点
     * @return 方法签名短文本，不可用时返回 {@code "-"}
     */
    private static String signatureText(ProceedingJoinPoint joinPoint) {
        if (joinPoint.getSignature() == null) {
            return "-";
        }
        return joinPoint.getSignature().toShortString();
    }
}
