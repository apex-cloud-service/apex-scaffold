package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.dto.admin.SsoAuditLogDTO;
import com.apex.scaffold.application.service.SysSsoAuditLogService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.TraceIdUtil;
import com.apex.scaffold.interfaces.util.MessageUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Validator;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * SSO 审计日志处理器(函数式端点)
 *
 * <p>提供 SSO 单点登录审计日志的查询和统计 API,用于记录和追踪 SSO 相关的所有操作行为。
 * 支持按用户、提供商、操作类型、操作结果、时间范围等维度查询审计日志,满足安全审计和合规要求。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>日志查询:分页查询 SSO 审计日志,支持多维度筛选和时间范围过滤</li>
 *   <li>日志统计:获取审计日志统计信息,包括操作类型分布、成功/失败比例、按提供商分组等</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <p>记录的操作类型包括:</p>
 * <ul>
 *   <li>LOGIN - 用户通过 SSO 登录</li>
 *   <li>LOGOUT - 用户登出</li>
 *   <li>REVOKE - 会话被撤销</li>
 *   <li>TEST_CONNECTION - SSO 提供商连接测试</li>
 *   <li>CONFIG_UPDATE - SSO 配置更新</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>GET /admin-api/v1/sso/audit-logs - 查询审计日志列表(分页)</li>
 *   <li>GET /admin-api/v1/sso/audit-logs/statistics - 获取审计日志统计信息</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Component
@Tag(name = "后台-SSO 审计日志", description = "SSO 单点登录审计日志查询")
public class SsoAuditLogHandler extends BaseHandler {

    private final SysSsoAuditLogService sysSsoAuditLogService;

    /**
     * 创建 SSO 审计日志处理器。
     *
     * @param sysSsoAuditLogService SSO 审计日志服务
     * @param messageUtils 国际化消息工具
     * @param validator Jakarta Bean Validation 校验器
     */
    public SsoAuditLogHandler(
            SysSsoAuditLogService sysSsoAuditLogService, MessageUtils messageUtils, Validator validator) {
        super(messageUtils, validator);
        this.sysSsoAuditLogService = sysSsoAuditLogService;
    }

    /**
     * 查询 SSO 审计日志列表
     *
     * <p>分页查询当前租户下的 SSO 审计日志，支持按用户名、提供商、操作类型、操作结果筛选。</p>
     *
     * @param request 服务器请求对象，包含分页和筛选查询参数
     * @return 包含 SSO 审计日志分页列表的响应对象
     */
    @RequirePermission("system:sso:auditlog:list")
    @Operation(
            operationId = "adminListSsoAuditLogs",
            summary = "查询 SSO 审计日志列表",
            description = "分页查询当前租户下的 SSO 审计日志,支持按用户名、提供商、操作类型、操作结果筛选")
    @Parameter(
            name = "page",
            description = "页码(从1开始)",
            in = ParameterIn.QUERY,
            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "integer", example = "1"))
    @Parameter(
            name = "size",
            description = "每页数量",
            in = ParameterIn.QUERY,
            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "integer", example = "20"))
    @Parameter(
            name = "userId",
            description = "用户 ID",
            in = ParameterIn.QUERY,
            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "integer", format = "int64"))
    @Parameter(
            name = "providerId",
            description = "SSO 提供商 ID",
            in = ParameterIn.QUERY,
            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "integer", format = "int64"))
    @Parameter(
            name = "operationType",
            description = "操作类型(LOGIN/LOGOUT/REVOKE/TEST_CONNECTION/CONFIG_UPDATE)",
            in = ParameterIn.QUERY,
            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string"))
    @Parameter(
            name = "startTime",
            description = "开始时间(ISO 8601 格式)",
            in = ParameterIn.QUERY,
            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", format = "date-time"))
    @Parameter(
            name = "endTime",
            description = "结束时间(ISO 8601 格式)",
            in = ParameterIn.QUERY,
            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", format = "date-time"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功,data 为 SSO 审计日志列表",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema =
                                        @io.swagger.v3.oas.annotations.media.Schema(
                                                implementation = SsoAuditLogDTO.class))),
        @ApiResponse(responseCode = "401", description = "未授权,需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足,需要 system:sso:auditlog:list 权限")
    })
    @OperationLog(
            module = "SsoAuditLog",
            type = OperationLog.OperationType.SELECT,
            description = "list",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> list(ServerRequest request) {
        int page = safeParseInt(request.queryParam("page").orElse("1"), 1);
        int size = safeParseInt(request.queryParam("size").orElse("20"), 20);
        Long userId = request.queryParam("userId").map(Long::parseLong).orElse(null);
        Long providerId = request.queryParam("providerId").map(Long::parseLong).orElse(null);
        String action = request.queryParam("operationType").orElse(null);
        LocalDateTime startTime = request.queryParam("startTime")
                .map(s -> LocalDateTime.parse(s, DateTimeFormatter.ISO_DATE_TIME))
                .orElse(null);
        LocalDateTime endTime = request.queryParam("endTime")
                .map(s -> LocalDateTime.parse(s, DateTimeFormatter.ISO_DATE_TIME))
                .orElse(null);

        return sysSsoAuditLogService
                .list(providerId, userId, action, startTime, endTime, page, size)
                .flatMap(this::buildSuccess);
    }

    /**
     * 获取 SSO 审计日志统计信息
     *
     * <p>获取当前租户下的 SSO 审计日志统计信息，包括操作类型分布、成功/失败比例、按提供商分组等。</p>
     *
     * @param request 服务器请求对象，包含统计范围查询参数
     * @return 包含 SSO 审计日志统计信息的响应对象
     */
    @RequirePermission("system:sso:auditlog:stat")
    @Operation(
            operationId = "adminGetSsoAuditLogStatistics",
            summary = "获取 SSO 审计日志统计信息",
            description = "获取当前租户下的 SSO 审计日志统计信息,包括操作类型分布、成功/失败比例、按提供商分组等")
    @Parameter(
            name = "providerId",
            description = "SSO 提供商 ID(必填)",
            required = true,
            in = ParameterIn.QUERY,
            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "integer", format = "int64"))
    @Parameter(
            name = "days",
            description = "统计最近天数",
            in = ParameterIn.QUERY,
            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "integer", defaultValue = "30"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功,data 含 providerId、days、totalCount、successCount、failureCount、"
                        + "successRate、actionCounts(按操作类型分组计数)、startTime、endTime"),
        @ApiResponse(responseCode = "400", description = "providerId 缺失或非法"),
        @ApiResponse(responseCode = "401", description = "未授权,需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足,需要 system:sso:auditlog:stat 权限")
    })
    @OperationLog(
            module = "SsoAuditLog",
            type = OperationLog.OperationType.SELECT,
            description = "getStatistics",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> getStatistics(ServerRequest request) {
        Long providerId = request.queryParam("providerId").map(Long::parseLong).orElse(null);
        int days = safeParseInt(request.queryParam("days").orElse("30"), 30);

        if (providerId == null) {
            return buildError(ResultCode.PARAM_INVALID, TraceIdUtil.getTraceId());
        }

        return sysSsoAuditLogService.getStatisticsByProvider(providerId, days).flatMap(this::buildSuccess);
    }
}
