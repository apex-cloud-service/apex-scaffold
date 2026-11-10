package com.apex.scaffold.common.capability;

import com.apex.scaffold.common.util.JsonUtil;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 租户能力端点返回的能力快照。
 *
 * @param exists 租户是否存在
 * @param features 已订阅能力编码
 * @param unbounded 是否订阅全部能力
 * @param expireAtEpochMillis 快照过期时间
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public record TenantCapabilitySnapshot(
        boolean exists, boolean unbounded, Set<String> features, long expireAtEpochMillis) {

    /** 规范化并保护能力集合。 */
    public TenantCapabilitySnapshot {
        features = features == null ? Set.of() : Set.copyOf(features);
    }

    /**
     * 严格解析能力端点响应并生成带 TTL 的快照。
     *
     * @param body 能力端点响应 JSON
     * @param ttlMillis 缓存 TTL，至少按一秒处理
     * @return 解析后的能力快照
     */
    public static TenantCapabilitySnapshot parse(String body, long ttlMillis) {
        Map<?, ?> root;
        try {
            root = JsonUtil.fromJson(body, Map.class);
        } catch (RuntimeException e) {
            throw new IllegalStateException("租户能力响应解析失败: " + e.getMessage(), e);
        }
        if (root == null || !(root.get("data") instanceof Map<?, ?> feed)) {
            throw new IllegalStateException("租户能力响应缺少 data 节点");
        }
        boolean exists = booleanField(feed, "exists");
        boolean unbounded = booleanField(feed, "unbounded");
        Object rawFeatures = feed.get("features");
        if (!(rawFeatures instanceof List<?> list)) {
            throw new IllegalStateException("租户能力响应 features 必须为数组");
        }
        Set<String> features = new LinkedHashSet<>();
        for (Object item : list) {
            if (!(item instanceof String feature)) {
                throw new IllegalStateException("租户能力响应 features 元素必须为字符串");
            }
            if (!feature.isBlank()) {
                features.add(feature);
            }
        }
        return new TenantCapabilitySnapshot(
                exists, unbounded, features, System.currentTimeMillis() + Math.max(1000, ttlMillis));
    }

    /**
     * 判断快照是否仍在 TTL 内。
     *
     * @return 未过期返回 true
     */
    public boolean fresh() {
        return System.currentTimeMillis() < expireAtEpochMillis;
    }

    /**
     * 判断快照是否订阅指定能力。
     *
     * @param capabilityCode 能力编码
     * @return 已订阅返回 true
     */
    public boolean subscribed(String capabilityCode) {
        return exists && CapabilityGate.subscribed(new TenantFeatures(unbounded, features), capabilityCode);
    }

    private static boolean booleanField(Map<?, ?> feed, String name) {
        Object value = feed.get(name);
        if (!(value instanceof Boolean result)) {
            throw new IllegalStateException("租户能力响应 " + name + " 必须为布尔值");
        }
        return result;
    }
}
