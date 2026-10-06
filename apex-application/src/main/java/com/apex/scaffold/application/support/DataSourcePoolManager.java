package com.apex.scaffold.application.support;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
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

    /**
     * 获取或创建数据源连接池
     *
     * @param datasourceId 数据源 ID
     * @param jdbcUrl JDBC URL
     * @param username 用户名
     * @param password 密码
     * @return 数据源实例
     */
    public Mono<DataSource> getOrCreateDataSource(
            String tenantId, Long datasourceId, String jdbcUrl, String username, String password) {
        return Mono.fromCallable(() -> {
            String key = buildKey(tenantId, datasourceId, jdbcUrl, username);

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
        });
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
        return Mono.fromCallable(() -> {
                    HikariConfig config = new HikariConfig();
                    config.setJdbcUrl(jdbcUrl);
                    config.setUsername(username);
                    config.setPassword(password);
                    config.setConnectionTimeout(
                            Duration.ofSeconds(timeoutSeconds).toMillis());
                    config.setMaximumPoolSize(1);

                    try (HikariDataSource testDs = new HikariDataSource(config);
                            Connection conn = testDs.getConnection()) {
                        return conn.isValid(timeoutSeconds);
                    }
                })
                .timeout(Duration.ofSeconds(timeoutSeconds + 1))
                .onErrorMap(e -> {
                    log.error("数据库连接测试失败: errorType={}", e.getClass().getSimpleName());
                    return new BusinessException(ResultCode.DB_ERROR);
                });
    }

    /**
     * 关闭并移除指定数据源连接池
     *
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
     * 构建数据源唯一键
     *
     * @param datasourceId 数据源 ID
     * @param jdbcUrl JDBC URL
     * @param username 用户名
     * @return 唯一键
     */
    private String buildKey(String tenantId, Long datasourceId, String jdbcUrl, String username) {
        return tenantId + HYPHEN_SEPARATOR + datasourceId + HYPHEN_SEPARATOR + jdbcUrl + HYPHEN_SEPARATOR + username;
    }
}
