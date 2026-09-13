package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.dto.admin.AuditLogDTO;
import com.apex.scaffold.application.service.AuditLogQueryService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.interfaces.util.MessageUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 操作审计日志处理器（函数式端点）。
 *
 * <p>提供操作审计日志（{@code sys_operation_log} 表）的分页查询 API，
 * 支撑管理后台「系统监控-审计日志」页面（C2 审计落库闭环）。
 * 审计写入由 {@code OperationLogAspect} 切面 + {@code OperationLogService}
 * 在业务方法执行后自动完成（写读同源），本 Handler 仅负责查询展示。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>审计日志分页查询：支持按操作模块、操作人模糊匹配过滤</li>
 * </ul>
 *
 * <h2>接口列表</h2>
 * <ul>
 *   <li>GET /admin-api/v1/audit-logs — 分页查询审计日志</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see AuditLogQueryService
 */
@Component
@Tag(name = "后台-审计日志", description = "操作审计日志查询")
public class AuditLogHandler extends BaseHandler {

    /** 审计日志服务 */
    private final AuditLogQueryService auditLogQueryService;

    /**
     * 创建审计日志处理器。
     *
     * @param auditLogService 审计日志服务
     * @param messageUtils 国际化消息工具
     * @param validator Jakarta Bean Validation 校验器
     */
    public AuditLogHandler(AuditLogQueryService auditLogQueryService, MessageUtils messageUtils, Validator validator) {
        super(messageUtils, validator);
        this.auditLogQueryService = auditLogQueryService;
    }

    /**
     * 分页查询审计日志。
     *
     * <p>查询参数：{@code page}（页码，默认 1）、{@code size}（每页大小，默认 20）、
     * {@code module}（操作模块，模糊）、{@code operatorName}（操作人，模糊）。</p>
     *
     * @param request HTTP 请求
     * @return 带 traceId 的统一成功响应，data 为 {@code PageResponse<AuditLogPO>}
     */
    @RequirePermission("system:audit-log:list")
    @Operation(
            operationId = "adminListAuditLogs",
            summary = "分页查询审计日志",
            description = "提供操作审计日志的分页查询，支持按操作模块、操作人等条件模糊匹配过滤，支撑C2审计落库闭环与安全审计")
    @Parameters({
        @Parameter(
                name = "page",
                description = "页码，从1开始",
                required = false,
                in = ParameterIn.QUERY,
                schema = @Schema(type = "integer", defaultValue = "1")),
        @Parameter(
                name = "size",
                description = "每页大小",
                required = false,
                in = ParameterIn.QUERY,
                schema = @Schema(type = "integer", defaultValue = "20")),
        @Parameter(
                name = "module",
                description = "操作模块（模糊匹配）",
                required = false,
                in = ParameterIn.QUERY,
                schema = @Schema(type = "string")),
        @Parameter(
                name = "operatorName",
                description = "操作人姓名（模糊匹配）",
                required = false,
                in = ParameterIn.QUERY,
                schema = @Schema(type = "string"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content =
                        @Content(mediaType = "application/json", schema = @Schema(implementation = AuditLogDTO.class))),
        @ApiResponse(responseCode = "400", description = "参数错误"),
        @ApiResponse(responseCode = "401", description = "未授权，需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 system:audit-log:list 权限")
    })
    @OperationLog(module = "审计日志", type = OperationLog.OperationType.SELECT, description = "分页查询审计日志")
    public Mono<ServerResponse> list(ServerRequest request) {
        int page = safeParseInt(request.queryParam("page").orElse(null), 1);
        int size = safeParseInt(request.queryParam("size").orElse(null), 20);
        String module = request.queryParam("module").filter(v -> !v.isBlank()).orElse(null);
        String operatorName =
                request.queryParam("operatorName").filter(v -> !v.isBlank()).orElse(null);
        return auditLogQueryService.search(module, operatorName, page, size).flatMap(this::buildSuccess);
    }
}
