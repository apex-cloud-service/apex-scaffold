package com.apex.scaffold.common.capability;

import java.util.Set;

/**
 * 租户已订阅的功能特性集合（D4 能力订阅判定的输入）。
 *
 * <p>由租户套餐解析侧（system 内部能力端点）产出，网关与业务服务消费：
 *
 * <ul>
 *   <li>{@code unbounded=true}：不限量租户（如平台租户），视为全量订阅；</li>
 *   <li>{@code features} 为空集合：未订阅任何能力（不再等同全量；全量必须由
 *       {@code unbounded=true} 显式表达）；</li>
 *   <li>否则按特性编码精确包含判定。</li>
 * </ul>
 *
 * @param unbounded 是否不限量租户
 * @param features 已订阅特性编码集合（不可变，{@code null} 按空集合处理）
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public record TenantFeatures(boolean unbounded, Set<String> features) {

    /** 兼容构造：{@code null} 特性集合按空集合处理，保证后续判定免空指针。 */
    public TenantFeatures {
        features = features == null ? Set.of() : Set.copyOf(features);
    }

    /**
     * 不限量租户工厂。
     *
     * <p>用于未开启能力配额的租户；{@code unbounded} 为 {@code true}，特性集合为空。
     *
     * @return 不限量租户的能力视图
     */
    public static TenantFeatures unlimited() {
        return new TenantFeatures(true, Set.of());
    }

    /**
     * 受限租户工厂。
     *
     * @param features 租户已开通的特性编码集合，允许为 {@code null}（按空集合处理）
     * @return 受限租户的能力视图
     */
    public static TenantFeatures of(Set<String> features) {
        return new TenantFeatures(false, features);
    }
}
