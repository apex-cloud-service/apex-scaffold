package com.apex.scaffold.infrastructure.persistence.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/**
 * 慢 SQL 指标采集拦截器（MyBatis 插件）。
 *
 * <p>拦截 {@link Executor} 的 {@code query}/{@code update}，统计每次 SQL 执行耗时并上报
 * Micrometer 指标，支撑 Grafana 大盘「数据库（慢SQL）」分组：</p>
 * <ul>
 *   <li>{@code apex.sql.execution}：SQL 执行耗时 Timer，按 {@code type}（SELECT/INSERT/UPDATE/DELETE）标签区分</li>
 *   <li>{@code apex.sql.slow.count}：慢 SQL 次数 Counter，按 {@code type} 标签区分</li>
 *   <li>{@code apex.sql.slow.max.ms}：历史最慢 SQL 耗时 Gauge（毫秒）</li>
 * </ul>
 *
 * <p>超过 {@link SlowSqlProperties#getThresholdMs()} 阈值时，除指标外还会以 WARN 级别
 * 输出截断后的 SQL 文本与耗时（慢 SQL 日志）。计时开销为纳秒级，不影响业务性能。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.3.0
 * @see SlowSqlProperties
 */
@Slf4j
@Intercepts({
    @Signature(
            type = Executor.class,
            method = "query",
            args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
    @Signature(
            type = Executor.class,
            method = "query",
            args = {
                MappedStatement.class,
                Object.class,
                RowBounds.class,
                ResultHandler.class,
                CacheKey.class,
                BoundSql.class
            }),
    @Signature(
            type = Executor.class,
            method = "update",
            args = {MappedStatement.class, Object.class})
})
public class SlowSqlMetricsInterceptor implements Interceptor {

    /** SQL 执行耗时指标名。 */
    static final String METRIC_EXECUTION = "apex.sql.execution";

    /** 慢 SQL 次数指标名。 */
    static final String METRIC_SLOW_COUNT = "apex.sql.slow.count";

    /** 历史最慢耗时指标名。 */
    static final String METRIC_SLOW_MAX_MS = "apex.sql.slow.max.ms";

    /** 慢 SQL 日志中 SQL 文本最大长度。 */
    private static final int SQL_LOG_MAX_LENGTH = 600;

    /** 指标注册表。 */
    private final MeterRegistry meterRegistry;

    /** 慢 SQL 配置。 */
    private final SlowSqlProperties properties;

    /** 按 SQL 类型缓存的执行耗时 Timer。 */
    private final Map<String, Timer> executionTimers = new ConcurrentHashMap<>();

    /** 按 SQL 类型缓存的慢 SQL 次数 Counter。 */
    private final Map<String, Counter> slowCounters = new ConcurrentHashMap<>();

    /** 历史最慢耗时（毫秒），线程安全。 */
    private final AtomicLong maxSlowMs = new AtomicLong();

    /**
     * 构造拦截器。
     *
     * @param properties    慢 SQL 配置
     * @param meterRegistry Micrometer 指标注册表
     */
    public SlowSqlMetricsInterceptor(SlowSqlProperties properties, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        Gauge.builder(METRIC_SLOW_MAX_MS, maxSlowMs, AtomicLong::get)
                .description("历史最慢 SQL 执行耗时（毫秒）")
                .register(meterRegistry);
    }

    /**
     * {@inheritDoc}
     *
     * <p>在 SQL 执行前后计时；超阈值时累加慢 SQL 计数并输出 WARN 日志。
     * 计时在 finally 中完成，异常正常上抛，不影响原有执行语义。</p>
     */
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (!properties.isEnabled()) {
            return invocation.proceed();
        }
        long start = System.nanoTime();
        try {
            return invocation.proceed();
        } finally {
            long costMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            String type = resolveSqlType(invocation);
            executionTimer(type).record(costMs, TimeUnit.MILLISECONDS);
            if (costMs >= properties.getThresholdMs()) {
                slowCounter(type).increment();
                maxSlowMs.accumulateAndGet(costMs, Math::max);
                log.warn(
                        "慢SQL: type={} cost={}ms threshold={}ms sql={}",
                        type,
                        costMs,
                        properties.getThresholdMs(),
                        extractSql(invocation));
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    /** {@inheritDoc} */
    @Override
    public void setProperties(java.util.Properties properties) {
        // 无插件级属性
    }

    /**
     * 从调用参数中解析 SQL 类型。
     *
     * @param invocation 调用
     * @return SELECT/INSERT/UPDATE/DELETE，无法识别时为 UNKNOWN
     */
    private String resolveSqlType(Invocation invocation) {
        Object[] args = invocation.getArgs();
        if (args != null && args.length > 0 && args[0] instanceof MappedStatement ms) {
            SqlCommandType type = ms.getSqlCommandType();
            return type == null ? "UNKNOWN" : type.name();
        }
        return "UNKNOWN";
    }

    /**
     * 从调用参数中提取可读 SQL 文本（压缩空白并截断）。
     *
     * @param invocation 调用
     * @return 截断后的 SQL 文本；无法提取时返回 {@code "<unknown>"}
     */
    private String extractSql(Invocation invocation) {
        Object[] args = invocation.getArgs();
        try {
            if (args != null && args.length > 0 && args[0] instanceof MappedStatement ms) {
                Object parameter = args.length > 1 ? args[1] : null;
                BoundSql boundSql = ms.getBoundSql(parameter);
                String sql = boundSql.getSql().replaceAll("\\s+", " ").trim();
                return sql.length() <= SQL_LOG_MAX_LENGTH
                        ? sql
                        : sql.substring(0, SQL_LOG_MAX_LENGTH) + "...[truncated]";
            }
        } catch (Exception e) {
            log.trace("提取慢SQL文本失败: {}", e.getMessage());
        }
        return "<unknown>";
    }

    /**
     * 获取（并按需创建）指定类型的执行耗时 Timer。
     *
     * @param type SQL 类型
     * @return Timer 实例
     */
    private Timer executionTimer(String type) {
        return executionTimers.computeIfAbsent(
                type, t -> Timer.builder(METRIC_EXECUTION).tag("type", t).register(meterRegistry));
    }

    /**
     * 获取（并按需创建）指定类型的慢 SQL Counter。
     *
     * @param type SQL 类型
     * @return Counter 实例
     */
    private Counter slowCounter(String type) {
        return slowCounters.computeIfAbsent(
                type, t -> Counter.builder(METRIC_SLOW_COUNT).tag("type", t).register(meterRegistry));
    }
}
