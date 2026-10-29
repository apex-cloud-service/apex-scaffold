package com.apex.scaffold.infrastructure.metrics;

import com.alibaba.druid.pool.DruidDataSource;
import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.SQLException;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Druid 连接池 Micrometer 指标绑定器
 *
 * <p>项目使用 dynamic-datasource 多数据源 + Druid 连接池（druid-spring-boot-3-starter），
 * 该 starter 不提供 Micrometer 指标自动注册，因此在此手动将各 Druid 数据源的实时连接状态绑定为
 * Gauge，将单调累计统计绑定为 FunctionCounter，经 {@link MeterRegistry} 暴露给 Prometheus 抓取。
 *
 * <p>指标命名沿用 Druid 生态习惯，均携带 {@code name} 标签标识数据源（如 master）：
 * <ul>
 *   <li><strong>druid.connections.*</strong>：连接池容量与实时状态（active/pooling/max/min/等待等）</li>
 *   <li><strong>druid.statistics.*</strong>：连接池生命周期累计统计（建连、回收、错误、提交、回滚等）</li>
 * </ul>
 *
 * <p>绑定时机为 {@link ApplicationReadyEvent}：此时动态数据源已全部创建完成，遍历
 * {@link DynamicRoutingDataSource#getDataSources()} 即可覆盖配置的全部数据源。
 * 运行时通过 {@code DynamicRoutingDataSource.addDataSource} 动态新增的数据源不会被绑定。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see DruidDataSource
 * @see MeterRegistry
 */
@Slf4j
@Component
@lombok.RequiredArgsConstructor
public class DruidDataSourceMetricsBinder {

    /** 连接状态指标前缀 */
    private static final String CONNECTIONS_PREFIX = "druid.connections";

    /** 生命周期统计指标前缀 */
    private static final String STATISTICS_PREFIX = "druid.statistics";

    /** 数据源名称标签 */
    private static final String DATASOURCE_TAG = "name";

    /** 指标注册表 */
    private final MeterRegistry meterRegistry;

    /** 动态路由数据源 */
    private final DataSource dataSource;

    /**
     * 应用启动完成后绑定全部已注册的 Druid 连接池指标
     */
    @EventListener(ApplicationReadyEvent.class)
    public void bindOnReady() {
        if (!(dataSource instanceof DynamicRoutingDataSource routingDataSource)) {
            log.warn(
                    "当前 DataSource 不是 DynamicRoutingDataSource，跳过 Druid 指标绑定，实际类型: {}",
                    dataSource.getClass().getName());
            return;
        }
        int bound = 0;
        for (Map.Entry<String, DataSource> entry :
                routingDataSource.getDataSources().entrySet()) {
            if (bindDataSource(entry.getKey(), entry.getValue())) {
                bound++;
            }
        }
        log.info("Druid 连接池指标绑定完成，共绑定 {} 个数据源", bound);
    }

    /**
     * 绑定单个数据源；仅当底层连接池是 Druid 时注册指标
     *
     * @param name 数据源名称（如 master）
     * @param candidate 候选数据源（可能为 P6Spy 等包装）
     * @return 是否成功绑定
     */
    public boolean bindDataSource(String name, DataSource candidate) {
        DruidDataSource druid = resolveDruid(candidate);
        if (druid == null) {
            return false;
        }
        bindGauges(druid, name);
        return true;
    }

    /**
     * 从候选数据源中解析出底层 Druid 连接池
     *
     * @param candidate 候选数据源
     * @return Druid 连接池；非 Druid 或解析失败返回 null
     */
    private DruidDataSource resolveDruid(DataSource candidate) {
        if (candidate instanceof DruidDataSource druid) {
            return druid;
        }
        try {
            return candidate.unwrap(DruidDataSource.class);
        } catch (SQLException e) {
            return null;
        }
    }

    /**
     * 注册指定 Druid 连接池的连接状态与生命周期统计指标
     *
     * @param druid Druid 连接池
     * @param name 数据源名称
     */
    private void bindGauges(DruidDataSource druid, String name) {
        gauge(CONNECTIONS_PREFIX + ".active", name, druid, d -> d.getActiveCount());
        gauge(CONNECTIONS_PREFIX + ".pooling", name, druid, d -> d.getPoolingCount());
        gauge(CONNECTIONS_PREFIX + ".usage", name, druid, d -> d.getActiveCount() + d.getPoolingCount());
        gauge(CONNECTIONS_PREFIX + ".initial", name, druid, d -> d.getInitialSize());
        gauge(CONNECTIONS_PREFIX + ".min", name, druid, d -> d.getMinIdle());
        gauge(CONNECTIONS_PREFIX + ".max", name, druid, d -> d.getMaxActive());
        gauge(CONNECTIONS_PREFIX + ".peak", name, druid, d -> d.getActivePeak());
        gauge(CONNECTIONS_PREFIX + ".wait.thread", name, druid, d -> d.getWaitThreadCount());
        counter(CONNECTIONS_PREFIX + ".wait.count", name, druid, d -> d.getNotEmptyWaitCount());
        counter(CONNECTIONS_PREFIX + ".wait.millis", name, druid, d -> d.getNotEmptyWaitMillis());
        counter(STATISTICS_PREFIX + ".connect", name, druid, d -> d.getConnectCount());
        counter(STATISTICS_PREFIX + ".create", name, druid, d -> d.getCreateCount());
        counter(STATISTICS_PREFIX + ".destroy", name, druid, d -> d.getDestroyCount());
        counter(STATISTICS_PREFIX + ".close", name, druid, d -> d.getCloseCount());
        counter(STATISTICS_PREFIX + ".recycle", name, druid, d -> d.getRecycleCount());
        counter(STATISTICS_PREFIX + ".error", name, druid, d -> d.getErrorCount());
        counter(STATISTICS_PREFIX + ".discard", name, druid, d -> d.getDiscardCount());
        counter(STATISTICS_PREFIX + ".commit", name, druid, d -> d.getCommitCount());
        counter(STATISTICS_PREFIX + ".rollback", name, druid, d -> d.getRollbackCount());
        counter(STATISTICS_PREFIX + ".start.transaction", name, druid, d -> d.getStartTransactionCount());
        log.debug("Druid 指标已绑定, 数据源: {}", name);
    }

    /**
     * 注册单个 Gauge 指标
     *
     * @param name 指标名称
     * @param dsName 数据源名称
     * @param druid Druid 连接池
     * @param valueFunction 取值函数
     */
    private void gauge(
            String name, String dsName, DruidDataSource druid, ToDoubleFunction<DruidDataSource> valueFunction) {
        Gauge.builder(name, druid, valueFunction).tag(DATASOURCE_TAG, dsName).register(meterRegistry);
    }

    /**
     * 注册 Druid 的单调累计统计。
     *
     * <p>该类指标使用 {@link FunctionCounter}，导出后带有 Prometheus 的 {@code _total}
     * 后缀，面板应使用 {@code rate()} 或 {@code increase()} 计算窗口增量；连接数、等待线程数
     * 等瞬时状态仍由 {@link Gauge} 注册，不应混用 Counter 语义。</p>
     *
     * @param name 指标名称
     * @param dsName 数据源名称标签
     * @param druid Druid 连接池
     * @param valueFunction 读取单调累计值的函数
     */
    private void counter(
            String name, String dsName, DruidDataSource druid, ToDoubleFunction<DruidDataSource> valueFunction) {
        FunctionCounter.builder(name, druid, valueFunction)
                .tag(DATASOURCE_TAG, dsName)
                .register(meterRegistry);
    }
}
