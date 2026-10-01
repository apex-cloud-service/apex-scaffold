package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.dto.log.RuntimeLogDTO;
import com.apex.scaffold.application.service.RuntimeLogService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
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
 * 运行时日志查询处理器（函数式端点）。
 *
 * <p>提供系统运行时日志的查询与统计 API，用于运维人员在线排查问题与分析日志，
 * 支持按时间范围、日志级别、Logger 名称、消息关键词等多维度过滤查询，
 * 默认查询最近 1 小时的日志，单次最多返回 1000 条记录。</p>
 *
 * <h2>接口清单</h2>
 * <ul>
 *   <li>GET /admin-api/v1/runtime-logs - 查询运行时日志（需要 system:log:runtime:query 权限）</li>
 *   <li>GET /admin-api/v1/runtime-logs/count - 统计运行时日志数量（需要 system:log:runtime:query 权限）</li>
 * </ul>
 *
 * <h2>查询参数</h2>
 * <ul>
 *   <li>startTime：开始时间（格式：yyyy-MM-dd HH:mm:ss，默认为 1 小时前）</li>
 *   <li>endTime：结束时间（格式：yyyy-MM-dd HH:mm:ss，默认为当前时间）</li>
 *   <li>level：日志级别（可选，如 ERROR、WARN、INFO、DEBUG、TRACE）</li>
 *   <li>logger：日志名称（可选，Logger 类名，支持模糊匹配）</li>
 *   <li>message：消息关键词（可选，支持模糊匹配）</li>
 *   <li>limit：返回条数（默认 100，最大 1000）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 * @see RuntimeLogService
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Tag(name = "运行时日志", description = "运行时日志查询接口")
public class RuntimeLogHandler {

    private static final int MAX_LOG_LIMIT = 1000;

    private final RuntimeLogService runtimeLogService;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 查询运行时日志
     *
     * @param request HTTP请求
     * @return 日志列表
     */
    @RequirePermission("system:log:runtime:query")
    @OperationLog(module = "运行日志", type = OperationLog.OperationType.SELECT, description = "查询运行时日志")
    @Operation(
            operationId = "queryRuntimeLogs",
            summary = "查询运行时日志",
            description = "查询系统运行时日志，支持按时间范围、日志级别、日志名称和消息关键词过滤，默认查询最近1小时的日志")
    @io.swagger.v3.oas.annotations.Parameters({
        @Parameter(
                name = "startTime",
                description = "开始时间（格式：yyyy-MM-dd HH:mm:ss，默认为1小时前）",
                required = false,
                in = ParameterIn.QUERY,
                schema = @Schema(type = "string", example = "2024-01-01 00:00:00")),
        @Parameter(
                name = "endTime",
                description = "结束时间（格式：yyyy-MM-dd HH:mm:ss，默认为当前时间）",
                required = false,
                in = ParameterIn.QUERY,
                schema = @Schema(type = "string", example = "2024-01-01 23:59:59")),
        @Parameter(
                name = "level",
                description = "日志级别（可选，如 ERROR、WARN、INFO、DEBUG、TRACE）",
                required = false,
                in = ParameterIn.QUERY,
                schema = @Schema(type = "string")),
        @Parameter(
                name = "logger",
                description = "日志名称（可选，Logger类名，支持模糊匹配）",
                required = false,
                in = ParameterIn.QUERY,
                schema = @Schema(type = "string")),
        @Parameter(
                name = "message",
                description = "消息关键词（可选，支持模糊匹配）",
                required = false,
                in = ParameterIn.QUERY,
                schema = @Schema(type = "string")),
        @Parameter(
                name = "limit",
                description = "返回条数（默认100，最大1000）",
                required = false,
                in = ParameterIn.QUERY,
                schema = @Schema(type = "integer", defaultValue = "100"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = RuntimeLogDTO.class))),
        @ApiResponse(responseCode = "400", description = "参数错误，例如时间格式不正确"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 system:log:runtime:query 权限")
    })
    public Mono<ServerResponse> queryLogs(ServerRequest request) {
        try {
            String startTimeStr = request.queryParam("startTime").orElse(null);
            String endTimeStr = request.queryParam("endTime").orElse(null);
            String level = request.queryParam("level").orElse(null);
            String logger = request.queryParam("logger").orElse(null);
            String message = request.queryParam("message").orElse(null);
            int limit = parseIntParam(request, "limit", 100);

            if (limit > MAX_LOG_LIMIT) {
                limit = MAX_LOG_LIMIT;
            }

            LocalDateTime startTime = startTimeStr != null
                    ? LocalDateTime.parse(startTimeStr, FORMATTER)
                    : LocalDateTime.now().minusHours(1);

            LocalDateTime endTime =
                    endTimeStr != null ? LocalDateTime.parse(endTimeStr, FORMATTER) : LocalDateTime.now();

            log.info(
                    "Query runtime logs: start={}, end={}, level={}, logger={}, message={}, limit={}",
                    startTime,
                    endTime,
                    level,
                    logger,
                    message,
                    limit);

            return runtimeLogService
                    .queryLogs(startTime, endTime, level, logger, message, limit)
                    .flatMap(logs -> ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Result.success(logs)))
                    .onErrorResume(e -> {
                        log.error("Query runtime logs failed", e);
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
     * 统计日志数量
     *
     * @param request HTTP请求
     * @return 日志数量
     */
    @RequirePermission("system:log:runtime:query")
    @Operation(operationId = "countRuntimeLogs", summary = "统计运行时日志数量", description = "统计指定条件下的运行时日志总数，支持按时间范围和日志级别过滤")
    @io.swagger.v3.oas.annotations.Parameters({
        @Parameter(
                name = "startTime",
                description = "开始时间（格式：yyyy-MM-dd HH:mm:ss，默认为1小时前）",
                required = false,
                in = ParameterIn.QUERY,
                schema = @Schema(type = "string", example = "2024-01-01 00:00:00")),
        @Parameter(
                name = "endTime",
                description = "结束时间（格式：yyyy-MM-dd HH:mm:ss，默认为当前时间）",
                required = false,
                in = ParameterIn.QUERY,
                schema = @Schema(type = "string", example = "2024-01-01 23:59:59")),
        @Parameter(
                name = "level",
                description = "日志级别（可选，如 ERROR、WARN、INFO、DEBUG、TRACE）",
                required = false,
                in = ParameterIn.QUERY,
                schema = @Schema(type = "string"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "统计成功",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Long.class))),
        @ApiResponse(responseCode = "400", description = "参数错误，例如时间格式不正确"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 system:log:runtime:query 权限")
    })
    public Mono<ServerResponse> countLogs(ServerRequest request) {
        try {
            String startTimeStr = request.queryParam("startTime").orElse(null);
            String endTimeStr = request.queryParam("endTime").orElse(null);
            String level = request.queryParam("level").orElse(null);

            LocalDateTime startTime = startTimeStr != null
                    ? LocalDateTime.parse(startTimeStr, FORMATTER)
                    : LocalDateTime.now().minusHours(1);

            LocalDateTime endTime =
                    endTimeStr != null ? LocalDateTime.parse(endTimeStr, FORMATTER) : LocalDateTime.now();

            return runtimeLogService
                    .countLogs(startTime, endTime, level)
                    .flatMap(count -> ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Result.success(count)))
                    .onErrorResume(e -> {
                        log.error("Count logs failed", e);
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
