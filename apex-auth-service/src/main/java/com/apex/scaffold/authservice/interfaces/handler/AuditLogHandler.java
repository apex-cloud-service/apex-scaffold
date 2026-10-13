package com.apex.scaffold.authservice.interfaces.handler;

import com.apex.scaffold.auth.application.assembler.AuditLogAssembler;
import com.apex.scaffold.auth.application.dto.AuditLogDTO;
import com.apex.scaffold.auth.application.dto.AuditLogQueryRequest;
import com.apex.scaffold.auth.application.service.AuditLogService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.OperationLog.OperationType;
import com.apex.scaffold.common.annotation.RequireAdminRole;
import com.apex.scaffold.common.annotation.RequireResourcePermission;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.LogSafetyUtil;
import com.apex.scaffold.common.util.TraceIdUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 审计日志Handler
 *
 * <p>提供全面的审计日志管理功能，用于记录、查询和分析系统中的所有操作行为。
 *
 * <p><b>核心功能：</b>
 * <ul>
 *   <li><b>日志查询</b>：支持按用户、事件类型、时间范围、IP地址等多维度查询</li>
 *   <li><b>风险分析</b>：识别和查询失败操作、高风险行为</li>
 *   <li><b>统计分析</b>：提供日志数量统计、登录失败次数统计</li>
 *   <li><b>日志清理</b>：按保留策略清理过期审计日志</li>
 * </ul>
 *
 * <p><b>审计日志内容：</b>
 * <ul>
 *   <li><b>用户行为</b>：登录、登出、密码修改、权限变更</li>
 *   <li><b>资源操作</b>：创建、修改、删除、查询关键资源</li>
 *   <li><b>安全事件</b>：认证失败、授权拒绝、异常访问</li>
 *   <li><b>配置变更</b>：系统配置、角色权限、策略规则</li>
 * </ul>
 *
 * <p><b>日志字段：</b>
 * <ul>
 *   <li><b>操作信息</b>：事件类型、操作结果、错误信息</li>
 *   <li><b>用户信息</b>：用户ID、用户名、租户ID</li>
 *   <li><b>环境信息</b>：IP地址、User-Agent、操作时间</li>
 *   <li><b>资源信息</b>：资源类型、资源ID、变更前后值</li>
 *   <li><b>追踪信息</b>：TraceID、请求路径、执行时长</li>
 * </ul>
 *
 * <p><b>风险等级：</b>
 * <ul>
 *   <li><b>低风险</b>：普通查询、非敏感数据访问</li>
 *   <li><b>中风险</b>：数据修改、配置变更</li>
 *   <li><b>高风险</b>：权限提升、敏感数据导出、批量删除</li>
 * </ul>
 *
 * <p><b>合规要求：</b>
 * <p>审计日志用于满足等保2.0、ISO27001、GDPR等合规要求，
 * 需确保日志完整性、防篡改、长期保存（建议180天以上）。
 *
 * <p><b>多租户支持：</b>
 * <p>所有日志按租户隔离，租户只能查询自己的审计日志。
 *
 * @author Apex Scaffold Team
 * @version 1.0.0
 * @since 2026-09-21
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Tag(name = "审计日志管理", description = "审计日志相关接口")
public class AuditLogHandler {

    private final AuditLogService auditLogService;
    private final AuditLogAssembler auditLogAssembler;

    /**
     * 获取审计日志详情
     *
     * <p>根据日志ID获取完整的审计日志详细信息。
     *
     * <p><b>路径参数：</b>
     * <ul>
     *   <li><b>id</b>：审计日志ID（必填）</li>
     * </ul>
     *
     * <p><b>返回数据：</b>
     * <ul>
     *   <li><b>id</b>：日志ID</li>
     *   <li><b>eventType</b>：事件类型（如LOGIN、LOGOUT、CREATE_USER等）</li>
     *   <li><b>userId</b>：操作用户ID</li>
     *   <li><b>username</b>：操作用户名</li>
     *   <li><b>tenantId</b>：租户ID</li>
     *   <li><b>result</b>：操作结果（SUCCESS/FAILURE）</li>
     *   <li><b>errorMessage</b>：错误信息（失败时）</li>
     *   <li><b>ipAddress</b>：客户端IP地址</li>
     *   <li><b>userAgent</b>：客户端User-Agent</li>
     *   <li><b>requestPath</b>：请求路径</li>
     *   <li><b>resourceType</b>：资源类型</li>
     *   <li><b>resourceId</b>：资源ID</li>
     *   <li><b>riskLevel</b>：风险等级（LOW/MEDIUM/HIGH）</li>
     *   <li><b>traceId</b>：链路追踪ID</li>
     *   <li><b>createdAt</b>：创建时间</li>
     * </ul>
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>安全事件调查 - 查看某次操作的完整细节</li>
     *   <li>审计报告生成 - 导出特定日志的详细信息</li>
     *   <li>问题排查 - 分析失败操作的原因</li>
     * </ul>
     *
     * @param request HTTP请求
     * @return HTTP响应
     */
    @OperationLog(module = "审计日志", type = OperationType.SELECT, description = "获取审计日志详情")
    @Operation(operationId = "getAuditLog", summary = "获取审计日志详情", description = "根据ID获取审计日志详情")
    @io.swagger.v3.oas.annotations.Parameter(name = "id", description = "审计日志ID", required = true, example = "1001")
    @ApiResponse(
            responseCode = "200",
            description = "获取成功",
            content = @Content(schema = @Schema(implementation = AuditLogDTO.class)))
    @ApiResponse(
            responseCode = "404",
            description = "日志不存在",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> getLog(ServerRequest request) {
        Long id = Long.valueOf(request.pathVariable("id"));
        log.debug("Getting audit log: id={}", id);

        return auditLogService
                .getLogById(id)
                .flatMap(entity -> auditLogAssembler.toDto(Mono.just(entity)))
                .flatMap(dto -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(dto)))
                .onErrorResume(e -> {
                    log.error("Failed to get audit log: id={}", id, e);
                    return buildError(ResultCode.ERROR, TraceIdUtil.getTraceId());
                });
    }

    /**
     * 查询用户审计日志
     *
     * <p>查询指定用户的所有审计日志记录。
     *
     * <p><b>路径参数：</b>
     * <ul>
     *   <li><b>userId</b>：用户ID（必填）</li>
     * </ul>
     *
     * <p><b>查询参数：</b>
     * <ul>
     *   <li><b>tenantId</b>：租户ID（必填）</li>
     * </ul>
     *
     * <p><b>返回数据：</b>
     * <p>返回该用户的所有审计日志列表（数组），按时间倒序排列。
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>用户行为分析 - 查看用户的所有操作记录</li>
     *   <li>安全审计 - 追踪特定用户的活动轨迹</li>
     *   <li>合规检查 - 生成用户操作报告</li>
     *   <li>异常检测 - 发现用户的异常行为模式</li>
     * </ul>
     *
     * <p><b>注意事项：</b>
     * <ul>
     *   <li>只返回该租户下该用户的日志，严格租户隔离</li>
     *   <li>建议结合分页参数使用，避免数据量过大</li>
     *   <li>默认返回最近90天的日志</li>
     * </ul>
     *
     * @param request HTTP请求
     * @return HTTP响应
     */
    @OperationLog(module = "审计日志", type = OperationType.SELECT, description = "查询用户审计日志")
    @Operation(operationId = "listUserAuditLogs", summary = "查询用户审计日志", description = "查询指定用户的审计日志列表")
    @io.swagger.v3.oas.annotations.Parameter(name = "userId", description = "用户ID", required = true, example = "1001")
    @io.swagger.v3.oas.annotations.Parameter(
            name = "tenantId",
            description = "租户ID",
            required = true,
            example = "default")
    @ApiResponse(responseCode = "200", description = "查询成功")
    @ApiResponse(
            responseCode = "400",
            description = "参数错误",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> listUserLogs(ServerRequest request) {
        Long userId = Long.valueOf(request.pathVariable("userId"));
        String tenantId =
                request.queryParam("tenantId").orElseThrow(() -> new IllegalArgumentException("tenantId is required"));
        log.debug("Listing user audit logs: userId={}, tenantId={}", userId, LogSafetyUtil.text(tenantId, 64));

        return auditLogService
                .listUserLogs(userId, tenantId)
                .map(auditLogAssembler::toDto)
                .collectList()
                .flatMap(dtos -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(dtos)))
                .onErrorResume(e -> {
                    log.error(
                            "Failed to list user audit logs: userId={}, tenantId={}",
                            userId,
                            LogSafetyUtil.text(tenantId, 64),
                            e);
                    return buildError(ResultCode.ERROR, TraceIdUtil.getTraceId());
                });
    }

    /**
     * 按事件类型查询审计日志
     *
     * <p>查询指定事件类型的所有审计日志记录。
     *
     * <p><b>路径参数：</b>
     * <ul>
     *   <li><b>eventType</b>：事件类型（必填）</li>
     * </ul>
     *
     * <p><b>查询参数：</b>
     * <ul>
     *   <li><b>tenantId</b>：租户ID（必填）</li>
     * </ul>
     *
     * <p><b>常见事件类型：</b>
     * <ul>
     *   <li><b>LOGIN</b>：用户登录</li>
     *   <li><b>LOGOUT</b>：用户登出</li>
     *   <li><b>CREATE_USER</b>：创建用户</li>
     *   <li><b>UPDATE_USER</b>：更新用户</li>
     *   <li><b>DELETE_USER</b>：删除用户</li>
     *   <li><b>CREATE_ROLE</b>：创建角色</li>
     *   <li><b>ASSIGN_PERMISSIONS</b>：分配权限</li>
     *   <li><b>PASSWORD_CHANGE</b>：修改密码</li>
     *   <li><b>MFA_ENABLE</b>：启用MFA</li>
     *   <li><b>EXPORT_DATA</b>：导出数据</li>
     *   <li><b>CONFIG_CHANGE</b>：配置变更</li>
     * </ul>
     *
     * <p><b>返回数据：</b>
     * <p>返回该事件类型的所有审计日志列表（数组），按时间倒序排列。
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>特定事件追踪 - 查看所有登录、登出等特定类型的操作</li>
     *   <li>安全事件分析 - 统计某类安全事件的发生频率</li>
     *   <li>权限变更审计 - 追踪所有权限相关的操作</li>
     *   <li>合规检查 - 验证特定操作的执行记录</li>
     * </ul>
     *
     * @param request HTTP请求
     * @return HTTP响应
     */
    @OperationLog(module = "审计日志", type = OperationType.SELECT, description = "按事件类型查询审计日志")
    @Operation(operationId = "listLogsByEventType", summary = "按事件类型查询", description = "按事件类型查询审计日志列表")
    @io.swagger.v3.oas.annotations.Parameter(
            name = "eventType",
            description = "事件类型（如LOGIN、LOGOUT、CREATE_USER等）",
            required = true,
            example = "LOGIN")
    @io.swagger.v3.oas.annotations.Parameter(
            name = "tenantId",
            description = "租户ID",
            required = true,
            example = "default")
    @ApiResponse(responseCode = "200", description = "查询成功")
    @ApiResponse(
            responseCode = "400",
            description = "参数错误",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> listLogsByEventType(ServerRequest request) {
        String eventType = request.pathVariable("eventType");
        String tenantId =
                request.queryParam("tenantId").orElseThrow(() -> new IllegalArgumentException("tenantId is required"));
        log.debug(
                "Listing logs by event type: eventType={}, tenantId={}",
                LogSafetyUtil.text(eventType, 64),
                LogSafetyUtil.text(tenantId, 64));

        return auditLogService
                .listLogsByEventType(eventType, tenantId)
                .map(auditLogAssembler::toDto)
                .collectList()
                .flatMap(dtos -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(dtos)))
                .onErrorResume(e -> {
                    log.error(
                            "Failed to list logs by event type: eventType={}, tenantId={}",
                            LogSafetyUtil.text(eventType, 64),
                            LogSafetyUtil.text(tenantId, 64),
                            e);
                    return buildError(ResultCode.ERROR, TraceIdUtil.getTraceId());
                });
    }

    /**
     * 按时间范围查询审计日志
     *
     * <p>查询指定时间范围内的所有审计日志记录。
     *
     * <p><b>请求体参数：</b>
     * <ul>
     *   <li><b>startTime</b>：开始时间（必填，ISO-8601格式）</li>
     *   <li><b>endTime</b>：结束时间（必填，ISO-8601格式）</li>
     *   <li><b>tenantId</b>：租户ID（必填）</li>
     * </ul>
     *
     * <p><b>返回数据：</b>
     * <p>返回时间范围内的所有审计日志列表（数组），按时间倒序排列。
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>定期审计 - 生成指定时间段的审计报告</li>
     *   <li>安全事件调查 - 查看特定时间段内的所有操作</li>
     *   <li>异常分析 - 追踪某个时间点前后的操作序列</li>
     *   <li>合规检查 - 导出特定周期的完整审计日志</li>
     * </ul>
     *
     * <p><b>性能建议：</b>
     * <ul>
     *   <li>建议查询范围不超过30天，避免数据量过大</li>
     *   <li>大范围查询建议使用异步导出功能</li>
     *   <li>可结合eventType等条件进一步筛选</li>
     * </ul>
     *
     * <p><b>示例请求：</b>
     * <pre>
     * {
     *   "startTime": "2026-09-01T00:00:00",
     *   "endTime": "2026-09-30T23:59:59",
     *   "tenantId": "default"
     * }
     * </pre>
     *
     * @param request HTTP请求
     * @return HTTP响应
     */
    @OperationLog(module = "审计日志", type = OperationType.SELECT, description = "按时间范围查询审计日志")
    @Operation(operationId = "listLogsByTimeRange", summary = "按时间范围查询", description = "按时间范围查询审计日志列表")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "时间范围查询请求",
            required = true,
            content = @Content(schema = @Schema(implementation = AuditLogQueryRequest.class)))
    @ApiResponse(responseCode = "200", description = "查询成功")
    @ApiResponse(
            responseCode = "400",
            description = "参数错误或时间范围无效",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> listLogsByTimeRange(ServerRequest request) {
        return request.bodyToMono(AuditLogQueryRequest.class)
                .flatMap(req -> {
                    log.debug(
                            "Listing logs by time range: startTime={}, endTime={}, tenantId={}",
                            req.getStartTime(),
                            req.getEndTime(),
                            req.getTenantId());
                    return auditLogService
                            .listLogsByTimeRange(req.getStartTime(), req.getEndTime(), req.getTenantId())
                            .map(auditLogAssembler::toDto)
                            .collectList();
                })
                .flatMap(dtos -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(dtos)))
                .onErrorResume(e -> {
                    log.error("Failed to list logs by time range", e);
                    return buildError(ResultCode.ERROR, TraceIdUtil.getTraceId());
                });
    }

    /**
     * 查询失败日志
     *
     * <p>查询所有操作结果为失败的审计日志记录。
     *
     * <p><b>查询参数：</b>
     * <ul>
     *   <li><b>tenantId</b>：租户ID（必填）</li>
     * </ul>
     *
     * <p><b>返回数据：</b>
     * <p>返回所有result=FAILURE的审计日志列表（数组），按时间倒序排列。
     *
     * <p><b>失败日志类型：</b>
     * <ul>
     *   <li><b>登录失败</b>：密码错误、账号锁定、MFA验证失败</li>
     *   <li><b>授权失败</b>：权限不足、资源不存在</li>
     *   <li><b>操作失败</b>：数据校验失败、业务规则冲突</li>
     *   <li><b>系统错误</b>：网络超时、服务异常</li>
     * </ul>
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>安全监控 - 识别暴力破解、异常访问等攻击行为</li>
     *   <li>问题排查 - 快速定位用户遇到的错误</li>
     *   <li>质量分析 - 统计系统失败率和失败原因</li>
     *   <li>告警触发 - 失败次数超过阈值时触发安全告警</li>
     * </ul>
     *
     * <p><b>重点关注：</b>
     * <ul>
     *   <li>同一用户短时间内多次登录失败（可能是暴力破解）</li>
     *   <li>同一IP大量失败请求（可能是扫描攻击）</li>
     *   <li>高权限操作失败（可能是权限提升尝试）</li>
     * </ul>
     *
     * @param request HTTP请求
     * @return HTTP响应
     */
    @OperationLog(module = "审计日志", type = OperationType.SELECT, description = "查询失败日志")
    @Operation(operationId = "listFailedLogs", summary = "查询失败日志", description = "查询所有失败的审计日志")
    @io.swagger.v3.oas.annotations.Parameter(
            name = "tenantId",
            description = "租户ID",
            required = true,
            example = "default")
    @ApiResponse(responseCode = "200", description = "查询成功")
    @ApiResponse(
            responseCode = "400",
            description = "参数错误",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> listFailedLogs(ServerRequest request) {
        String tenantId =
                request.queryParam("tenantId").orElseThrow(() -> new IllegalArgumentException("tenantId is required"));
        log.debug("Listing failed logs: tenantId={}", LogSafetyUtil.text(tenantId, 64));

        return auditLogService
                .listFailedLogs(tenantId)
                .map(auditLogAssembler::toDto)
                .collectList()
                .flatMap(dtos -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(dtos)))
                .onErrorResume(e -> {
                    log.error("Failed to list failed logs: tenantId={}", LogSafetyUtil.text(tenantId, 64), e);
                    return buildError(ResultCode.ERROR, TraceIdUtil.getTraceId());
                });
    }

    /**
     * 查询高风险日志
     *
     * <p>查询所有风险等级为高的审计日志记录。
     *
     * <p><b>查询参数：</b>
     * <ul>
     *   <li><b>tenantId</b>：租户ID（必填）</li>
     * </ul>
     *
     * <p><b>返回数据：</b>
     * <p>返回所有riskLevel=HIGH的审计日志列表（数组），按时间倒序排列。
     *
     * <p><b>高风险操作类型：</b>
     * <ul>
     *   <li><b>权限提升</b>：赋予超级管理员权限、修改角色权限</li>
     *   <li><b>敏感数据操作</b>：批量导出用户数据、访问敏感配置</li>
     *   <li><b>系统配置变更</b>：修改安全策略、关闭审计功能</li>
     *   <li><b>批量操作</b>：批量删除用户、批量修改权限</li>
     *   <li><b>账号操作</b>：禁用管理员账号、重置管理员密码</li>
     *   <li><b>安全功能</b>：禁用MFA、修改IP白名单</li>
     * </ul>
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>安全审计 - 重点审查高风险操作的合理性</li>
     *   <li>合规检查 - 确保高风险操作有充分授权和记录</li>
     *   <li>异常检测 - 识别可疑的高权限操作</li>
     *   <li>事件响应 - 安全事件发生时快速定位相关操作</li>
     * </ul>
     *
     * <p><b>安全建议：</b>
     * <ul>
     *   <li>高风险日志应实时监控，异常操作立即告警</li>
     *   <li>高风险操作建议启用双重验证（如MFA、审批流）</li>
     *   <li>定期审查高风险日志，确保所有操作都有合理业务需求</li>
     *   <li>高风险日志保留期应长于普通日志（建议1年以上）</li>
     * </ul>
     *
     * @param request HTTP请求
     * @return HTTP响应
     */
    @OperationLog(module = "审计日志", type = OperationType.SELECT, description = "查询高风险日志")
    @Operation(operationId = "listHighRiskLogs", summary = "查询高风险日志", description = "查询所有高风险的审计日志")
    @io.swagger.v3.oas.annotations.Parameter(
            name = "tenantId",
            description = "租户ID",
            required = true,
            example = "default")
    @ApiResponse(responseCode = "200", description = "查询成功")
    @ApiResponse(
            responseCode = "400",
            description = "参数错误",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> listHighRiskLogs(ServerRequest request) {
        String tenantId =
                request.queryParam("tenantId").orElseThrow(() -> new IllegalArgumentException("tenantId is required"));
        log.debug("Listing high risk logs: tenantId={}", LogSafetyUtil.text(tenantId, 64));

        return auditLogService
                .listHighRiskLogs(tenantId)
                .map(auditLogAssembler::toDto)
                .collectList()
                .flatMap(dtos -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(dtos)))
                .onErrorResume(e -> {
                    log.error("Failed to list high risk logs: tenantId={}", LogSafetyUtil.text(tenantId, 64), e);
                    return buildError(ResultCode.ERROR, TraceIdUtil.getTraceId());
                });
    }

    /**
     * 按IP地址查询日志
     *
     * <p>查询指定IP地址的所有审计日志记录。
     *
     * <p><b>路径参数：</b>
     * <ul>
     *   <li><b>ipAddress</b>：IP地址（必填）</li>
     * </ul>
     *
     * <p><b>查询参数：</b>
     * <ul>
     *   <li><b>tenantId</b>：租户ID（必填）</li>
     * </ul>
     *
     * <p><b>返回数据：</b>
     * <p>返回该IP地址的所有审计日志列表（数组），按时间倒序排列。
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>IP追踪 - 追踪某个IP的所有访问行为</li>
     *   <li>攻击分析 - 分析可疑IP的攻击模式和手段</li>
     *   <li>地理分析 - 结合IP归属地分析异常访问</li>
     *   <li>黑名单管理 - 为频繁攻击的IP建立黑名单</li>
     * </ul>
     *
     * <p><b>异常行为识别：</b>
     * <ul>
     *   <li><b>暴力破解</b>：同一IP短时间内大量登录失败</li>
     *   <li><b>扫描攻击</b>：同一IP访问大量不同接口或资源</li>
     *   <li><b>异常登录</b>：同一IP登录多个不同账号</li>
     *   <li><b>异常地理位置</b>：IP归属地与用户常用地不符</li>
     * </ul>
     *
     * <p><b>安全建议：</b>
     * <ul>
     *   <li>结合失败日志分析，识别恶意IP</li>
     *   <li>对高频访问IP进行速率限制</li>
     *   <li>对异常IP触发告警或自动封禁</li>
     * </ul>
     *
     * @param request HTTP请求
     * @return HTTP响应
     */
    @OperationLog(module = "审计日志", type = OperationType.SELECT, description = "按IP地址查询日志")
    @Operation(operationId = "listLogsByIpAddress", summary = "按IP地址查询", description = "按IP地址查询审计日志列表")
    @io.swagger.v3.oas.annotations.Parameter(
            name = "ipAddress",
            description = "IP地址（支持IPv4和IPv6）",
            required = true,
            example = "192.168.1.100")
    @io.swagger.v3.oas.annotations.Parameter(
            name = "tenantId",
            description = "租户ID",
            required = true,
            example = "default")
    @ApiResponse(responseCode = "200", description = "查询成功")
    @ApiResponse(
            responseCode = "400",
            description = "参数错误或IP格式无效",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> listLogsByIpAddress(ServerRequest request) {
        String ipAddress = request.pathVariable("ipAddress");
        String tenantId =
                request.queryParam("tenantId").orElseThrow(() -> new IllegalArgumentException("tenantId is required"));
        log.debug(
                "Listing logs by IP address: ipAddress={}, tenantId={}",
                LogSafetyUtil.text(ipAddress, 64),
                LogSafetyUtil.text(tenantId, 64));

        return auditLogService
                .listLogsByIpAddress(ipAddress, tenantId)
                .map(auditLogAssembler::toDto)
                .collectList()
                .flatMap(dtos -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(dtos)))
                .onErrorResume(e -> {
                    log.error(
                            "Failed to list logs by IP address: ipAddress={}, tenantId={}",
                            LogSafetyUtil.text(ipAddress, 64),
                            LogSafetyUtil.text(tenantId, 64),
                            e);
                    return buildError(ResultCode.ERROR, TraceIdUtil.getTraceId());
                });
    }

    /**
     * 统计日志数量
     *
     * <p>统计指定时间范围内的审计日志数量。
     *
     * <p><b>请求体参数：</b>
     * <ul>
     *   <li><b>startTime</b>：开始时间（必填，ISO-8601格式）</li>
     *   <li><b>endTime</b>：结束时间（必填，ISO-8601格式）</li>
     *   <li><b>tenantId</b>：租户ID（必填）</li>
     * </ul>
     *
     * <p><b>返回数据：</b>
     * <p>返回日志总数（Long类型）。
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>活跃度统计 - 分析系统在不同时段的活跃度</li>
     *   <li>容量规划 - 预估审计日志存储容量需求</li>
     *   <li>趋势分析 - 对比不同时期的操作量变化</li>
     *   <li>性能优化 - 识别高峰时段，优化资源分配</li>
     * </ul>
     *
     * <p><b>示例请求：</b>
     * <pre>
     * {
     *   "startTime": "2026-09-01T00:00:00",
     *   "endTime": "2026-09-30T23:59:59",
     *   "tenantId": "default"
     * }
     * </pre>
     *
     * <p><b>示例响应：</b>
     * <pre>
     * {
     *   "code": 200,
     *   "message": "success",
     *   "data": 12580
     * }
     * </pre>
     *
     * @param request HTTP请求
     * @return HTTP响应
     */
    @OperationLog(module = "审计日志", type = OperationType.SELECT, description = "统计日志数量")
    @Operation(operationId = "countAuditLogs", summary = "统计日志数量", description = "统计指定时间范围内的日志数量")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "时间范围统计请求",
            required = true,
            content = @Content(schema = @Schema(implementation = AuditLogQueryRequest.class)))
    @ApiResponse(responseCode = "200", description = "统计成功")
    @ApiResponse(
            responseCode = "400",
            description = "参数错误",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> countLogs(ServerRequest request) {
        return request.bodyToMono(AuditLogQueryRequest.class)
                .flatMap(req -> {
                    log.debug(
                            "Counting logs: startTime={}, endTime={}, tenantId={}",
                            req.getStartTime(),
                            req.getEndTime(),
                            req.getTenantId());
                    return auditLogService.countLogsByTimeRange(
                            req.getStartTime(), req.getEndTime(), req.getTenantId());
                })
                .flatMap(count -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(count)))
                .onErrorResume(e -> {
                    log.error("Failed to count logs", e);
                    return buildError(ResultCode.ERROR, TraceIdUtil.getTraceId());
                });
    }

    /**
     * 统计登录失败次数
     *
     * <p>统计用户在指定时间后的登录失败次数。
     *
     * <p><b>路径参数：</b>
     * <ul>
     *   <li><b>userId</b>：用户ID（必填）</li>
     * </ul>
     *
     * <p><b>查询参数：</b>
     * <ul>
     *   <li><b>tenantId</b>：租户ID（必填）</li>
     *   <li><b>startTime</b>：开始时间（必填，ISO-8601格式）</li>
     * </ul>
     *
     * <p><b>返回数据：</b>
     * <p>返回登录失败次数（Long类型）。
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>账号锁定判断 - 达到阈值时触发账号锁定</li>
     *   <li>安全告警 - 异常登录失败触发安全告警</li>
     *   <li>暴力破解检测 - 识别暴力破解攻击行为</li>
     *   <li>验证码触发 - 失败次数达到阈值时要求输入验证码</li>
     * </ul>
     *
     * <p><b>安全策略建议：</b>
     * <ul>
     *   <li><b>3次失败</b>：要求输入图形验证码</li>
     *   <li><b>5次失败</b>：账号锁定5分钟</li>
     *   <li><b>10次失败</b>：账号锁定30分钟，触发安全告警</li>
     *   <li><b>20次失败</b>：账号锁定24小时，通知管理员</li>
     * </ul>
     *
     * <p><b>统计时间窗口：</b>
     * <ul>
     *   <li>通常统计最近15分钟或1小时内的失败次数</li>
     *   <li>时间窗口过长会导致误判，过短可能无法有效防护</li>
     * </ul>
     *
     * <p><b>示例URL：</b>
     * <pre>
     * GET /api/audit-logs/users/1001/login-failures?tenantId=default&startTime=2026-09-25T10:00:00
     * </pre>
     *
     * @param request HTTP请求
     * @return HTTP响应
     */
    @OperationLog(module = "审计日志", type = OperationType.SELECT, description = "统计登录失败次数")
    @Operation(operationId = "countLoginFailures", summary = "统计登录失败次数", description = "统计用户在指定时间后的登录失败次数")
    @io.swagger.v3.oas.annotations.Parameter(name = "userId", description = "用户ID", required = true, example = "1001")
    @io.swagger.v3.oas.annotations.Parameter(
            name = "tenantId",
            description = "租户ID",
            required = true,
            example = "default")
    @io.swagger.v3.oas.annotations.Parameter(
            name = "startTime",
            description = "开始时间（ISO-8601格式）",
            required = true,
            example = "2026-09-25T10:00:00")
    @ApiResponse(responseCode = "200", description = "统计成功")
    @ApiResponse(
            responseCode = "400",
            description = "参数错误",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> countLoginFailures(ServerRequest request) {
        Long userId = Long.valueOf(request.pathVariable("userId"));
        String tenantId =
                request.queryParam("tenantId").orElseThrow(() -> new IllegalArgumentException("tenantId is required"));
        String startTimeStr = request.queryParam("startTime")
                .orElseThrow(() -> new IllegalArgumentException("startTime is required"));
        LocalDateTime startTime = LocalDateTime.parse(startTimeStr);

        log.debug(
                "Counting login failures: userId={}, startTime={}, tenantId={}",
                userId,
                startTime,
                LogSafetyUtil.text(tenantId, 64));

        return auditLogService
                .countLoginFailures(userId, startTime, tenantId)
                .flatMap(count -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(count)))
                .onErrorResume(e -> {
                    log.error(
                            "Failed to count login failures: userId={}, tenantId={}",
                            userId,
                            LogSafetyUtil.text(tenantId, 64),
                            e);
                    return buildError(ResultCode.ERROR, TraceIdUtil.getTraceId());
                });
    }

    /**
     * 清理过期日志
     *
     * <p>清理指定保留天数之前的审计日志。
     *
     * <p><b>路径参数：</b>
     * <ul>
     *   <li><b>retentionDays</b>：日志保留天数（必填，正整数）</li>
     * </ul>
     *
     * <p><b>查询参数：</b>
     * <ul>
     *   <li><b>tenantId</b>：租户ID（必填）</li>
     * </ul>
     *
     * <p><b>返回数据：</b>
     * <p>返回清理的日志数量（Long类型）。
     *
     * <p><b>保留策略建议：</b>
     * <ul>
     *   <li><b>普通日志</b>：保留90天（满足大多数合规要求）</li>
     *   <li><b>安全日志</b>：保留180天（等保2.0要求）</li>
     *   <li><b>高风险日志</b>：保留365天或更长（重要证据保留）</li>
     *   <li><b>合规审计</b>：根据行业要求，金融行业通常要求5-7年</li>
     * </ul>
     *
     * <p><b>清理执行：</b>
     * <ul>
     *   <li>批量删除过期日志，减少数据库压力</li>
     *   <li>建议在系统低峰期（如凌晨）执行</li>
     *   <li>可配置定时任务自动清理</li>
     *   <li>清理前可先归档到冷存储（如对象存储）</li>
     * </ul>
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>存储优化 - 定期清理过期日志，释放存储空间</li>
     *   <li>性能优化 - 减少日志表数据量，提升查询性能</li>
     *   <li>合规管理 - 按照数据保留策略清理数据</li>
     *   <li>成本控制 - 降低数据库和存储成本</li>
     * </ul>
     *
     * <p><b>安全警告：</b>
     * <ul>
     *   <li>此操作不可逆，删除前请确认已完成归档</li>
     *   <li>建议先使用查询接口统计要删除的日志数量</li>
     *   <li>重要日志（如安全事件）建议单独归档后再清理</li>
     *   <li>清理操作本身也会记录审计日志</li>
     * </ul>
     *
     * <p><b>示例URL：</b>
     * <pre>
     * DELETE /api/audit-logs/cleanup/90?tenantId=default
     * </pre>
     *
     * @param request HTTP请求
     * @return HTTP响应
     */
    @OperationLog(module = "审计日志", type = OperationType.DELETE, description = "清理过期日志")
    @RequireAdminRole
    @RequireResourcePermission(
            resourceType = "audit-logs",
            resourceIdExpression = "#request.queryParam('tenantId').orElse('')",
            permission = "MANAGE",
            errorMessage = "无权清理该租户审计日志")
    @Operation(operationId = "cleanupExpiredLogs", summary = "清理过期日志", description = "清理指定保留天数之前的审计日志")
    @io.swagger.v3.oas.annotations.Parameter(
            name = "retentionDays",
            description = "日志保留天数（大于此天数的日志将被清理）",
            required = true,
            example = "90")
    @io.swagger.v3.oas.annotations.Parameter(
            name = "tenantId",
            description = "租户ID",
            required = true,
            example = "default")
    @ApiResponse(responseCode = "200", description = "清理成功，返回清理的日志数量")
    @ApiResponse(
            responseCode = "400",
            description = "参数错误或保留天数无效",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @ApiResponse(
            responseCode = "403",
            description = "权限不足，需要管理员权限",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> cleanupExpiredLogs(ServerRequest request) {
        String beforeTimeStr = request.queryParam("beforeTime")
                .orElseThrow(() -> new IllegalArgumentException("beforeTime is required"));
        LocalDateTime beforeTime = LocalDateTime.parse(beforeTimeStr);

        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> securityContext.getAuthentication().getDetails())
                .ofType(String.class)
                .switchIfEmpty(Mono.defer(
                        () -> Mono.justOrEmpty(TenantContext.currentTenantId().orElse(null))))
                .switchIfEmpty(Mono.error(new IllegalStateException("tenant context is required")))
                .flatMap(tenantId -> {
                    log.info(
                            "Cleaning up expired logs: beforeTime={}, tenantId={}",
                            beforeTime,
                            LogSafetyUtil.text(tenantId, 64));
                    return auditLogService
                            .cleanupExpiredLogs(beforeTime, tenantId)
                            .flatMap(count -> ServerResponse.ok()
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .bodyValue(Result.success(count)))
                            .onErrorResume(e -> {
                                log.error(
                                        "Failed to cleanup expired logs: beforeTime={}, tenantId={}",
                                        beforeTime,
                                        LogSafetyUtil.text(tenantId, 64),
                                        e);
                                return buildError(ResultCode.ERROR, TraceIdUtil.getTraceId());
                            });
                });
    }

    /**
     * 构建错误响应
     *
     * @param code    错误码
     * @param traceId 追踪ID
     * @return HTTP响应
     */
    private Mono<ServerResponse> buildError(ResultCode code, String traceId) {
        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(Result.error(code, traceId));
    }
}
