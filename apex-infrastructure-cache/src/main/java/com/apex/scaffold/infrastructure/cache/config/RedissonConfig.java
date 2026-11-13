package com.apex.scaffold.infrastructure.cache.config;

import com.apex.scaffold.common.config.RedissonProperties;
import io.netty.channel.nio.NioEventLoopGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 客户端配置类
 *
 * <p>显式配置 Redisson 分布式锁的 Watchdog（看门狗）机制和连接池参数，
 * 提升 Redis 操作的可控性和可观测性。该配置类仅在生产环境（prod）或
 * 显式启用 Redisson 时生效，开发环境默认使用 Spring Data Redis（Lettuce）。
 *
 * <h2>设计说明</h2>
 * <p>Redisson 的看门狗（Watchdog）机制是一种自动续期机制，用于防止分布式锁在业务执行过程中提前释放。
 * <ul>
 *   <li><strong>Lock Watchdog Timeout</strong>：默认 30 秒，看门狗续期间隔为该值的 1/3（即 10 秒）</li>
 *   <li><strong>自动续期</strong>：持有锁的线程会每隔 10 秒自动续期，直到业务执行完毕或进程崩溃</li>
 *   <li><strong>触发条件</strong>：仅当 {@code leaseTime = -1} 时启用看门狗自动续期</li>
 *   <li><strong>适用场景</strong>：执行时间不确定的分布式任务，防止锁提前释放导致并发问题</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <table border="1">
 *   <tr>
 *     <th>配置项</th>
 *     <th>默认值</th>
 *     <th>说明</th>
 *   </tr>
 *   <tr>
 *     <td>{@code apex.redis.lock-watchdog-timeout}</td>
 *     <td>30000（30 秒）</td>
 *     <td>看门狗超时时间（毫秒）</td>
 *   </tr>
 *   <tr>
 *     <td>{@code apex.redis.command-timeout}</td>
 *     <td>3000（3 秒）</td>
 *     <td>Redis 命令超时时间（毫秒）</td>
 *   </tr>
 *   <tr>
 *     <td>{@code apex.redis.retry-attempts}</td>
 *     <td>3</td>
 *     <td>命令重试次数</td>
 *   </tr>
 *   <tr>
 *     <td>{@code apex.redis.retry-interval}</td>
 *     <td>1500（1.5 秒）</td>
 *     <td>重试间隔（毫秒）</td>
 *   </tr>
 * </table>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // application.yml 配置
 * spring:
 *   data:
 *     redis:
 *       host: ${REDIS_HOST:127.0.0.1}
 *       port: ${REDIS_PORT:6379}
 *       password: ${REDIS_PASSWORD:}
 *
 * apex:
 *   redis:
 *     lock-watchdog-timeout: 30000
 *     command-timeout: 3000
 *     retry-attempts: 3
 *     retry-interval: 1500
 *
 * // 使用 Redisson 分布式锁
 * @CacheLock(prefix = "user", key = "#userId", waitTime = 3000, leaseTime = -1)
 * public UserEntity getUserById(Long userId) {
 *     // 业务逻辑，看门狗会自动续期
 * }
 * }</pre>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>看门狗机制仅适用于 {@code leaseTime = -1} 的场景，显式设置 {@code leaseTime} 则不会启用</li>
 *   <li>看门狗续期操作本身是异步的，不会阻塞业务线程</li>
 *   <li>如果进程崩溃，锁会在 {@code lockWatchdogTimeout} 后自动释放</li>
 *   <li>开发环境（dev）默认排除 Redisson，使用 Spring Data Redis（Lettuce）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 * @see RedissonProperties
 * @see org.redisson.config.Config
 * @see org.redisson.api.RedissonClient
 * @see com.apex.scaffold.infrastructure.cache.annotation.CacheLock
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(RedissonProperties.class)
@ConditionalOnProperty(name = "spring.profiles.active", havingValue = "dev", matchIfMissing = false)
@RequiredArgsConstructor
public class RedissonConfig {

    private final RedissonProperties redissonProperties;

    @Value("${spring.data.redis.host:127.0.0.1}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.database:0}")
    private int redisDatabase;

    /**
     * 创建 Redisson 客户端 Bean
     *
     * <p>配置 Redisson 单机模式连接参数，包括看门狗超时、命令超时、重试策略等。
     * 该方法仅在非 dev 环境下生效（通过 {@code @ConditionalOnProperty} 控制）。
     *
     * @return 配置好的 RedissonClient 实例
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();

        // 配置单机模式
        config.useSingleServer()
                .setAddress("redis://" + redisHost + ":" + redisPort)
                .setPassword(redisPassword.isEmpty() ? null : redisPassword)
                .setDatabase(redisDatabase)
                .setConnectionMinimumIdleSize(5)
                .setConnectionPoolSize(20)
                .setConnectTimeout(5000)
                .setTimeout(redissonProperties.getCommandTimeout())
                .setRetryAttempts(redissonProperties.getRetryAttempts())
                .setRetryInterval(redissonProperties.getRetryInterval());

        // 配置看门狗超时时间
        config.setLockWatchdogTimeout(redissonProperties.getLockWatchdogTimeout());

        // 配置 Netty 线程池（复用全局 NioEventLoopGroup，避免创建过多线程）
        config.setEventLoopGroup(new NioEventLoopGroup());

        // 配置线程数（默认为 CPU 核心数 * 2）
        config.setThreads(Runtime.getRuntime().availableProcessors() * 2);

        // 配置 Netty 线程数（默认为 CPU 核心数 * 2）
        config.setNettyThreads(Runtime.getRuntime().availableProcessors() * 2);

        log.info(
                "Redisson 客户端配置完成: host={}, port={}, database={}, lockWatchdogTimeout={}ms, commandTimeout={}ms, retryAttempts={}, retryInterval={}ms",
                redisHost,
                redisPort,
                redisDatabase,
                redissonProperties.getLockWatchdogTimeout(),
                redissonProperties.getCommandTimeout(),
                redissonProperties.getRetryAttempts(),
                redissonProperties.getRetryInterval());

        return Redisson.create(config);
    }
}
