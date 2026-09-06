package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.service.RuntimeLogService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.result.Result;
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
 * 运行时日志查询 Handler
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuntimeLogHandler {

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
    public Mono<ServerResponse> queryLogs(ServerRequest request) {
        try {
            String startTimeStr = request.queryParam("startTime").orElse(null);
            String endTimeStr = request.queryParam("endTime").orElse(null);
            String level = request.queryParam("level").orElse(null);
            String logger = request.queryParam("logger").orElse(null);
            String message = request.queryParam("message").orElse(null);
            int limit = parseIntParam(request, "limit", 100);

            if (limit > 1000) {
                limit = 1000;
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
