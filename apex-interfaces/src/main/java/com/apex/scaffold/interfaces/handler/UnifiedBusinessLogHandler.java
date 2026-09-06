package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.service.UnifiedBusinessLogService;
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
 * 业务日志统一查询 Handler
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
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
