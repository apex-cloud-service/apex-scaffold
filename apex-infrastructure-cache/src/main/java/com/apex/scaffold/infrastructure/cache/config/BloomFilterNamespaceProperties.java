package com.apex.scaffold.infrastructure.cache.config;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * BloomFilter 命名空间容量配置
 *
 * <p>为不同业务域的 BloomFilter 配置独立的容量和误判率参数。
 *
 * <h2>为什么需要分域配置</h2>
 * <ul>
 *   <li>不同业务域的数据量差异巨大（用户 500 万 vs 配置项 1 万）</li>
 *   <li>统一容量会导致小数据量域浪费内存，大数据量域误判率上升</li>
 *   <li>分域配置可以精确控制内存占用和误判率</li>
 * </ul>
 *
 * <h2>配置示例</h2>
 * <pre>
 * apex:
 *   cache:
 *     bloom-filter:
 *       enabled: true
 *       # 全局默认配置（未配置的命名空间使用此值）
 *       expected-insertions: 1000000
 *       fpp: 0.01
 *       # 分域配置（覆盖全局默认值）
 *       namespaces:
 *         user:
 *           expected-insertions: 5000000   # 500 万用户
 *           fpp: 0.01                      # 1% 误判率
 *         order:
 *           expected-insertions: 10000000  # 1000 万订单
 *           fpp: 0.01
 *         media:
 *           expected-insertions: 2000000   # 200 万媒体
 *           fpp: 0.005                     # 0.5% 误判率（更严格）
 *         config:
 *           expected-insertions: 10000     # 1 万配置项
 *           fpp: 0.01
 * </pre>
 *
 * <h2>容量规划建议</h2>
 * <table>
 *   <caption>不同数据量级的推荐配置</caption>
 *   <tr><th>数据量</th><th>expected-insertions</th><th>fpp</th><th>内存占用</th></tr>
 *   <tr><td>1 万</td><td>10,000</td><td>0.01</td><td>12 KB</td></tr>
 *   <tr><td>10 万</td><td>100,000</td><td>0.01</td><td>120 KB</td></tr>
 *   <tr><td>100 万</td><td>1,000,000</td><td>0.01</td><td>1.2 MB</td></tr>
 *   <tr><td>500 万</td><td>5,000,000</td><td>0.01</td><td>6.0 MB</td></tr>
 *   <tr><td>1000 万</td><td>10,000,000</td><td>0.01</td><td>12.0 MB</td></tr>
 * </table>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.1.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "apex.cache.bloom-filter")
public class BloomFilterNamespaceProperties {

    /**
     * 命名空间配置映射
     *
     * <p>Key: 命名空间名称（如 "user", "order"）
     * <p>Value: 该命名空间的容量配置
     */
    private Map<String, NamespaceConfig> namespaces = new HashMap<>();

    /**
     * 命名空间配置类
     */
    @Data
    public static class NamespaceConfig {

        /**
         * 预期插入数量
         */
        private long expectedInsertions;

        /**
         * 误判率（0-1）
         */
        private double fpp;

        /**
         * 是否启用自适应扩容（默认 false）
         *
         * <p>启用后，当实际数据量超过 expectedInsertions 的 80% 时，
         * 自动扩容到 2 倍容量并重建 BloomFilter。
         */
        private boolean autoScale = false;

        /**
         * 自适应扩容阈值（默认 0.8，即 80%）
         */
        private double scaleThreshold = 0.8;
    }

    /**
     * 获取指定命名空间的配置
     *
     * @param namespace 命名空间名称
     * @return 命名空间配置，未配置时返回 null
     */
    public NamespaceConfig getNamespaceConfig(String namespace) {
        return namespaces.get(namespace);
    }

    /**
     * 判断命名空间是否有独立配置
     *
     * @param namespace 命名空间名称
     * @return 是否有独立配置
     */
    public boolean hasNamespaceConfig(String namespace) {
        return namespaces.containsKey(namespace);
    }
}
