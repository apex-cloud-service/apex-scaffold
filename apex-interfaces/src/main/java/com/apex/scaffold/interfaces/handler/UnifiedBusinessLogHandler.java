package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.service.UnifiedBusinessLogService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 业务日志统一查询 Handler
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Tag(name = "业务日志", description = "业务日志统一查询接口")
public class UnifiedBusinessLogHandler {

    private final UnifiedBusinessLogService unifiedBusinessLogService;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 统一查询业务日志
     *
     * @param request HTTP请求
     * @return 日志分页数据
     */
    @RequirePermission("system:log:business:query")
    @OperationLog(module = "业务日志", type = OperationLog.OperationType.SELECT, description = "查询业务日志")
    @Operation(
            operationId = "queryUnifiedBusinessLogs",
            summary = "统一查询业务日志",
            description = "统一查询登录日志、操作日志和API调用日志，支持按日志类型、时间范围、用户名和模块名过滤，并提供分页功能")
    @io.swagger.v3.oas.annotations.Parameters({
        @Parameter(
                name = "logType",
                description = "日志类型（可选，login-登录日志/operation-操作日志/api-API调用日志，不传则查询全部）",
                required = false,
                in = ParameterIn.QUERY,
                schema = @Schema(type = "string")),
        @Parameter(
                name = "startTime",
                description = "开始时间（格式：yyyy-MM-dd HH:mm:ss，默认为7天前）",
                required = false,
                in = ParameterIn.QUERY,
                schema = @Schema(type = "string", example = "2024-01-01 00:00:00")),
        @Parameter(
                name = "endTime",
                description = "结束时间（格式：yyyy-MM-dd HH:mm:ss，默认为当前时间）",
                required = false,
                in = ParameterIn.QUERY,
                schema = @Schema(type = "string", example = "2024-01-31 23:59:59")),
        @Parameter(
                name = "username",
                description = "用户名（可选，模糊匹配）",
                required = false,
                in = ParameterIn.QUERY,
                schema = @Schema(type = "string")),
        @Parameter(
                name = "module",
                description = "模块名（可选，模糊匹配）",
                required = false,
                in = ParameterIn.QUERY,
                schema = @Schema(type = "string")),
        @Parameter(
                name = "page",
                description = "页码，从1开始",
                required = false,
                in = ParameterIn.QUERY,
                schema = @Schema(type = "integer", defaultValue = "1")),
        @Parameter(
                name = "size",
                description = "每页大小（最大100）",
                required = false,
                in = ParameterIn.QUERY,
                schema = @Schema(type = "integer", defaultValue = "20"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = PageResponse.class))),
        @ApiResponse(responseCode = "400", description = "参数错误，例如时间格式不正确"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 system:log:business:query 权限")
    })
    public Mono<ServerResponse> queryLogs(ServerRequest request) {
        try {
            String logType = request.queryParam("logType").orElse(null);
            String startTimeStr = request.queryParam("startTime").orElse(null);
            String endTimeStr = request.queryParam("endTime").orElse(null);
            String username = request.queryParam("username").orElse(null);
            String module = request.queryParam("module").orElse(null);
            int pageNum = parseIntParam(request, "page", 1);
            int pageSize = parseIntParam(request, "size", 20);

            if (pageSize > 100) {
                pageSize = 100;
            }

            LocalDateTime startTime = startTimeStr != null
                    ? LocalDateTime.parse(startTimeStr, FORMATTER)
                    : LocalDateTime.now().minusDays(7);

            LocalDateTime endTime =
                    endTimeStr != null ? LocalDateTime.parse(endTimeStr, FORMATTER) : LocalDateTime.now();

            log.info(
                    "Query unified business logs: type={}, start={}, end={}, username={}, module={}, page={}, size={}",
                    logType,
                    startTime,
                    endTime,
                    username,
                    module,
                    pageNum,
                    pageSize);

            return unifiedBusinessLogService
                    .queryLogs(logType, startTime, endTime, username, module, pageNum, pageSize)
                    .flatMap(result -> ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Result.success(result)))
                    .onErrorResume(e -> {
                        log.error("Query unified business logs failed", e);
                        return ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Result.error("查询失败：" + e.getMessage()));
                    });
        } catch (Exception e) {
            log.error("Parse query parameters failed", e);
            return ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Result.error("参数错误：" + e.getMessage()));
        }
    }

    /**
     * 统计业务日志数量
     *
     * @param request HTTP请求
     * @return 日志数量
     */
    @RequirePermission("system:log:business:query")
    @Operation(
            operationId = "countUnifiedBusinessLogs",
            summary = "统计业务日志数量",
            description = "统计指定条件下的业务日志总数，支持按日志类型和时间范围过滤")
    @io.swagger.v3.oas.annotations.Parameters({
        @Parameter(
                name = "logType",
                description = "日志类型（可选，login-登录日志/operation-操作日志/api-API调用日志，不传则统计全部）",
                required = false,
                in = ParameterIn.QUERY,
                schema = @Schema(type = "string")),
        @Parameter(
                name = "startTime",
                description = "开始时间（格式：yyyy-MM-dd HH:mm:ss，默认为7天前）",
                required = false,
                in = ParameterIn.QUERY,
                schema = @Schema(type = "string", example = "2024-01-01 00:00:00")),
        @Parameter(
                name = "endTime",
                description = "结束时间（格式：yyyy-MM-dd HH:mm:ss，默认为当前时间）",
                required = false,
                in = ParameterIn.QUERY,
                schema = @Schema(type = "string", example = "2024-01-31 23:59:59"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "统计成功",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Long.class))),
        @ApiResponse(responseCode = "400", description = "参数错误，例如时间格式不正确"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 system:log:business:query 权限")
    })
    public Mono<ServerResponse> countLogs(ServerRequest request) {
        try {
            String logType = request.queryParam("logType").orElse(null);
            String startTimeStr = request.queryParam("startTime").orElse(null);
            String endTimeStr = request.queryParam("endTime").orElse(null);

            LocalDateTime startTime = startTimeStr != null
                    ? LocalDateTime.parse(startTimeStr, FORMATTER)
                    : LocalDateTime.now().minusDays(7);

            LocalDateTime endTime =
                    endTimeStr != null ? LocalDateTime.parse(endTimeStr, FORMATTER) : LocalDateTime.now();

            return unifiedBusinessLogService
                    .countLogs(logType, startTime, endTime)
                    .flatMap(count -> ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Result.success(count)))
                    .onErrorResume(e -> {
                        log.error("Count unified business logs failed", e);
                        return ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Result.error("统计失败：" + e.getMessage()));
                    });
        } catch (Exception e) {
            log.error("Parse query parameters failed", e);
            return ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Result.error("参数错误：" + e.getMessage()));
        }
    }

    private int parseIntParam(ServerRequest request, String paramName, int defaultValue) {
        return request.queryParam(paramName)
                .map(value -> {
                    try {
                        return Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        return defaultValue;
                    }
                })
                .orElse(defaultValue);
    }
}
