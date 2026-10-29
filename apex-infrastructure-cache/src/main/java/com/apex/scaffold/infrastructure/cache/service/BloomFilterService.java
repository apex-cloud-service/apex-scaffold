package com.apex.scaffold.infrastructure.cache.service;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.infrastructure.cache.config.CacheProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 布隆过滤器服务
 *
 * <p>基于 Redisson 实现的分布式布隆过滤器服务，主要用于缓存穿透防护。
 * 通过布隆过滤器快速判断某个键是否可能存在于数据源中，拦截一定不存在的键，
 * 避免大量无效请求穿透到数据库。
 *
 * <h2>工作原理</h2>
 * <p>布隆过滤器是一种空间高效的概率型数据结构，支持快速成员资格查询：
 * <ul>
 *   <li><b>判断存在</b>：如果布隆过滤器返回"可能存在"，则键可能在数据源中（需进一步查询确认）</li>
 *   <li><b>判断不存在</b>：如果布隆过滤器返回"一定不存在"，则键绝对不在数据源中（直接拦截）</li>
 * </ul>
 *
 * <h2>误判率</h2>
 * <p>布隆过滤器存在<b>假阳性</b>（False Positive）但不存在假阴性：
 * <ul>
 *   <li>可能将不存在的键误判为存在（假阳性），但不会将存在的键误判为不存在</li>
 *   <li>误判率由容量（{@code expectedInsertions}）和误判率参数（{@code fpp}）控制</li>
 *   <li>默认配置：100万条目容量 + 1% 误判率</li>
 * </ul>
 *
 * <h2>应用场景</h2>
 * <ul>
 *   <li><b>缓存穿透防护</b>：拦截查询不存在的用户 ID、商品 ID 等，防止恶意攻击</li>
 *   <li><b>黑名单过滤</b>：快速判断 IP、用户 ID 是否在黑名单中</li>
 *   <li><b>去重</b>：判断某个请求、事件是否已经处理过</li>
 * </ul>
 *
 * <h2>配置示例</h2>
 * <pre>
 * apex:
 *   cache:
 *     bloom-filter:
 *       enabled: true                    # 启用布隆过滤器
 *       expected-insertions: 1000000     # 预期插入 100 万条数据
 *       fpp: 0.01                        # 1% 误判率
 *       redis-key-prefix: "apex:cache:bloom:"
 * </pre>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 初始化：将所有有效用户 ID 添加到布隆过滤器
 * bloomFilterService.addBatch("user-ids", allUserIds).subscribe();
 *
 * // 查询前先检查布隆过滤器
 * bloomFilterService.mightContain("user-ids", userId)
 *     .flatMap(exists -> {
 *         if (!exists) {
 *             // 布隆过滤器判断不存在，直接返回空
 *             return Mono.empty();
 *         }
 *         // 可能存在，查询数据库
 *         return userRepository.findById(userId);
 *     });
 * }</pre>
 *
 * <h2>注意事项</h2>
 * <ul>
 *   <li>布隆过滤器不支持删除操作，如果需要删除，只能重建整个过滤器</li>
 *   <li>过滤器容量应根据实际数据量合理配置，容量过小会导致误判率上升</li>
 *   <li>查询失败时默认返回 true（假设存在），避免因过滤器故障导致正常请求被误拦截</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see RBloomFilter
 * @see CacheProperties.BloomFilter
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "apex.cache.bloom-filter", name = "enabled", havingValue = "true")
public class BloomFilterService {

    private final RedissonClient redissonClient;

    private final CacheProperties cacheProperties;

    /**
     * 判断值是否可能存在于布隆过滤器中
     *
     * <p>布隆过滤器的核心查询方法，返回值含义：
     * <ul>
     *   <li><b>true</b>：该值<b>可能存在</b>（需进一步查询确认，存在假阳性概率）</li>
     *   <li><b>false</b>：该值<b>一定不存在</b>（可直接拦截，无假阴性）</li>
     * </ul>
     *
     * <h3>容错处理</h3>
     * <p>查询失败时默认返回 {@code true}（假设存在），避免因布隆过滤器故障导致正常请求被误拦截。
     *
     * @param filterName 布隆过滤器名称，不能为 null
     * @param value      待查询的值，不能为 null
     * @return {@code true} 表示可能存在，{@code false} 表示一定不存在
     */
    public Mono<Boolean> mightContain(String filterName, String value) {
        if (filterName == null || value == null) {
            return Mono.just(false);
        }
        return Mono.fromCallable(() -> {
                    RBloomFilter<String> bloomFilter = getOrCreateBloomFilter(filterName);
                    boolean exists = bloomFilter.contains(value);
                    log.debug("布隆过滤器检查, filterName: {}, value: {}, exists: {}", filterName, value, exists);
                    return exists;
                })
                .subscribeOn(BlockingIoSchedulers.cache())
                .onErrorResume(ex -> {
                    log.error("布隆过滤器检查失败, filterName: {}, value: {}", filterName, value, ex);
                    return Mono.just(true);
                });
    }

    /**
     * 将单个值添加到布隆过滤器
     *
     * <p>将值添加到指定名称的布隆过滤器中。如果过滤器不存在，会根据配置自动初始化。
     *
     * <h3>幂等性</h3>
     * <p>重复添加相同的值是安全的，不会产生副作用。
     *
     * <h3>容错处理</h3>
     * <p>添加失败时会记录错误日志但不会抛出异常，避免影响主流程。
     *
     * @param filterName 布隆过滤器名称，不能为 null
     * @param value      待添加的值，不能为 null
     * @return 完成信号（无返回值）
     */
    public Mono<Void> add(String filterName, String value) {
        if (filterName == null || value == null) {
            return Mono.empty();
        }
        return Mono.fromCallable(() -> {
                    RBloomFilter<String> bloomFilter = getOrCreateBloomFilter(filterName);
                    boolean added = bloomFilter.add(value);
                    log.debug("布隆过滤器添加, filterName: {}, value: {}, added: {}", filterName, value, added);
                    return added;
                })
                .subscribeOn(BlockingIoSchedulers.cache())
                .then()
                .onErrorResume(ex -> {
                    log.error("布隆过滤器添加失败, filterName: {}, value: {}", filterName, value, ex);
                    return Mono.empty();
                });
    }

    /**
     * 批量将多个值添加到布隆过滤器
     *
     * <p>批量添加操作比单个添加更高效，适用于初始化场景或大批量数据预热。
     * 如果过滤器不存在，会根据配置自动初始化。
     *
     * <h3>使用场景</h3>
     * <ul>
     *   <li>应用启动时，将所有有效的业务 ID 批量加载到布隆过滤器</li>
     *   <li>定期同步数据库中的最新数据到布隆过滤器</li>
     * </ul>
     *
     * <h3>容错处理</h3>
     * <p>单个值添加失败不会中断整个批量操作，失败的条目会被跳过。
     * 操作完成后会记录实际成功添加的数量。
     *
     * @param filterName 布隆过滤器名称，不能为 null
     * @param values     待添加的值集合，不能为 null
     * @return 完成信号（无返回值）
     */
    public Mono<Void> addBatch(String filterName, Iterable<String> values) {
        if (filterName == null || values == null) {
            return Mono.empty();
        }
        return Mono.fromCallable(() -> {
                    RBloomFilter<String> bloomFilter = getOrCreateBloomFilter(filterName);
                    int count = 0;
                    for (String value : values) {
                        if (value != null && bloomFilter.add(value)) {
                            count++;
                        }
                    }
                    log.debug("布隆过滤器批量添加, filterName: {}, count: {}", filterName, count);
                    return count;
                })
                .subscribeOn(BlockingIoSchedulers.cache())
                .then()
                .onErrorResume(ex -> {
                    log.error("布隆过滤器批量添加失败, filterName: {}", filterName, ex);
                    return Mono.empty();
                });
    }

    /**
     * 获取布隆过滤器中的元素数量（近似值）
     *
     * <p>返回布隆过滤器中已添加的元素数量估算值。
     * 由于布隆过滤器的概率性质，此数量是近似的，不是精确值。
     *
     * <h3>注意事项</h3>
     * <ul>
     *   <li>计数值是估算的，误差随着元素数量增加而增大</li>
     *   <li>如果过滤器不存在，返回 0</li>
     *   <li>此操作主要用于监控和调试，不应作为业务逻辑判断依据</li>
     * </ul>
     *
     * @param filterName 布隆过滤器名称，不能为 null
     * @return 元素数量（近似值）
     */
    public Mono<Long> count(String filterName) {
        if (filterName == null) {
            return Mono.just(0L);
        }
        return Mono.fromCallable(() -> {
                    RBloomFilter<String> bloomFilter = getBloomFilter(filterName);
                    if (bloomFilter == null || !bloomFilter.isExists()) {
                        return 0L;
                    }
                    return bloomFilter.count();
                })
                .subscribeOn(BlockingIoSchedulers.cache())
                .onErrorResume(ex -> {
                    log.error("布隆过滤器计数失败, filterName: {}", filterName, ex);
                    return Mono.just(0L);
                });
    }

    /**
     * 删除布隆过滤器
     *
     * <p>从 Redis 中删除指定名称的布隆过滤器及其所有数据。
     * 此操作不可逆，删除后需要重新初始化和添加数据。
     *
     * <h3>使用场景</h3>
     * <ul>
     *   <li>数据源发生重大变更，需要重建布隆过滤器</li>
     *   <li>过滤器配置参数调整（容量或误判率），需要删除后重建</li>
     *   <li>清理不再使用的过滤器，释放 Redis 内存</li>
     * </ul>
     *
     * <h3>注意事项</h3>
     * <p>删除后，所有依赖此过滤器的查询将失去保护，
     * 应确保在删除前已停止相关业务查询，或准备好降级策略。
     *
     * @param filterName 布隆过滤器名称，不能为 null
     * @return 完成信号（无返回值）
     */
    public Mono<Void> delete(String filterName) {
        if (filterName == null) {
            return Mono.empty();
        }
        return Mono.fromCallable(() -> {
                    RBloomFilter<String> bloomFilter = getBloomFilter(filterName);
                    if (bloomFilter != null && bloomFilter.isExists()) {
                        boolean deleted = bloomFilter.delete();
                        log.info("布隆过滤器删除, filterName: {}, deleted: {}", filterName, deleted);
                        return deleted;
                    }
                    return false;
                })
                .subscribeOn(BlockingIoSchedulers.cache())
                .then()
                .onErrorResume(ex -> {
                    log.error("布隆过滤器删除失败, filterName: {}", filterName, ex);
                    return Mono.empty();
                });
    }

    /**
     * 获取或创建布隆过滤器实例
     *
     * <p>如果指定名称的布隆过滤器不存在，则根据配置的参数自动初始化：
     * <ul>
     *   <li>预期插入数量（{@code expectedInsertions}）：默认 100 万</li>
     *   <li>误判率（{@code fpp}）：默认 1%</li>
     * </ul>
     *
     * <p>如果过滤器已存在，直接返回现有实例。
     *
     * @param filterName 布隆过滤器名称
     * @return Redisson 布隆过滤器实例
     */
    private RBloomFilter<String> getOrCreateBloomFilter(String filterName) {
        String redisKey = buildRedisKey(filterName);
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(redisKey);
        if (!bloomFilter.isExists()) {
            long expectedInsertions = cacheProperties.getBloomFilter().getExpectedInsertions();
            double fpp = cacheProperties.getBloomFilter().getFpp();
            bloomFilter.tryInit(expectedInsertions, fpp);
            log.info("布隆过滤器初始化, filterName: {}, expectedInsertions: {}, fpp: {}", filterName, expectedInsertions, fpp);
        }
        return bloomFilter;
    }

    /**
     * 获取已存在的布隆过滤器实例
     *
     * <p>仅查询指定名称的布隆过滤器，不会自动创建。
     * 调用前应确保过滤器已存在，或在使用前检查 {@code isExists()}。
     *
     * @param filterName 布隆过滤器名称
     * @return Redisson 布隆过滤器实例（可能不存在）
     */
    private RBloomFilter<String> getBloomFilter(String filterName) {
        String redisKey = buildRedisKey(filterName);
        return redissonClient.getBloomFilter(redisKey);
    }

    /**
     * 构建布隆过滤器的 Redis 键
     *
     * <p>将业务过滤器名称转换为完整的 Redis 键，格式为：
     * {@code <配置前缀><filterName>}
     *
     * <p>默认前缀：{@code apex:cache:bloom:}
     *
     * @param filterName 业务过滤器名称
     * @return Redis 完整键
     */
    private String buildRedisKey(String filterName) {
        return cacheProperties.getBloomFilter().getRedisKeyPrefix() + filterName;
    }
}
