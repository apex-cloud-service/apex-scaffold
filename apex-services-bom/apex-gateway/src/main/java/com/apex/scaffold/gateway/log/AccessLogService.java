package com.apex.scaffold.gateway.log;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.common.util.JsonUtil;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Mono;

/**
 * 网关访问日志落库服务.
 *
 * <p>对应迁移方案 §5.3「访问日志」落库编排，是访问日志持久化的<b>唯一入口</b>，
 * 承载三条核心不变量：
 * <ol>
 *   <li><b>脱敏红线</b>：{@link #sanitizeHeaders(HttpHeaders)} 仅记录白名单请求头，
 *       对 {@code Authorization}/{@code Cookie} 等敏感头一律以 {@code ***} 占位，
 *       杜绝真实凭据入库（安全红线）；</li>
 *   <li><b>异步吞异常</b>：落库通过 {@link BlockingIoSchedulers#jdbc()} 移出
 *       Netty 事件循环，且仓储异常一律被 {@code onErrorResume} 吞掉并计数，
 *       <b>绝不允许</b>访问日志写库失败影响正常响应；</li>
 *   <li><b>开关与采样</b>：总开关关闭或未命中采样时，直接返回空信号，
 *       不产生任何仓储调用，用于高 QPS 场景降载。</li>
 * </ol>
 *
 * <p><b>响应式说明</b>：{@link #save(AccessLogRecord)} 返回 {@link reactor.core.publisher.Mono}
 * 且不阻塞调用线程；调用方（访问日志过滤器）通常在 {@code doFinally} 中
 * {@code subscribe()} 触发，不参与响应链路。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Slf4j
@lombok.RequiredArgsConstructor
public class AccessLogService {

    /** 需要记录的请求头（按固定顺序输出，保证 JSON 稳定可比对） */
    private static final List<String> RECORDED_HEADERS = List.of(
            HttpHeaders.CONTENT_TYPE,
            HttpHeaders.ACCEPT,
            HttpHeaders.USER_AGENT,
            "X-Client-Type",
            "X-Gray-Tag",
            HttpHeaders.AUTHORIZATION,
            HttpHeaders.COOKIE,
            HttpHeaders.SET_COOKIE,
            "X-Api-Key",
            "X-Auth-Token");

    /** 敏感请求头（即使被记录也一律以 {@code ***} 占位） */
    private static final Set<String> MASKED_HEADERS =
            Set.of(HttpHeaders.AUTHORIZATION, HttpHeaders.COOKIE, HttpHeaders.SET_COOKIE, "X-Api-Key", "X-Auth-Token");

    /** 脱敏占位符 */
    private static final String MASK_VALUE = "***";

    /** 访问日志落库失败计数指标名 */
    public static final String METRIC_PERSIST_FAILED = "apex.gateway.accesslog.persist.failed";

    /** 采样率下界（闭区间）：小于等于该值表示不采样 */
    private static final double SAMPLE_RATE_MIN = 0.0d;

    /** 采样率上界（闭区间）：大于等于该值表示全量采样 */
    private static final double SAMPLE_RATE_MAX = 1.0d;

    /** 访问日志仓储 */
    private final AccessLogRepository repository;

    /** 访问日志落库配置 */
    private final AccessLogProperties properties;

    /** 落库失败计数指标注册表 */
    private final MeterRegistry meterRegistry;

    /** 落库失败累计次数（用于观测，不作为错误向上传播） */
    private final AtomicLong persistFailureCount = new AtomicLong();

    /**
     * 异步持久化一条访问日志.
     *
     * <p>落库前依次判定总开关与采样率，任一不满足即返回空信号；
     * 满足时在 JDBC 专用调度器上执行写入，仓储异常被吞掉并计数，
     * 保证返回信号始终为正常完成，不影响调用方。
     *
     * @param record 待落库的访问日志记录（请求头须已脱敏）
     */
    public Mono<Void> save(AccessLogRecord record) {
        if (record == null || !properties.isEnabled() || !sampled()) {
            return Mono.empty();
        }
        return repository.save(record).subscribeOn(BlockingIoSchedulers.jdbc()).onErrorResume(ex -> {
            long failures = persistFailureCount.incrementAndGet();
            Counter.builder(METRIC_PERSIST_FAILED)
                    .description("网关访问日志落库失败次数")
                    .register(meterRegistry)
                    .increment();
            log.warn("访问日志落库失败(已忽略): traceId={}, failures={}, reason={}", record.traceId(), failures, ex.getMessage());
            return Mono.empty();
        });
    }

    /**
     * 提取并脱敏请求头为 JSON 快照.
     *
     * <p>仅遍历白名单 {@link #RECORDED_HEADERS}，其中敏感头以 {@code ***} 占位，
     * 其余头值按 {@code max-header-length} 截断；未在白名单内的头一律不记录，
     * 避免敏感信息无意外溢。
     *
     * @param headers 请求头
     * @return 脱敏后的请求头 JSON（如 {@code {"Content-Type":"application/json","Authorization":"***"}}）
     */
    public String sanitizeHeaders(HttpHeaders headers) {
        Map<String, String> snapshot = new LinkedHashMap<>();
        if (headers != null) {
            for (String name : RECORDED_HEADERS) {
                String value = headers.getFirst(name);
                if (value == null) {
                    continue;
                }
                snapshot.put(name, MASKED_HEADERS.contains(name) ? MASK_VALUE : truncate(value));
            }
        }
        return JsonUtil.toJson(snapshot);
    }

    /**
     * 获取落库失败累计次数.
     *
     * @return 自服务创建以来的落库失败次数
     */
    public long persistFailureCount() {
        return persistFailureCount.get();
    }

    /**
     * 判断当前请求是否命中采样.
     *
     * @return {@code true} 表示需要落库
     */
    private boolean sampled() {
        double rate = properties.getSampleRate();
        if (rate <= SAMPLE_RATE_MIN) {
            return false;
        }
        if (rate >= SAMPLE_RATE_MAX) {
            return true;
        }
        return ThreadLocalRandom.current().nextDouble() < rate;
    }

    /**
     * 按配置的最大长度截断请求头值.
     *
     * @param value 原始请求头值
     * @return 截断后的值
     */
    private String truncate(String value) {
        int max = properties.getMaxHeaderLength();
        if (max > 0 && value.length() > max) {
            return value.substring(0, max);
        }
        return value;
    }
}
