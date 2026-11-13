package com.apex.scaffold.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redisson 配置属性类
 *
 * <p>定义 Redisson 客户端的配置参数，支持通过 {@code apex.redis.*} 配置项覆盖默认值。
 * 该配置类主要控制 Redisson 的看门狗（Watchdog）机制、命令超时和重试策略。
 *
 * <h2>设计说明</h2>
 * <p>Redisson 的看门狗（Watchdog）机制用于自动延长分布式锁的有效期，防止业务执行时间过长导致锁提前释放。
 * 当 {@code leaseTime} 设置为 {@code -1} 时，看门狗会每隔 {@link #lockWatchdogTimeout} 的 1/3 时间
 * 自动续期锁，直到业务方法执行完毕或进程崩溃。
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
 *     <td>看门狗超时时间，续期间隔为该值的 1/3</td>
 *   </tr>
 *   <tr>
 *     <td>{@code apex.redis.command-timeout}</td>
 *     <td>3000（3 秒）</td>
 *     <td>Redis 命令执行超时时间</td>
 *   </tr>
 *   <tr>
 *     <td>{@code apex.redis.retry-attempts}</td>
 *     <td>3</td>
 *     <td>命令失败重试次数</td>
 *   </tr>
 *   <tr>
 *     <td>{@code apex.redis.retry-interval}</td>
 *     <td>1500（1.5 秒）</td>
 *     <td>重试间隔时间</td>
 *   </tr>
 * </table>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * # application.yml 配置示例
 * apex:
 *   redis:
 *     lock-watchdog-timeout: 30000  # 30 秒
 *     command-timeout: 3000          # 3 秒
 *     retry-attempts: 3              # 重试 3 次
 *     retry-interval: 1500           # 重试间隔 1.5 秒
 * }</pre>
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 * @see org.redisson.config.Config
 * @see com.apex.scaffold.infrastructure.cache.config.RedissonConfig
 */
@Data
@ConfigurationProperties(prefix = "apex.redis")
public class RedissonProperties {

    /**
     * 分布式锁看门狗超时时间（毫秒）
     *
     * <p>默认 30000ms（30 秒）。看门狗会每隔该值的 1/3（即 10 秒）自动续期锁。
     * 该机制仅在 {@code leaseTime = -1} 时生效。
     *
     * <p><strong>使用场景</strong>：适用于执行时间不确定的分布式任务，
     * 例如数据同步、文件处理等长时间运行的业务操作。
     */
    private long lockWatchdogTimeout = 30000L;

    /**
     * Redis 命令超时时间（毫秒）
     *
     * <p>默认 3000ms（3 秒）。超过该时间未响应则抛出 {@link org.redisson.client.RedisTimeoutException}。
     *
     * <p><strong>注意</strong>：该超时不包括命令在 Redis 队列中的等待时间，仅计算命令执行时间。
     */
    private int commandTimeout = 3000;

    /**
     * 命令失败重试次数
     *
     * <p>默认 3 次。仅对网络瞬时故障（如连接超时、读取超时）有效，
     * Redis 服务端返回的错误（如 WRONGTYPE、NOAUTH）不会触发重试。
     */
    private int retryAttempts = 3;

    /**
     * 重试间隔（毫秒）
     *
     * <p>默认 1500ms（1.5 秒）。每次重试前等待的时间。
     * Redisson 使用指数退避策略，实际等待时间为 {@code retryInterval * (2^retryCount - 1)}。
     */
    private int retryInterval = 1500;
}
