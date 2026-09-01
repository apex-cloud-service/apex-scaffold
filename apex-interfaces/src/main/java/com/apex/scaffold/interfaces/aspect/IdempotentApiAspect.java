package com.apex.scaffold.interfaces.aspect;

import com.apex.scaffold.common.annotation.IdempotentApi;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.interfaces.monitor.SecurityMetricsRecorder;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
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
 *   <li><b>失败释放</b>：业务方法抛出异常时删除幂等键，允许客户端换新键或窗口后重试</li>
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

    /** Redis 幂等键前缀。 */
    private static final String IDEMPOTENT_KEY_PREFIX = "apex:idem:";

    /** 占位值：键存在即表示处理中或已完成。 */
    private static final String PLACEHOLDER_VALUE = "1";

    /** 未知 IP 标识，与 {@code RequestUtil.UNKNOWN_IP} 语义一致。 */
    private static final String UNKNOWN_IP = "unknown";

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
                log.warn("HTTP 幂等校验失败，缺少 {} 请求头, path: {}", idempotentApi.header(), context.path());
                securityMetricsRecorder.recordRepeatSubmitRejected();
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
                        log.warn("HTTP 幂等拒绝重复提交, key: {}, path: {}", cacheKey, context.path());
                        securityMetricsRecorder.recordRepeatSubmitRejected();
                        return Mono.error(new BusinessException(ResultCode.REPEAT_SUBMIT));
                    }
                    try {
                        Object result = joinPoint.proceed();
                        if (result instanceof Mono<?> mono) {
                            return mono.cast(Object.class)
                                    .onErrorResume(error -> release(cacheKey).then(Mono.error(error)));
                        }
                        // 同步返回：无异常即视为成功，保留幂等键至 TTL 过期
                        return Mono.just(result);
                    } catch (Throwable error) {
                        return release(cacheKey).then(Mono.error(unwrap(error)));
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
        log.error("Redis 不可用，HTTP 幂等校验降级放行: {}", error.getMessage(), error);
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
     * @param args 方法参数数组
     * @return 请求上下文，未找到请求对象时返回 {@code null}
     */
    private static RequestContext resolveRequestContext(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof ServerWebExchange exchange) {
                ServerHttpRequest request = exchange.getRequest();
                return new RequestContext(
                        request.getURI().getPath(),
                        request.getHeaders(),
                        com.apex.scaffold.common.util.RequestUtil.getClientIp(request));
            }
        }
        for (Object arg : args) {
            if (arg instanceof ServerRequest request) {
                return new RequestContext(
                        request.path(), request.headers().asHttpHeaders(), clientIpFromServerRequest(request));
            }
        }
        return null;
    }

    /**
     * 从函数式 {@link ServerRequest} 提取客户端 IP（与 {@code RequestUtil} 语义一致）。
     *
     * @param request 函数式请求
     * @return 客户端 IP，无法获取时返回 {@code unknown}
     */
    private static String clientIpFromServerRequest(ServerRequest request) {
        HttpHeaders headers = request.headers().asHttpHeaders();
        String forwardedFor = headers.getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = headers.getFirst("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        Optional<InetSocketAddress> remoteAddress = request.remoteAddress();
        if (remoteAddress.isPresent() && remoteAddress.get().getAddress() != null) {
            return remoteAddress.get().getAddress().getHostAddress();
        }
        return UNKNOWN_IP;
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
