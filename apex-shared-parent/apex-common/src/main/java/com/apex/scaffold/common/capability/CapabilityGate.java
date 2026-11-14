package com.apex.scaffold.common.capability;

import java.util.Set;

/**
 * 租户能力订阅判定组件（D4 共用判定）。
 *
 * <p>输入租户特性集合与能力编码，输出是否已订阅。网关 {@code /open-api/**}
 * 能力门禁与各业务服务的二次校验必须复用同一实现，避免两端判定口径漂移。
 *
 * <p>本类为纯函数工具，不持有状态、不做 IO；特性集合的获取（内部端点 + 缓存）
 * 由调用方自行完成，失败时的 fail-closed 策略同样由调用方决定。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public final class CapabilityGate {

    private CapabilityGate() {}

    /**
     * 判定租户是否已订阅指定能力。
     *
     * @param features 租户特性集合（{@code null} 视为未订阅，fail-closed）
     * @param capabilityCode 能力编码（如 {@code file-storage}）
     * @return 已订阅返回 {@code true}
     */
    public static boolean subscribed(TenantFeatures features, String capabilityCode) {
        if (features == null || capabilityCode == null || capabilityCode.isBlank()) {
            return false;
        }
        if (features.unbounded()) {
            return true;
        }
        Set<String> codes = features.features();
        // 空 features 表示未订阅任何能力；全量订阅必须由 unbounded=true 显式表达，
        // 不能再用空集合兜底为全量（避免配置缺失被静默放大为全开）。
        return codes.contains(capabilityCode);
    }
}
