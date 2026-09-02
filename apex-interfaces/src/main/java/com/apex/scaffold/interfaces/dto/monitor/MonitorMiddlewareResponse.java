package com.apex.scaffold.interfaces.dto.monitor;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 中间件监控响应（/admin-api/v1/monitor/middleware）。
 *
 *
 *<p>聚合 MySQL 连接池、Redis 健康与派生指标、消息中间件（RocketMQ）
 *与链路追踪（Zipkin）的状态与关键指标。</p>
 *
 *@param timestamp 采集时间戳（毫秒）
 *@param mysql MySQL 指标
 *@param redis Redis 指标
 *@param mq 消息中间件指标
 *@param tracing 链路追踪指标
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Schema(description = "中间件监控（MySQL/Redis/消息中间件/Zipkin）")
public record MonitorMiddlewareResponse(
        @Schema(description = "采集时间戳（毫秒）") long timestamp,
        @Schema(description = "MySQL 指标") MysqlStats mysql,
        @Schema(description = "Redis 指标") RedisStats redis,
        @Schema(description = "消息中间件指标") MqStats mq,
        @Schema(description = "链路追踪指标") TracingStats tracing) {

    /** MySQL 连接池与 SQL 统计。 */
    @Schema(description = "MySQL 指标")
    public record MysqlStats(
            @Schema(description = "数据源是否可用") boolean available,
            @Schema(description = "数据源类型/驱动") String driver,
            @Schema(description = "活动连接数") double activeConnections,
            @Schema(description = "最大连接数") double maxConnections,
            @Schema(description = "空闲连接数") double idleConnections,
            @Schema(description = "连接使用率 0~1") double activeConnectionRatio,
            @Schema(description = "累计 SQL 请求数") double totalRequests,
            @Schema(description = "累计 SQL 错误数") double totalErrors,
            @Schema(description = "慢 SQL 数（阈值外）") double slowSqlCount) {}

    /** Redis 健康与派生指标。 */
    @Schema(description = "Redis 指标")
    public record RedisStats(
            @Schema(description = "Redis 是否可用") boolean available,
            @Schema(description = "节点健康（PING 探针）") boolean healthy,
            @Schema(description = "节点数") double nodes,
            @Schema(description = "派生指标采集是否成功") boolean up,
            @Schema(description = "活跃会话数") double activeSessions,
            @Schema(description = "IP 黑名单大小") double ipBlacklistSize,
            @Schema(description = "Token 黑名单大小") double tokenBlacklistSize,
            @Schema(description = "最近成功采集时间戳（Unix 秒）") double lastSuccessTimestamp) {}

    /** 消息中间件（RocketMQ）指标。 */
    @Schema(description = "消息中间件指标")
    public record MqStats(
            @Schema(description = "是否已接入消息中间件") boolean available,
            @Schema(description = "中间件类型") String type,
            @Schema(description = "NameServer 地址") String nameServer,
            @Schema(description = "生产组") String producerGroup,
            @Schema(description = "生产者状态（RUNNING/未启动）") String producerStatus,
            @Schema(description = "Topic 数") int topics) {}

    /** 链路追踪（Zipkin）指标。 */
    @Schema(description = "链路追踪指标")
    public record TracingStats(
            @Schema(description = "是否已启用链路追踪") boolean available,
            @Schema(description = "追踪类型（Brave/Zipkin）") String type,
            @Schema(description = "Zipkin 上报地址") String endpoint,
            @Schema(description = "采样率 0~1") double samplingRate,
            @Schema(description = "待上报 Span 数") long spansPending,
            @Schema(description = "丢弃 Span 数") long spansDropped) {}
}
