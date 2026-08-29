package com.apex.scaffold.interfaces.dto.monitor;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 治理监控响应（/admin-api/v1/monitor/governance）。
 *
 * <p>聚合 Sentinel 熔断/限流/授权规则、安全计数、Resilience4j 熔断器状态
 * 与网关/API 流量统计。</p>
 *
 * @param timestamp 采集时间戳（毫秒）
 * @param sentinel Sentinel 规则与状态
 * @param security 安全治理计数
 * @param circuitBreakers 熔断器状态列表
 * @param gateway 网关流量统计
 */
@Schema(description = "治理监控（Sentinel 熔断/限流 + 网关流量 + 熔断器）")
public record MonitorGovernanceResponse(
        @Schema(description = "采集时间戳（毫秒）") long timestamp,
        @Schema(description = "Sentinel 规则与状态") SentinelStats sentinel,
        @Schema(description = "安全治理计数") SecurityCounters security,
        @Schema(description = "熔断器状态列表") List<CircuitBreakerState> circuitBreakers,
        @Schema(description = "网关流量统计") GatewayStats gateway) {

    /** Sentinel 规则统计。 */
    @Schema(description = "Sentinel 规则统计")
    public record SentinelStats(
            @Schema(description = "Sentinel 是否启用") boolean enabled,
            @Schema(description = "限流规则") List<SentinelRule> flowRules,
            @Schema(description = "熔断降级规则") List<SentinelRule> degradeRules,
            @Schema(description = "授权规则") List<SentinelRule> authorityRules,
            @Schema(description = "热点参数规则") List<SentinelRule> paramFlowRules) {}

    /** Sentinel 规则摘要。 */
    @Schema(description = "Sentinel 规则摘要")
    public record SentinelRule(
            @Schema(description = "资源名") String resource,
            @Schema(description = "规则类型（限流/熔断/授权/热点）") String grade,
            @Schema(description = "阈值") double count,
            @Schema(description = "策略描述") String strategy,
            @Schema(description = "控制行为描述") String controlBehavior,
            @Schema(description = "熔断时间窗口（秒）") Integer timeWindow,
            @Schema(description = "是否启用") boolean enabled) {}

    /** 安全治理计数。 */
    @Schema(description = "安全治理计数")
    public record SecurityCounters(
            @Schema(description = "限流拒绝次数") double rateLimitRejected,
            @Schema(description = "熔断打开次数") double circuitBreakerOpened,
            @Schema(description = "IP 封禁次数") double ipBlocked,
            @Schema(description = "登录失败次数") double loginFailed,
            @Schema(description = "XSS 拦截次数") double xssIntercepted,
            @Schema(description = "防重放拒绝次数") double antiReplayRejected,
            @Schema(description = "幂等重复提交拒绝次数") double repeatSubmitRejected) {}

    /** 熔断器状态。 */
    @Schema(description = "熔断器状态")
    public record CircuitBreakerState(
            @Schema(description = "熔断器名称") String name,
            @Schema(description = "状态 CLOSED/OPEN/HALF_OPEN/FORCED_OPEN/DISABLED") String state,
            @Schema(description = "失败率 0~1") double failureRate,
            @Schema(description = "慢调用率 0~1") double slowCallRate,
            @Schema(description = "缓冲调用数") long bufferedCalls,
            @Schema(description = "失败调用数") long failedCalls,
            @Schema(description = "未放行调用数") long notPermittedCalls) {}

    /** 网关流量统计。 */
    @Schema(description = "网关流量统计")
    public record GatewayStats(
            @Schema(description = "累计请求数") double totalRequests,
            @Schema(description = "累计 4xx 错误数") double error4xx,
            @Schema(description = "累计 5xx 错误数") double error5xx,
            @Schema(description = "平均响应时间（秒）") double avgResponseTime,
            @Schema(description = "最大响应时间（秒）") double maxResponseTime,
            @Schema(description = "TOP 路径流量") List<PathTraffic> topPaths) {}

    /** 路径流量。 */
    @Schema(description = "路径流量")
    public record PathTraffic(
            @Schema(description = "请求路径") String path,
            @Schema(description = "HTTP 方法") String method,
            @Schema(description = "请求数") double count,
            @Schema(description = "平均耗时（秒）") double avgTime,
            @Schema(description = "最大耗时（秒）") double maxTime,
            @Schema(description = "错误数") double errorCount,
            @Schema(description = "错误率 0~1") double errorRate) {}
}
