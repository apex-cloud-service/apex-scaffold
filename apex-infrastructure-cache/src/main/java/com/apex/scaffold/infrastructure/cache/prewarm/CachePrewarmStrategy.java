package com.apex.scaffold.infrastructure.cache.prewarm;

import reactor.core.publisher.Mono;

/**
 * 缓存预热策略接口
 *
 * <p>定义缓存预热的标准接口，应用启动时自动加载热点数据到缓存中，
 * 避免启动初期大量请求直接打到数据库造成性能问题。
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>系统配置数据：字典、权限、菜单等常用配置项</li>
 *   <li>热点业务数据：首页推荐、热门商品、活跃用户等高频访问数据</li>
 *   <li>静态资源索引：文件列表、CDN 映射表等较少变化的数据</li>
 * </ul>
 *
 * <h2>实现要点</h2>
 * <ul>
 *   <li>预热策略应实现为 Spring Bean，由 {@link com.apex.scaffold.infrastructure.cache.service.CachePrewarmService} 自动发现和执行</li>
 *   <li>预热操作应该是幂等的，支持重复执行而不产生副作用</li>
 *   <li>预热失败不应阻塞应用启动，应优雅降级并记录日志</li>
 *   <li>大批量数据预热应分批加载，避免内存溢出和长时间阻塞</li>
 * </ul>
 *
 * <h2>配置控制</h2>
 * <p>通过配置项 {@code apex.cache.prewarm.enabled=true} 启用缓存预热功能，
 * 并可设置 {@code apex.cache.prewarm.startup-delay-seconds} 控制启动后延迟多久开始预热。
 *
 * <h2>实现示例</h2>
 * <pre>{@code
 * @Component
 * public class UserCachePrewarmStrategy implements CachePrewarmStrategy {
 *
 *     private final UserRepository userRepository;
 *     private final MultiLevelCacheService cacheService;
 *
 *     @Override
 *     public String getName() {
 *         return "user-cache-prewarm";
 *     }
 *
 *     @Override
 *     public Mono<Integer> prewarm() {
 *         return userRepository.findActiveUsers()
 *             .flatMap(user -> cacheService.set("user:" + user.getId(), user, Duration.ofHours(1)))
 *             .count()
 *             .map(Long::intValue);
 *     }
 * }
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see com.apex.scaffold.infrastructure.cache.service.CachePrewarmService
 * @see com.apex.scaffold.infrastructure.cache.service.MultiLevelCacheService
 */
public interface CachePrewarmStrategy {

    /**
     * 获取预热策略名称
     *
     * <p>用于日志记录和策略识别，应返回一个简洁且具有业务含义的名称。
     * 建议使用 kebab-case 命名风格，如 "user-cache-prewarm"、"product-cache-prewarm"。
     *
     * @return 策略名称，不能为 null
     */
    String getName();

    /**
     * 执行缓存预热操作
     *
     * <p>从数据源（通常是数据库）加载数据并写入缓存。
     * 预热过程应该是异步非阻塞的，使用 Reactor 响应式编程模型。
     *
     * <h3>执行要求</h3>
     * <ul>
     *   <li>幂等性：支持重复执行，不会因为缓存已存在而失败</li>
     *   <li>容错性：单条数据预热失败不应导致整个预热过程中断</li>
     *   <li>性能考虑：大批量数据应分批加载，避免一次性加载过多数据导致 OOM</li>
     *   <li>异步执行：使用适当的调度器（如 {@link com.apex.scaffold.common.scheduler.BlockingIoSchedulers}）
     *       隔离阻塞 I/O 操作</li>
     * </ul>
     *
     * <h3>返回值说明</h3>
     * <p>返回成功预热的数据条目数量，用于监控和日志记录。
     * 如果预热过程中部分数据失败，返回实际成功的数量；
     * 如果整个预热失败，可以返回 0 或传播异常（异常会被 CachePrewarmService 捕获并记录）。
     *
     * @return 成功预热的数据条目数量（Mono 包装）
     */
    Mono<Integer> prewarm();
}
