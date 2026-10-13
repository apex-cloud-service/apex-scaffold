package com.apex.scaffold.interfaces.dto.monitor;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 监控概览响应（/admin-api/v1/monitor/overview）。
 *
 *
 *<p>为监控大盘顶部指标卡提供聚合快照：应用状态、运行时长、
 *堆内存、CPU、线程、会话以及请求/治理/核心业务计数。</p>
 *
 *@param application 应用名称
 *@param version 应用版本
 *@param status 运行状态（UP / DOWN / 降级）
 *@param uptimeSeconds 进程已运行时长（秒）
 *@param startTime 进程启动时间
 *@param timestamp 采集时间戳（毫秒）
 *@param heapUsed 堆内存已用（字节）
 *@param heapMax 堆内存最大值（字节）
 *@param heapUsedPercent 堆内存使用率（0~1，1 表示已满）
 *@param nonHeapUsed 非堆内存已用（字节）
 *@param cpuUsage 进程 CPU 使用率（0~1）
 *@param systemCpuUsage 系统 CPU 使用率（0~1）
 *@param threadCount 当前存活线程数
 *@param activeSessions 活跃会话数（NaN 表示采集不可用）
 *@param totalRequests 累计 API 请求数
 *@param error4xx 累计 4xx 错误数
 *@param error5xx 累计 5xx 错误数
 *@param avgResponseTime 平均响应时间（秒）
 *@param p95ResponseTime 响应时间 P95 分位（秒）
 *@param p99ResponseTime 响应时间 P99 分位（秒）
 *@param gatewayRequests 累计网关请求数
 *@param rateLimitRejected 限流拒绝次数
 *@param circuitBreakerOpened 熔断打开次数
 *@param loginFailed 登录失败次数
 *@param loginSuccess 登录成功次数
 *@param registerCount 注册成功次数
 *@param uploadCount 文件上传成功次数
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Schema(description = "监控概览（大盘指标卡）")
public record MonitorOverviewResponse(
        @Schema(description = "应用名称") String application,
        @Schema(description = "应用版本") String version,
        @Schema(description = "运行状态 UP/DOWN") String status,
        @Schema(description = "进程已运行时长（秒）") long uptimeSeconds,
        @Schema(description = "进程启动时间") String startTime,
        @Schema(description = "采集时间戳（毫秒）") long timestamp,
        @Schema(description = "堆内存已用（字节）") double heapUsed,
        @Schema(description = "堆内存最大值（字节）") double heapMax,
        @Schema(description = "堆内存使用率 0~1") double heapUsedPercent,
        @Schema(description = "非堆内存已用（字节）") double nonHeapUsed,
        @Schema(description = "进程 CPU 使用率 0~1") double cpuUsage,
        @Schema(description = "系统 CPU 使用率 0~1") double systemCpuUsage,
        @Schema(description = "当前存活线程数") double threadCount,
        @Schema(description = "活跃会话数") double activeSessions,
        @Schema(description = "累计 API 请求数") double totalRequests,
        @Schema(description = "累计 4xx 错误数") double error4xx,
        @Schema(description = "累计 5xx 错误数") double error5xx,
        @Schema(description = "平均响应时间（秒）") double avgResponseTime,
        @Schema(description = "响应时间 P95 分位（秒）") double p95ResponseTime,
        @Schema(description = "响应时间 P99 分位（秒）") double p99ResponseTime,
        @Schema(description = "累计网关请求数") double gatewayRequests,
        @Schema(description = "限流拒绝次数") double rateLimitRejected,
        @Schema(description = "熔断打开次数") double circuitBreakerOpened,
        @Schema(description = "登录失败次数") double loginFailed,
        @Schema(description = "登录成功次数") double loginSuccess,
        @Schema(description = "注册成功次数") double registerCount,
        @Schema(description = "文件上传成功次数") double uploadCount) {}
