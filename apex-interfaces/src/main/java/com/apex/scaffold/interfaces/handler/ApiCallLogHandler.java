package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.dto.admin.ApiCallLogDTO;
import com.apex.scaffold.application.service.ApiCallLogQueryService;
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
 * API 调用日志处理器（函数式端点）。
 *
 * <p>提供对外部 HTTP 服务调用日志（{@code sys_api_call_log} 表）的分页查询 API，
 * 支撑管理后台「监控中心-API调用日志」页面（第三方接口联调与排障）。
 * 日志写入由 {@code ApiCallLogFilter} + {@code ApiCallLogService} 自动完成
 * （写读同源，请求/响应体与敏感头已脱敏），本 Handler 仅负责查询展示。</p>
 *
 * <h2>接口清单</h2>
 * <ul>
 *   <li>GET /admin-api/v1/api-call-logs - 分页查询 API 调用日志
 *       （支持 serviceName / status / traceId / path 过滤）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.3.0
 * @see ApiCallLogQueryService
 */
@Component
@Tag(name = "后台-API调用日志", description = "对外HTTP调用日志回溯查询")
public class ApiCallLogHandler extends BaseHandler {

    /** API 调用日志查询服务。 */
    private final ApiCallLogQueryService apiCallLogQueryService;

    /**
     * 创建 API 调用日志处理器。
     *
     * @param apiCallLogQueryService API 调用日志查询服务
     * @param messageUtils 国际化消息工具
     * @param validator Jakarta Bean Validation 校验器
     */
    public ApiCallLogHandler(
            ApiCallLogQueryService apiCallLogQueryService, MessageUtils messageUtils, Validator validator) {
        super(messageUtils, validator);
        this.apiCallLogQueryService = apiCallLogQueryService;
    }

    /**
     * 分页查询 API 调用日志。
     *
     * <p>查询参数：{@code page}（页码，默认 1）、{@code size}（每页大小，默认 20）、
     * {@code serviceName}（目标服务，精确）、{@code status}（调用状态，精确）、
     * {@code traceId}（链路ID，模糊）、{@code path}（请求路径，模糊）。</p>
     *
     * @param request HTTP 请求
     * @return 带 traceId 的统一成功响应，data 为 {@code PageResponse<ApiCallLogDTO>}
     */
    @RequirePermission("system:api-call-log:list")
    @Operation(
            operationId = "adminListApiCallLogs",
            summary = "分页查询API调用日志",
            description = "提供对外部HTTP服务调用日志的分页查询，支持按服务名、状态、链路ID、路径等条件过滤，支撑第三方接口联调与排障"
    )
    @Parameters({
            @Parameter(
                    name = "page",
                    description = "页码，从1开始",
                    required = false,
                    in = ParameterIn.QUERY,
                    schema = @Schema(type = "integer", defaultValue = "1")
            ),
            @Parameter(
                    name = "size",
                    description = "每页大小",
                    required = false,
                    in = ParameterIn.QUERY,
                    schema = @Schema(type = "integer", defaultValue = "20")
            ),
            @Parameter(
                    name = "serviceName",
                    description = "目标服务名称（精确匹配）",
                    required = false,
                    in = ParameterIn.QUERY,
                    schema = @Schema(type = "string")
            ),
            @Parameter(
                    name = "status",
                    description = "调用状态（0-成功 1-客户端错误 2-服务端错误 3-网络异常）",
                    required = false,
                    in = ParameterIn.QUERY,
                    schema = @Schema(type = "integer")
            ),
            @Parameter(
                    name = "traceId",
                    description = "链路追踪ID（模糊匹配）",
                    required = false,
                    in = ParameterIn.QUERY,
                    schema = @Schema(type = "string")
            ),
            @Parameter(
                    name = "path",
                    description = "请求路径（模糊匹配）",
                    required = false,
                    in = ParameterIn.QUERY,
                    schema = @Schema(type = "string")
            )
    })
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "查询成功",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiCallLogDTO.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "参数错误"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "未授权，需要登录"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "权限不足，需要 system:api-call-log:list 权限"
            )
    })
    @OperationLog(module = "API调用日志", type = OperationLog.OperationType.SELECT, description = "分页查询API调用日志")
    public Mono<ServerResponse> list(ServerRequest request) {
        int page = safeParseInt(request.queryParam("page").orElse(null), 1);
        int size = safeParseInt(request.queryParam("size").orElse(null), 20);
        Integer status = request.queryParam("status")
                .filter(v -> !v.isBlank())
                .map(v -> safeParseInt(v, -1))
                .orElse(null);
        String serviceName =
                request.queryParam("serviceName").filter(v -> !v.isBlank()).orElse(null);
        String traceId = request.queryParam("traceId").filter(v -> !v.isBlank()).orElse(null);
        String path = request.queryParam("path").filter(v -> !v.isBlank()).orElse(null);
        return apiCallLogQueryService
                .search(serviceName, status, traceId, path, page, size)
                .flatMap(this::buildSuccess);
    }
}
