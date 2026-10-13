package com.apex.scaffold.interfaces.aspect;

import com.apex.scaffold.common.annotation.AntiReplay;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.interfaces.monitor.SecurityMetricsRecorder;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 防重放攻击切面
 *
 * <p>基于 {@link AntiReplay} 注解实现请求重放防护，防止同一请求被重复提交。
 * 通过校验请求头中的时间戳（X-Timestamp）和随机数（X-Nonce）来识别重放请求。
 *
 * <h2>工作原理</h2>
 * <ol>
 *   <li>从目标方法参数中提取请求上下文（函数式 {@link ServerRequest} 或注解式 {@link ServerWebExchange}），
 *       获取请求头</li>
 *   <li>校验 {@code X-Timestamp} 与服务器当前时间的偏差是否在允许范围内</li>
 *   <li>校验 {@code X-Nonce} 是否已存在于 Redis（SET NX EX 原子操作）</li>
 *   <li>任一校验失败则抛出 {@link BusinessException}，拒绝请求执行</li>
 * </ol>
 *
 * <h2>函数式端点适配</h2>
 * <p>项目 WebFlux 纯函数式端点（RouterFunction）的 Handler 方法参数形态为 {@link ServerRequest}，
 * 而非 {@link ServerWebExchange}。本切面通过 {@link AspectRequestUtil#resolveHttpHeaders(Object[])}
 * 兼容两种形态，避免函数式端点上标注 {@code @AntiReplay} 被静默跳过。</p>
 *
 * <h2>防重放键命名规范</h2>
 * <pre>
 *   replay:{nonce}
 * </pre>
 *
 * <h2>响应式混合处理</h2>
 * <p>
 * 当需要 Nonce 校验（走 Redis 响应式路径）时，返回类型为 {@link Mono}；
 * 当仅需时间戳校验时，返回 {@link ProceedingJoinPoint#proceed()} 的原始返回值。
 * 根据 {@code joinPoint.proceed()} 的返回类型自动适配处理方式。
 * </p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code * @AntiReplay(maxTimeDiff = 300, requireNonce = true)
 * public Mono<ServerResponse> submitVote(ServerRequest request) { ... }
 * }</pre>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 * @see AntiReplay
 * @see ReactiveStringRedisTemplate
 */
@Slf4j
@Aspect
@Component
@Order(0)
@ConditionalOnProperty(
        prefix = "apex.security.anti-replay",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class AntiReplayAspect {

    private final AtomicLong rejected = new AtomicLong();

    private void recordRejection(String reason) {
        securityMetricsRecorder.recordAntiReplayRejected();
        long count = rejected.incrementAndGet();
        if (count > 0 && (count & (count - 1)) == 0) {
            log.warn("防重放拒绝: reason={}, total={}", reason, count);
        }
    }

    /** Redis 防重放键前缀 */
    private static final String NONCE_KEY_PREFIX = "replay:";

    /** Redis 防重放键值 */
    private static final String NONCE_VALUE = "1";

    /** Nonce 在 Redis 中的过期时间（秒），从配置中读取，默认 360 秒（6 分钟） */
    @Value("${apex.security.anti-replay.nonce-ttl-seconds:360}")
    private long nonceTtlSeconds;

    /** 请求头名称：时间戳 */
    private static final String HEADER_TIMESTAMP = "X-Timestamp";

    /** 请求头名称：随机数 */
    private static final String HEADER_NONCE = "X-Nonce";

    /** 响应式 Redis 模板，用于 Nonce 的原子性写入 */
    private final ReactiveStringRedisTemplate redisTemplate;

    /** 安全指标记录器，记录防重放拦截事件到 Prometheus 指标。 */
    private final SecurityMetricsRecorder securityMetricsRecorder;

    /**
     * 构造防重放切面
     *
     * @param redisTemplate 响应式 Redis 字符串模板
     */
    public AntiReplayAspect(
            ReactiveStringRedisTemplate redisTemplate, SecurityMetricsRecorder securityMetricsRecorder) {
        this.redisTemplate = redisTemplate;
        this.securityMetricsRecorder = securityMetricsRecorder;
    }

    /**
     * 防重放环绕通知
     *
     * <p>拦截所有标注 {@link AntiReplay} 注解的方法，按以下流程校验：
     * <ul>
     *   <li>查找方法参数中的 {@link ServerWebExchange}，提取请求头</li>
     *   <li>校验时间戳偏差是否在 {@link AntiReplay#maxTimeDiff()} 秒内</li>
     *   <li>若需 Nonce 校验，通过 Redis SET NX EX 原子操作检查 Nonce 唯一性，
     *       并将目标方法执行包装在 {@link Mono#fromCallable} 中</li>
     *   <li>仅时间戳校验时，直接返回 {@code joinPoint.proceed()}</li>
     * </ul>
     *
     * @param joinPoint  连接点，提供目标方法的执行上下文
     * @param antiReplay 防重放注解实例
     * @return 表示目标方法执行结果的 Mono，或同步执行结果
     * @throws Throwable 防重放校验失败抛出 {@link BusinessException}，或目标方法执行异常
     */
    @Around("@annotation(antiReplay)")
    public Object around(ProceedingJoinPoint joinPoint, AntiReplay antiReplay) throws Throwable {
        HttpHeaders headers = AspectRequestUtil.resolveHttpHeaders(joinPoint.getArgs());
        if (headers == null) {
            log.warn("未找到 ServerWebExchange/ServerRequest 参数，跳过防重放校验");
            return joinPoint.proceed();
        }

        String timestampStr = headers.getFirst(HEADER_TIMESTAMP);

        if (timestampStr == null) {
            recordRejection("missing-timestamp");
            throw new BusinessException(ResultCode.ANTI_REPLAY_FAILED);
        }

        long timestamp = Long.parseLong(timestampStr);
        long now = Instant.now().getEpochSecond();

        if (Math.abs(now - timestamp) > antiReplay.maxTimeDiff()) {
            recordRejection("expired-timestamp");
            throw new BusinessException(ResultCode.ANTI_REPLAY_FAILED);
        }

        if (antiReplay.requireNonce()) {
            String nonce = headers.getFirst(HEADER_NONCE);
            if (nonce == null || nonce.isBlank()) {
                recordRejection("missing-nonce");
                throw new BusinessException(ResultCode.ANTI_REPLAY_FAILED);
            }

            String nonceKey = NONCE_KEY_PREFIX + nonce;
            return redisTemplate
                    .opsForValue()
                    .setIfAbsent(nonceKey, NONCE_VALUE, Duration.ofSeconds(nonceTtlSeconds))
                    .flatMap(acquired -> {
                        if (Boolean.FALSE.equals(acquired)) {
                            recordRejection("reused-nonce");
                            return Mono.error(new BusinessException(ResultCode.ANTI_REPLAY_FAILED));
                        }
                        log.debug("防重放通过");
                        try {
                            Object result = joinPoint.proceed();
                            if (result instanceof Mono<?> mono) {
                                return mono.cast(Object.class);
                            }
                            if (result instanceof Flux<?> flux) {
                                return Flux.from(flux).cast(Object.class).collectList();
                            }
                            if (result instanceof Publisher<?> publisher) {
                                return Flux.from(publisher).cast(Object.class).collectList();
                            }
                            // 同步返回值：包装进 Mono 完成响应式链（避免在 Redis I/O 线程执行阻塞代码）
                            return Mono.just(result);
                        } catch (Throwable error) {
                            return Mono.error(
                                    error instanceof RuntimeException runtimeException
                                            ? runtimeException
                                            : new RuntimeException(error));
                        }
                    });
        }

        return joinPoint.proceed();
    }
}
