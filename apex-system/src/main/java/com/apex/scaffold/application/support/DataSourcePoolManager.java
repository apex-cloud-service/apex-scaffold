package com.apex.scaffold.application.support;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 动态数据源连接池管理器
 *
 * <p>使用 HikariCP 管理代码生成器的动态数据源连接池，支持连接池复用、连接测试和连接池释放。
 * 采用单例模式，保证每个数据源配置只创建一个连接池实例。
 *
 * <p>连接池配置参数优化为代码生成场景：
 * <ul>
 *   <li>最小空闲连接数：1（代码生成为低频操作）</li>
 *   <li>最大连接数：5（避免占用过多资源）</li>
 *   <li>连接超时：5秒（快速失败）</li>
 *   <li>空闲超时：10分钟（及时回收）</li>
 *   <li>最大生命周期：30分钟（定期刷新）</li>
 * </ul>
 *
 * <p>安全约束：
 * <ul>
 *   <li>仅允许已支持的数据库类型对应的 JDBC 协议前缀，拒绝任意驱动与本地文件类 URL</li>
 *   <li>所有连接池创建与连接测试均调度到 {@link BlockingIoSchedulers#jdbc()}，不占用事件循环</li>
 *   <li>并发创建/测试由信号量限流，避免连接风暴打满目标库</li>
 *   <li>密码只写入连接池，不记录日志、不回传</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Component
public class DataSourcePoolManager {

    private static final String HYPHEN_SEPARATOR = "-";

    private static final Map<String, HikariDataSource> DATA_SOURCE_POOL = new ConcurrentHashMap<>();

    private static final int MIN_IDLE = 1;
    private static final int MAX_POOL_SIZE = 5;
    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration MAX_LIFETIME = Duration.ofMinutes(30);

    /** 允许的 JDBC 协议前缀，顺序与支持能力一致。 */
    private static final List<String> ALLOWED_JDBC_PREFIXES = List.of("jdbc:mysql:", "jdbc:postgresql:");

    private static final int DEFAULT_CONCURRENCY = 4;
    private static final int MIN_TIMEOUT_SECONDS = 1;
    private static final int MAX_TIMEOUT_SECONDS = 30;
    private static final long GATE_ACQUIRE_SECONDS = 10L;

    private final Semaphore connectionGate;

    /**
     * 构造动态数据源连接池管理器。
     *
     * @param maxConcurrency 并发创建/测试连接池的信号量上限，小于 1 时按 1 处理
     */
    public DataSourcePoolManager(
            @Value("${apex.generator.datasource.max-concurrency:" + DEFAULT_CONCURRENCY + "}") int maxConcurrency) {
        this.connectionGate = new Semaphore(Math.max(1, maxConcurrency));
    }

    /**
     * 获取或创建数据源连接池
     *
     * @param tenantId 租户 ID
     * @param datasourceId 数据源 ID
     * @param jdbcUrl JDBC URL
     * @param username 用户名
     * @param password 密码
     * @return 数据源实例
     */
    public Mono<DataSource> getOrCreateDataSource(
            String tenantId, Long datasourceId, String jdbcUrl, String username, String password) {
        return Mono.defer(() -> {
                    validateJdbcUrl(jdbcUrl);
                    String key = buildKey(tenantId, datasourceId, jdbcUrl, username);
                    return Mono.fromCallable(() -> {
                                HikariDataSource dataSource = DATA_SOURCE_POOL.computeIfAbsent(key, k -> {
                                    HikariConfig config = new HikariConfig();
                                    config.setJdbcUrl(jdbcUrl);
                                    config.setUsername(username);
                                    config.setPassword(password);

                                    config.setMinimumIdle(MIN_IDLE);
                                    config.setMaximumPoolSize(MAX_POOL_SIZE);
                                    config.setConnectionTimeout(CONNECTION_TIMEOUT.toMillis());
                                    config.setIdleTimeout(IDLE_TIMEOUT.toMillis());
                                    config.setMaxLifetime(MAX_LIFETIME.toMillis());

                                    config.setPoolName("GenCodePool-" + datasourceId);
                                    config.setAutoCommit(true);
                                    config.setConnectionTestQuery("SELECT 1");

                                    log.info("创建代码生成数据源连接池: datasourceId={}", datasourceId);
                                    return new HikariDataSource(config);
                                });
                                return (DataSource) dataSource;
                            })
                            .subscribeOn(BlockingIoSchedulers.jdbc())
                            .onErrorMap(error -> error instanceof BusinessException
                                    ? error
                                    : new BusinessException(ResultCode.DB_ERROR));
                })
                .transformDeferred(this::limitConcurrency);
    }

    /**
     * 测试数据源连接
     *
     * @param jdbcUrl JDBC URL
     * @param username 用户名
     * @param password 密码
     * @param timeoutSeconds 超时时间（秒）
     * @return 连接是否成功
     */
    public Mono<Boolean> testConnection(String jdbcUrl, String username, String password, int timeoutSeconds) {
        return Mono.defer(() -> {
                    validateJdbcUrl(jdbcUrl);
                    int effectiveTimeout = Math.min(Math.max(timeoutSeconds, MIN_TIMEOUT_SECONDS), MAX_TIMEOUT_SECONDS);
                    return Mono.fromCallable(() -> {
                                HikariConfig config = new HikariConfig();
                                config.setJdbcUrl(jdbcUrl);
                                config.setUsername(username);
                                config.setPassword(password);
                                config.setConnectionTimeout(
                                        Duration.ofSeconds(effectiveTimeout).toMillis());
                                config.setMaximumPoolSize(1);

                                try (HikariDataSource testDs = new HikariDataSource(config);
                                        Connection conn = testDs.getConnection()) {
                                    return conn.isValid(effectiveTimeout);
                                }
                            })
                            .subscribeOn(BlockingIoSchedulers.jdbc())
                            .timeout(Duration.ofSeconds(effectiveTimeout + 1L))
                            .onErrorMap(e -> {
                                log.error(
                                        "数据库连接测试失败: errorType={}", e.getClass().getSimpleName());
                                return new BusinessException(ResultCode.DB_ERROR);
                            });
                })
                .transformDeferred(this::limitConcurrency);
    }

    /**
     * 以信号量限制同时创建/测试连接的并发数，避免连接风暴打满目标库或耗尽本地线程
     *
     * @param source 上游信号
     * @param <T> 元素类型
     * @return 限流后的信号
     */
    private <T> Mono<T> limitConcurrency(Mono<T> source) {
        return source.defer(
                () -> Mono.fromCallable(() -> connectionGate.tryAcquire(GATE_ACQUIRE_SECONDS, TimeUnit.SECONDS))
                        .subscribeOn(BlockingIoSchedulers.jdbc())
                        .flatMap(acquired -> acquired
                                ? source.doFinally(signal -> connectionGate.release())
                                : Mono.error(new BusinessException(ResultCode.SYSTEM_BUSY, "代码生成数据源连接繁忙，请稍后重试"))));
    }

    /**
     * 关闭并移除指定数据源连接池
     *
     * @param tenantId 租户 ID
     * @param datasourceId 数据源 ID
     */
    public void closeDataSource(String tenantId, Long datasourceId) {
        DATA_SOURCE_POOL.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith(tenantId + HYPHEN_SEPARATOR + datasourceId + HYPHEN_SEPARATOR)) {
                HikariDataSource ds = entry.getValue();
                if (!ds.isClosed()) {
                    ds.close();
                    log.info("关闭代码生成数据源连接池: datasourceId={}", datasourceId);
                }
                return true;
            }
            return false;
        });
    }

    /**
     * 关闭所有数据源连接池
     */
    public void closeAll() {
        DATA_SOURCE_POOL.values().forEach(ds -> {
            if (!ds.isClosed()) {
                ds.close();
            }
        });
        DATA_SOURCE_POOL.clear();
        log.info("已关闭所有代码生成数据源连接池");
    }

    /**
     * 校验 JDBC URL 协议，仅允许已支持的数据库类型
     *
     * @param jdbcUrl JDBC URL
     */
    public static void validateJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "JDBC URL 不能为空");
        }
        String normalized = jdbcUrl.trim().toLowerCase(Locale.ROOT);
        boolean allowed = ALLOWED_JDBC_PREFIXES.stream().anyMatch(normalized::startsWith);
        if (!allowed) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的数据库连接协议");
        }
    }

    /**
     * 构建数据源唯一键
     *
     * @param tenantId 租户 ID
     * @param datasourceId 数据源 ID
     * @param jdbcUrl JDBC URL
     * @param username 用户名
     * @return 唯一键
     */
    private String buildKey(String tenantId, Long datasourceId, String jdbcUrl, String username) {
        return tenantId + HYPHEN_SEPARATOR + datasourceId + HYPHEN_SEPARATOR + jdbcUrl + HYPHEN_SEPARATOR + username;
    }
}
