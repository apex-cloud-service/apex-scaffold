package com.apex.scaffold.gateway.log;

import java.time.LocalDateTime;

/**
 * 网关访问日志持久化记录.
 *
 * <p>对应迁移方案 §5.3「访问日志」落库模型，是一条访问日志的最小不可变载荷，
 * 由 {@link com.apex.scaffold.gateway.filter.RequestLoggingFilter} 在链路末端组装，
 * 交由 {@link AccessLogService} 异步写入独立库 {@code apex_gateway.access_log}。
 *
 * <p><b>字段约定</b>：
 * <ul>
 *   <li>{@code id} —— 雪花 ID 主键；组装时可传 {@code null}，由
 *       {@link R2dbcAccessLogRepository} 落库前统一生成</li>
 *   <li>{@code requestHeadersJson} —— <b>已脱敏</b>的请求头 JSON 快照，
 *       敏感凭据一律以 {@code ***} 占位（安全红线，见 {@link AccessLogService#sanitizeHeaders}）</li>
 *   <li>{@code userId}/{@code tenantId} —— 由网关注入的可信身份头，匿名请求可为空</li>
 * </ul>
 *
 * @param id                 雪花 ID 主键（可为 {@code null}，落库前生成）
 * @param traceId            链路追踪 ID（贯穿网关与下游服务）
 * @param method             HTTP 方法（GET/POST/…）
 * @param path               请求路径（不含查询串）
 * @param routeId            命中的网关路由 ID
 * @param serviceId          目标服务 ID（{@code lb://} 后的服务名）
 * @param grayTag            灰度标记（{@code gray}/{@code stable}）
 * @param status             响应 HTTP 状态码
 * @param costMs             端到端耗时（毫秒）
 * @param clientIp           客户端 IP
 * @param userAgent          客户端 User-Agent
 * @param requestHeadersJson 脱敏后的请求头 JSON 快照
 * @param userId             用户 ID（可空）
 * @param tenantId           租户 ID（可空）
 * @param createTime         记录产生时间
 * @author Apex Team
 * @since 1.3.1
 */
public record AccessLogRecord(
        Long id,
        String traceId,
        String method,
        String path,
        String routeId,
        String serviceId,
        String grayTag,
        Integer status,
        Long costMs,
        String clientIp,
        String userAgent,
        String requestHeadersJson,
        String userId,
        String tenantId,
        LocalDateTime createTime) {}
