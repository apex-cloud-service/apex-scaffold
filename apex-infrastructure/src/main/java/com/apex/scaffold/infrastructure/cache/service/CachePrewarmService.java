package com.apex.scaffold.infrastructure.cache.service;

import com.apex.scaffold.infrastructure.cache.config.CacheProperties;
import com.apex.scaffold.infrastructure.cache.prewarm.CachePrewarmStrategy;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 缓存预热服务
 *
 * <p>应用启动时自动执行缓存预热策略，将热点数据提前加载到缓存中，
 * 避免启动初期大量请求直接访问数据库导致的性能问题和缓存雪崩。
 *
 * <h2>工作原理</h2>
 * <p>实现 {@link ApplicationRunner} 接口，在 Spring Boot 应用启动完成后自动触发：
 * <ol>
 *   <li>延迟指定时间（默认 30 秒）等待应用完全就绪</li>
 *   <li>自动发现所有实现了 {@link CachePrewarmStrategy} 接口的 Bean</li>
 *   <li>并发执行所有预热策略，将数据加载到缓存</li>
 *   <li>记录预热结果和统计信息</li>
 * </ol>
 *
 * <h2>启用条件</h2>
 * <p>通过配置项 {@code apex.cache.prewarm.enabled=true} 启用缓存预热功能。
 * 未启用时，此服务不会被实例化。
 *
 * <h2>配置示例</h2>
 * <pre>
 * apex:
 *   cache:
 *     prewarm:
 *       enabled: true                   # 启用缓存预热
 *       startup-delay-seconds: 30       # 启动后延迟 30 秒开始预热
 *       batch-size: 100                 # 批量预热大小
 *       batch-interval-millis: 100      # 批次间隔 100 毫秒
 * </pre>
 *
 * <h2>预热策略</h2>
 * <p>预热策略通过实现 {@link CachePrewarmStrategy} 接口并注册为 Spring Bean 来定义。
 * 服务会自动发现并执行所有策略：
 * <pre>{@code
 * @Component
 * public class UserCachePrewarmStrategy implements CachePrewarmStrategy {
 *     @Override
 *     public String getName() {
 *         return "user-cache-prewarm";
 *     }
 *
 *     @Override
 *     public Mono<Integer> prewarm() {
 *         // 加载活跃用户到缓存
 *         return userRepository.findActiveUsers()
 *             .flatMap(user -> cacheService.set("user:" + user.getId(), user, Duration.ofHours(1)))
 *             .count()
 *             .map(Long::intValue);
 *     }
 * }
 * }</pre>
 *
 * <h2>执行特性</h2>
 * <ul>
 *   <li><b>异步非阻塞</b>：使用 Reactor 响应式编程，不阻塞应用启动</li>
 *   <li><b>容错处理</b>：单个策略失败不影响其他策略执行，失败策略返回 0</li>
 *   <li><b>并发执行</b>：多个预热策略并发执行，提高预热效率</li>
 *   <li><b>手动触发</b>：支持通过 {@link #prewarm(String)} 方法手动触发指定策略</li>
 * </ul>
 *
 * <h2>监控指标</h2>
 * <p>预热过程会记录以下指标供监控：
 * <ul>
 *   <li>预热策略总数</li>
 *   <li>每个策略预热的条目数量</li>
 *   <li>预热失败的策略和错误信息</li>
 *   <li>预热总耗时</li>
 * </ul>
 *
 * <h2>注意事项</h2>
 * <ul>
 *   <li>预热策略应该是幂等的，支持重复执行</li>
 *   <li>大批量数据预热应分批加载，避免内存溢出</li>
 *   <li>预热失败不应阻塞应用启动，应优雅降级</li>
 *   <li>预热延迟时间应根据应用启动时间合理设置</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see CachePrewarmStrategy
 * @see ApplicationRunner
 * @see CacheProperties.Prewarm
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "apex.cache.prewarm", name = "enabled", havingValue = "true")
public class CachePrewarmService implements ApplicationRunner {

    private final List<CachePrewarmStrategy> prewarmStrategies;

    private final CacheProperties cacheProperties;

    /**
     * 应用启动时自动触发缓存预热
     *
     * <p>实现 {@link ApplicationRunner#run(ApplicationArguments)} 方法，
     * 在 Spring Boot 应用启动完成后自动执行。
     *
     * <p>执行流程：
     * <ol>
     *   <li>检查是否有可用的预热策略，无策略则跳过</li>
     *   <li>延迟指定时间（配置项：{@code apex.cache.prewarm.startup-delay-seconds}）</li>
     *   <li>异步执行所有预热策略（使用 {@code Schedulers.boundedElastic()}）</li>
     *   <li>记录预热结果和统计信息</li>
     * </ol>
     *
     * @param args 应用启动参数
     * @throws Exception 启动过程中的异常（预热失败不会抛出异常）
     */
    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (prewarmStrategies == null || prewarmStrategies.isEmpty()) {
            log.info("缓存预热策略列表为空，跳过预热");
            return;
        }

        long delaySeconds = cacheProperties.getPrewarm().getStartupDelaySeconds();
        log.info("应用启动，将在 {} 秒后开始缓存预热，预热策略数量: {}", delaySeconds, prewarmStrategies.size());

        Mono.delay(java.time.Duration.ofSeconds(delaySeconds))
                .then(prewarmAll())
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(result -> log.info("缓存预热完成，总计预热条目: {}", result), error -> log.error("缓存预热失败", error));
    }

    /**
     * 执行所有缓存预热策略
     *
     * <p>并发执行所有已注册的 {@link CachePrewarmStrategy} 策略，
     * 并统计总共预热的数据条目数量。
     *
     * <h3>执行特性</h3>
     * <ul>
     *   <li><b>并发执行</b>：多个策略通过 Flux.flatMap 并发执行，提高效率</li>
     *   <li><b>容错处理</b>：单个策略失败不会中断其他策略，失败策略返回 0</li>
     *   <li><b>日志记录</b>：记录每个策略的开始、成功和失败信息</li>
     *   <li><b>结果聚合</b>：使用 reduce 聚合所有策略的预热条目数</li>
     * </ul>
     *
     * @return 所有策略成功预热的总条目数
     */
    public Mono<Integer> prewarmAll() {
        return Flux.fromIterable(prewarmStrategies)
                .flatMap(strategy -> {
                    log.info("开始执行缓存预热策略: {}", strategy.getName());
                    return strategy.prewarm()
                            .doOnSuccess(count -> log.info("缓存预热策略 {} 完成，预热条目: {}", strategy.getName(), count))
                            .doOnError(error -> log.error("缓存预热策略 {} 执行失败", strategy.getName(), error))
                            .onErrorReturn(0);
                })
                .reduce(0, Integer::sum);
    }

    /**
     * 手动触发指定名称的缓存预热策略
     *
     * <p>根据策略名称查找并执行单个预热策略，适用于以下场景：
     * <ul>
     *   <li>数据发生重大变更，需要重新预热特定缓存</li>
     *   <li>新增预热策略后，手动触发一次预热</li>
     *   <li>定期刷新缓存，通过定时任务调用此方法</li>
     *   <li>运维人员通过管理接口手动触发预热</li>
     * </ul>
     *
     * <h3>查找规则</h3>
     * <p>通过 {@link CachePrewarmStrategy#getName()} 返回的名称精确匹配。
     * 如果找不到匹配的策略，返回 0 并记录警告日志。
     *
     * @param strategyName 预热策略名称，不能为 null
     * @return 预热的数据条目数量，未找到策略时返回 0
     */
    public Mono<Integer> prewarm(String strategyName) {
        return Flux.fromIterable(prewarmStrategies)
                .filter(strategy -> strategy.getName().equals(strategyName))
                .next()
                .flatMap(strategy -> {
                    log.info("手动触发缓存预热策略: {}", strategyName);
                    return strategy.prewarm();
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("未找到缓存预热策略: {}", strategyName);
                    return Mono.just(0);
                }));
    }
}
