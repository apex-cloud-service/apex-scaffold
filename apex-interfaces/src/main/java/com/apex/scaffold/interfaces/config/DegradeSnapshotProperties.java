package com.apex.scaffold.interfaces.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 降级快照兜底配置属性。
 *
 * <p>前缀 {@code apex.degrade.snapshot}，由 {@code com.apex.scaffold.Application} 上的
 * {@code @ConfigurationPropertiesScan} 自动发现。控制降级快照的保存开关、Redis
 * 写入节流窗口、本地缓存容量上限以及接入快照保存的资源白名单。</p>
 *
 * <h2>设计要点</h2>
 * <ul>
 *   <li><b>总开关默认开启</b>：{@code enabled} 默认 {@code true}，关闭时快照保存整体跳过；</li>
 *   <li><b>白名单默认空列表</b>：{@link #getResources()} 默认为空，表示默认不接入任何接口，
 *       保证对现有功能零影响；接入需在部署环境显式声明资源名（如 {@code /api/v1/rankings/rules}）；</li>
 *   <li><b>写放大控制</b>：同一资源在 {@code redisThrottleSeconds} 窗口内最多写一次 Redis，
 *       本地缓存不受节流限制（始终更新，保证最新数据）。</li>
 * </ul>
 *
 * <h2>配置示例（application.yml）</h2>
 * <pre>{@code * apex:
 *   degrade:
 *     snapshot:
 *       enabled: true                       # 总开关
 *       redis-throttle-seconds: 5           # Redis 写入节流窗口（秒）
 *       local-max-entries: 256              # 本地缓存最大条目数
 *       resources: [ /api/v1/rankings/rules ]  # 快照白名单（显式声明才接入）
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "apex.degrade.snapshot")
public class DegradeSnapshotProperties {

    /** 默认 Redis 写入节流窗口（秒）：同一资源在此窗口内最多写一次 Redis。 */
    private static final long DEFAULT_REDIS_THROTTLE_SECONDS = 5L;

    /** 默认本地缓存最大条目数：超出后按最旧条目淘汰，防止内存积攒。 */
    private static final int DEFAULT_LOCAL_MAX_ENTRIES = 256;

    /** 快照保存总开关，默认开启；关闭时 {@code saveSnapshot} 直接返回。 */
    private boolean enabled = true;

    /** Redis 写入节流窗口（秒），默认 5 秒。 */
    private long redisThrottleSeconds = DEFAULT_REDIS_THROTTLE_SECONDS;

    /** 本地缓存最大条目数，默认 256。 */
    private int localMaxEntries = DEFAULT_LOCAL_MAX_ENTRIES;

    /**
     * 快照白名单：仅这些归一化资源保存快照。
     *
     * <p>默认空列表 = 不接入任何接口（零影响兜底）；接入需显式声明，
     * 且必须经过评审（禁止个人化接口，避免快照跨用户错配）。</p>
     */
    private List<String> resources = new ArrayList<>();

    /**
     * 快照保存总开关是否开启。
     *
     * @return {@code true} 表示启用快照保存
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置快照保存总开关。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取 Redis 写入节流窗口（秒）。
     *
     * @return 节流窗口秒数，恒大于等于 0
     */
    public long getRedisThrottleSeconds() {
        return redisThrottleSeconds;
    }

    /**
     * 设置 Redis 写入节流窗口（秒）。
     *
     * @param redisThrottleSeconds 节流窗口秒数
     */
    public void setRedisThrottleSeconds(long redisThrottleSeconds) {
        this.redisThrottleSeconds = Math.max(0L, redisThrottleSeconds);
    }

    /**
     * 获取本地缓存最大条目数。
     *
     * @return 最大条目数，恒大于等于 1
     */
    public int getLocalMaxEntries() {
        return localMaxEntries;
    }

    /**
     * 设置本地缓存最大条目数。
     *
     * @param localMaxEntries 最大条目数
     */
    public void setLocalMaxEntries(int localMaxEntries) {
        this.localMaxEntries = Math.max(1, localMaxEntries);
    }

    /**
     * 获取快照白名单资源列表。
     *
     * @return 归一化资源名列表；空列表表示不接入任何接口
     */
    public List<String> getResources() {
        return resources;
    }

    /**
     * 设置快照白名单资源列表。
     *
     * @param resources 归一化资源名列表
     */
    public void setResources(List<String> resources) {
        this.resources = Optional.ofNullable(resources).orElseGet(ArrayList::new);
    }
}
