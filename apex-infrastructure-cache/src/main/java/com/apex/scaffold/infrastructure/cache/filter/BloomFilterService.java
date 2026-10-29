package com.apex.scaffold.infrastructure.cache.filter;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 布隆过滤器服务
 *
 * <p>提供基于 Guava BloomFilter 的缓存穿透防护，适用于大规模键空间的存在性快速判断。
 * 支持多个命名空间隔离的过滤器实例，每个命名空间独立维护预期容量和误判率。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>多命名空间支持：不同业务域使用独立的 BloomFilter 实例</li>
 *   <li>动态创建：首次使用自动创建指定容量和误判率的过滤器</li>
 *   <li>线程安全：基于 ConcurrentHashMap 保证多线程环境下的安全访问</li>
 *   <li>误判率控制：默认 0.01（1%），可根据业务场景调整</li>
 * </ul>
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>防止缓存穿透：在查询缓存前，先用 Bloom Filter 判断 key 是否可能存在</li>
 *   <li>大规模存在性检查：用户ID、订单ID、文件哈希等海量数据的快速过滤</li>
 *   <li>热点数据预热：启动时将数据库中的所有有效 key 加载到过滤器</li>
 * </ul>
 *
 * <h2>工作原理</h2>
 * <pre>
 * 1. 数据预热阶段：将数据库中所有有效 key 加入 BloomFilter
 * 2. 查询阶段：
 *    - BloomFilter.mightContain(key) = false → key 一定不存在，直接返回空
 *    - BloomFilter.mightContain(key) = true → key 可能存在，继续查询缓存和数据库
 * </pre>
 *
 * <h2>性能特征</h2>
 * <ul>
 *   <li>空间效率：100万元素、1%误判率仅需约 1.2MB 内存</li>
 *   <li>时间复杂度：O(k)，k 为哈希函数个数，通常为常数级</li>
 *   <li>误判情况：可能将不存在的 key 判断为存在（假阳性），但不会将存在的判断为不存在</li>
 * </ul>
 *
 * <h2>配置说明</h2>
 * <p>通过 {@code apex.cache.bloom-filter.enabled=true} 启用。
 * 各命名空间的容量和误判率在首次使用时通过代码指定。
 *
 * <h2>注意事项</h2>
 * <ul>
 *   <li>BloomFilter 不支持删除操作，需要重建才能移除元素</li>
 *   <li>容量规划时预留 20-30% 增长空间，避免超出预期容量导致误判率上升</li>
 *   <li>定期重建：根据业务数据变化周期（如每日凌晨）重新加载过滤器</li>
 *   <li>冷启动预热：应用启动时异步加载数据库 key，避免启动阶段大量穿透</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.1.0
 */
@Slf4j
@Service
@ConditionalOnProperty(
        prefix = "apex.cache.bloom-filter",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
public class BloomFilterService {

    /** 默认预期元素数量 */
    private static final int DEFAULT_EXPECTED_INSERTIONS = 1_000_000;

    /** 默认误判率：1% */
    private static final double DEFAULT_FPP = 0.01;

    /** 命名空间到 BloomFilter 实例的映射 */
    private final ConcurrentMap<String, BloomFilter<String>> filters = new ConcurrentHashMap<>();

    /**
     * 判断 key 是否可能存在于指定命名空间
     *
     * <p>返回 false 表示 key 一定不存在；返回 true 表示 key 可能存在（存在误判可能）。
     *
     * @param namespace 命名空间，如 "user"、"order"
     * @param key 待检查的键
     * @return true-可能存在，false-一定不存在
     */
    public boolean mightContain(String namespace, String key) {
        BloomFilter<String> filter = filters.get(namespace);
        if (filter == null) {
            log.warn("BloomFilter 命名空间未初始化: {}, 跳过检查", namespace);
            // 未初始化时保守返回可能存在，避免误拦截
            return true;
        }
        return filter.mightContain(key);
    }

    /**
     * 将 key 添加到指定命名空间的 BloomFilter
     *
     * <p>如果命名空间的过滤器尚不存在，会自动创建默认容量和误判率的实例。
     *
     * @param namespace 命名空间
     * @param key 待添加的键
     */
    public void put(String namespace, String key) {
        BloomFilter<String> filter = getOrCreateFilter(namespace, DEFAULT_EXPECTED_INSERTIONS, DEFAULT_FPP);
        filter.put(key);
    }

    /**
     * 批量添加 key 到指定命名空间
     *
     * @param namespace 命名空间
     * @param keys 待添加的键列表
     */
    public void putAll(String namespace, Iterable<String> keys) {
        BloomFilter<String> filter = getOrCreateFilter(namespace, DEFAULT_EXPECTED_INSERTIONS, DEFAULT_FPP);
        for (String key : keys) {
            filter.put(key);
        }
        log.info("批量添加到 BloomFilter 完成, namespace: {}", namespace);
    }

    /**
     * 创建或重建指定命名空间的 BloomFilter
     *
     * <p>用于定期重建过滤器，清除过期数据并重新加载有效数据。
     *
     * @param namespace 命名空间
     * @param expectedInsertions 预期元素数量
     * @param fpp 误判率（False Positive Probability）
     * @return 新创建的 BloomFilter 实例
     */
    public BloomFilter<String> createOrRebuild(String namespace, int expectedInsertions, double fpp) {
        BloomFilter<String> newFilter =
                BloomFilter.create(Funnels.stringFunnel(StandardCharsets.UTF_8), expectedInsertions, fpp);
        filters.put(namespace, newFilter);
        log.info(
                "BloomFilter 已创建/重建, namespace: {}, expectedInsertions: {}, fpp: {}",
                namespace,
                expectedInsertions,
                fpp);
        return newFilter;
    }

    /**
     * 获取或创建 BloomFilter 实例
     *
     * @param namespace 命名空间
     * @param expectedInsertions 预期元素数量
     * @param fpp 误判率
     * @return BloomFilter 实例
     */
    private BloomFilter<String> getOrCreateFilter(String namespace, int expectedInsertions, double fpp) {
        return filters.computeIfAbsent(namespace, k -> {
            log.info(
                    "首次使用，创建 BloomFilter, namespace: {}, expectedInsertions: {}, fpp: ",
                    namespace,
                    expectedInsertions,
                    fpp);
            return BloomFilter.create(Funnels.stringFunnel(StandardCharsets.UTF_8), expectedInsertions, fpp);
        });
    }

    /**
     * 移除指定命名空间的 BloomFilter
     *
     * <p>用于清理不再使用的过滤器，释放内存。
     *
     * @param namespace 命名空间
     */
    public void remove(String namespace) {
        BloomFilter<String> removed = filters.remove(namespace);
        if (removed != null) {
            log.info("BloomFilter 已移除, namespace: {}", namespace);
        }
    }

    /**
     * 获取所有命名空间的名称列表
     *
     * @return 命名空间名称集合
     */
    public Set<String> getNamespaces() {
        return new HashSet<>(filters.keySet());
    }

    /**
     * 获取当前已初始化的命名空间数量
     *
     * @return 命名空间数量
     */
    public int getNamespaceCount() {
        return filters.size();
    }
}
