package com.apex.scaffold.interfaces.governance.platform;

/**
 * 部署平台治理能力。
 *
 * @param readiness 是否具备就绪探针能力
 * @param registration 是否支持服务注册
 * @param externalScaling 是否支持外部扩缩容判定
 * @param healthCheck 是否提供健康检查端点
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public record GovernanceCapabilities(
        boolean readiness, boolean registration, boolean externalScaling, boolean healthCheck) {}
