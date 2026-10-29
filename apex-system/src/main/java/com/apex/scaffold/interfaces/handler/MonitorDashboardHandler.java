package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.interfaces.monitor.dashboard.MonitorMetricsCollector;
import com.apex.scaffold.interfaces.util.MessageUtils;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 监控大盘处理器（函数式端点）。
 *
 * <p>提供监控大盘聚合数据接口，数据由 {@link MonitorMetricsCollector}
 * 在进程内聚合 Micrometer / OS / Sentinel / 中间件指标，供管理前端大盘展示。</p>
 *
 * <h2>接口清单</h2>
 * <ul>
 *   <li>GET /admin-api/v1/monitor/overview - 监控概览</li>
 *   <li>GET /admin-api/v1/monitor/system - 系统资源（内存/CPU/磁盘/网络/JVM/线程/GC）</li>
 *   <li>GET /admin-api/v1/monitor/middleware - 中间件（MySQL/Redis/MQ/Zipkin）</li>
 *   <li>GET /admin-api/v1/monitor/governance - 治理（Sentinel 熔断/限流/网关流量）</li>
 *   <li>GET /admin-api/v1/monitor/business - 核心业务指标</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see MonitorMetricsCollector
 */
@Component
@ConditionalOnClass({CircuitBreakerRegistry.class, RocketMQTemplate.class})
@Tag(name = "后台-监控大盘", description = "系统性能与治理监控聚合数据")
public class MonitorDashboardHandler extends BaseHandler {

    /** 监控指标采集器。 */
    private final MonitorMetricsCollector collector;

    /**
     * 创建监控大盘处理器。
     *
     * @param messageUtils 国际化消息工具
     * @param collector 监控指标采集器
     */
    public MonitorDashboardHandler(MessageUtils messageUtils, MonitorMetricsCollector collector) {
        super(messageUtils);
        this.collector = collector;
    }

    /**
     * 监控概览（大盘指标卡）。
     *
     * @param request HTTP 请求
     * @return 统一成功响应，data 为概览数据
     */
    @RequirePermission("system:monitor:view")
    @Operation(operationId = "monitorOverview", summary = "监控概览", description = "应用状态、运行时长、堆内存、CPU、线程、会话与请求/治理/业务计数")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Object.class))),
        @ApiResponse(responseCode = "401", description = "未授权，需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要system:monitor:view权限")
    })
    @OperationLog(
            module = "MonitorDashboard",
            type = OperationLog.OperationType.SELECT,
            description = "overview",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> overview(ServerRequest request) {
        return Mono.fromSupplier(collector::overview).flatMap(this::buildSuccess);
    }

    /**
     * 系统资源监控（内存/CPU/磁盘/网络/JVM/线程/GC）。
     *
     * @param request HTTP 请求
     * @return 统一成功响应，data 为系统资源数据
     */
    @RequirePermission("system:monitor:view")
    @Operation(operationId = "monitorSystem", summary = "系统资源监控", description = "JVM 内存/GC、CPU、进程、线程、磁盘、网络、阻塞 IO 线程池")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Object.class))),
        @ApiResponse(responseCode = "401", description = "未授权，需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要system:monitor:view权限")
    })
    @OperationLog(
            module = "MonitorDashboard",
            type = OperationLog.OperationType.SELECT,
            description = "system",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> system(ServerRequest request) {
        return Mono.fromSupplier(collector::system)
                .subscribeOn(BlockingIoSchedulers.general())
                .flatMap(this::buildSuccess);
    }

    /**
     * 中间件监控（MySQL/Redis/消息中间件/Zipkin）。
     *
     * @param request HTTP 请求
     * @return 统一成功响应，data 为中间件数据
     */
    @RequirePermission("system:monitor:view")
    @Operation(
            operationId = "monitorMiddleware",
            summary = "中间件监控",
            description = "MySQL 连接池、Redis 健康与派生指标、消息中间件、链路追踪状态")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Object.class))),
        @ApiResponse(responseCode = "401", description = "未授权，需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要system:monitor:view权限")
    })
    @OperationLog(
            module = "MonitorDashboard",
            type = OperationLog.OperationType.SELECT,
            description = "middleware",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> middleware(ServerRequest request) {
        return Mono.fromSupplier(collector::middleware).flatMap(this::buildSuccess);
    }

    /**
     * 治理监控（Sentinel 熔断/限流 + 网关流量 + 熔断器）。
     *
     * @param request HTTP 请求
     * @return 统一成功响应，data 为治理数据
     */
    @RequirePermission("system:monitor:view")
    @Operation(
            operationId = "monitorGovernance",
            summary = "治理监控",
            description = "Sentinel 规则、安全计数、Resilience4j 熔断器状态、网关/API 流量")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Object.class))),
        @ApiResponse(responseCode = "401", description = "未授权，需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要system:monitor:view权限")
    })
    @OperationLog(
            module = "MonitorDashboard",
            type = OperationLog.OperationType.SELECT,
            description = "governance",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> governance(ServerRequest request) {
        return Mono.fromSupplier(collector::governance).flatMap(this::buildSuccess);
    }

    /**
     * 核心业务监控指标与趋势。
     *
     * @param request HTTP 请求
     * @return 统一成功响应，data 为业务指标数据
     */
    @RequirePermission("system:monitor:view")
    @Operation(operationId = "monitorBusiness", summary = "核心业务监控", description = "登录/注册/上传/信用变动等核心业务指标与近 10 分钟趋势")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Object.class))),
        @ApiResponse(responseCode = "401", description = "未授权，需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要system:monitor:view权限")
    })
    @OperationLog(
            module = "MonitorDashboard",
            type = OperationLog.OperationType.SELECT,
            description = "business",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> business(ServerRequest request) {
        return Mono.fromSupplier(collector::business).flatMap(this::buildSuccess);
    }
}
