package com.apex.scaffold.infrastructure.cache.wrapper;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 缓存逻辑过期包装器
 *
 * <p>为缓存数据提供逻辑过期时间包装,用于实现缓存穿透防护和异步刷新策略。
 * 包装器只保存数据和逻辑期限，不负责缓存删除、刷新线程或并发互斥；这些行为由调用方实现。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li><strong>高并发缓存刷新</strong>:避免缓存失效时大量请求直接打到数据库</li>
 *   <li><strong>缓存降级</strong>:过期数据仍可用于降级返回,避免服务不可用</li>
 *   <li><strong>异步更新</strong>:调用方可检测期限并提交后台刷新任务</li>
 *   <li><strong>热点数据保护</strong>:调用方可保留旧数据并自行协调刷新</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <table border="1">
 *   <caption>逻辑过期 vs 物理过期</caption>
 *   <tr>
 *     <th>特性</th>
 *     <th>逻辑过期</th>
 *     <th>物理过期(TTL)</th>
 *   </tr>
 *   <tr>
 *     <td>数据存储</td>
 *     <td>过期后仍存在</td>
 *     <td>过期后自动删除</td>
 *   </tr>
 *   <tr>
 *     <td>并发刷新</td>
 *     <td>由调用方控制刷新并发和线程</td>
 *     <td>可能多线程同时查库</td>
 *   </tr>
 *   <tr>
 *     <td>降级能力</td>
 *     <td>可返回过期数据</td>
 *     <td>无法返回已删除数据</td>
 *   </tr>
 *   <tr>
 *     <td>适用场景</td>
 *     <td>高并发热点数据</td>
 *     <td>一般缓存数据</td>
 *   </tr>
 * </table>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 示例1: 包装数据并设置60秒逻辑过期
 * User user = userService.getById(userId);
 * LogicalExpiryWrapper<User> wrapper = LogicalExpiryWrapper.of(user, 60);
 * cache.put("user:" + userId, wrapper);
 *
 * // 示例2: 检查数据是否逻辑过期
 * LogicalExpiryWrapper<User> cached = cache.get("user:" + userId);
 * if (cached.isExpired()) {
 *     // 返回过期数据(降级)
 *     User staleUser = cached.getData();
 *     // 异步刷新(避免阻塞)
 *     executor.execute(() -> refreshCache(userId));
 *     return staleUser;
 * }
 * return cached.getData();
 *
 * // 示例3: 手动构造包装器
 * Instant expireTime = Instant.now().plusSeconds(300);
 * LogicalExpiryWrapper<String> wrapper = new LogicalExpiryWrapper<>("data", expireTime);
 * }</pre>
 *
 * @param <T> 被包装的数据类型
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogicalExpiryWrapper<T> {

    private T data;

    private Instant logicalExpireTime;

    /**
     * 使用当前时钟判断是否严格晚于逻辑过期时刻，不触发删除、锁定或刷新。
     *
     * @return 未设置过期时刻时为 {@code false}，否则仅在当前时刻晚于该时刻时为 {@code true}
     */
    public boolean isExpired() {
        return logicalExpireTime != null && Instant.now().isAfter(logicalExpireTime);
    }

    /**
     * 以调用时的当前时刻加上指定秒数生成逻辑期限，不设置缓存后端的物理 TTL。
     *
     * @param <T> 数据类型
     * @param data 被包装数据，允许为 {@code null}
     * @param ttlSeconds 相对于当前时刻的秒数，零或负数不会被拒绝
     * @return 带有计算后逻辑期限的新包装对象
     * @throws java.time.DateTimeException 计算后的时刻超出 {@link Instant} 支持范围
     * @throws ArithmeticException 秒数相加发生数值溢出
     */
    public static <T> LogicalExpiryWrapper<T> of(T data, long ttlSeconds) {
        Instant expireTime = Instant.now().plusSeconds(ttlSeconds);
        return new LogicalExpiryWrapper<>(data, expireTime);
    }
}
