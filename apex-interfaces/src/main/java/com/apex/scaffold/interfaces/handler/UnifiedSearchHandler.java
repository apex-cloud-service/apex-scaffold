package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.service.IndexSyncService;
import com.apex.scaffold.application.service.SearchService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.result.Result;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 统一搜索 Handler
 *
 * <p>提供全文搜索和索引管理 API。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnifiedSearchHandler {

    private final SearchService searchService;
    private final IndexSyncService indexSyncService;

    /**
     * 统一搜索
     *
     * @param request HTTP请求
     * @return 搜索结果
     */
    @RequirePermission("system:search:query")
    @OperationLog(module = "全文搜索", type = OperationLog.OperationType.SELECT, description = "执行全文搜索")
    public Mono<ServerResponse> search(ServerRequest request) {
        // 解析查询参数
        String keyword = request.queryParam("keyword").orElse("");
        String types = request.queryParam("types").orElse("");
        int page = parseIntParam(request, "page", 1);
        int size = parseIntParam(request, "size", 20);

        // 限制分页大小
        if (size > 100) {
            size = 100;
        }
        if (size < 1) {
            size = 20;
        }
        if (page < 1) {
            page = 1;
        }

        // 解析资源类型
        Set<String> resourceTypes = types.isEmpty()
                ? null
                : Arrays.stream(types.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toSet());

        log.info("Search request: keyword={}, types={}, page={}, size={}", keyword, resourceTypes, page, size);

        // 执行搜索
        return searchService
                .search(keyword, resourceTypes, page, size)
                .flatMap(result -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(result)))
                .onErrorResume(e -> {
                    log.error("Search failed", e);
                    return ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Result.error("搜索失败：" + e.getMessage()));
                });
    }

    /**
     * 重建索引
     *
     * @param request HTTP请求
     * @return 重建结果
     */
    @RequirePermission("system:search:rebuild")
    @OperationLog(module = "全文搜索", type = OperationLog.OperationType.UPDATE, description = "重建搜索索引")
    public Mono<ServerResponse> rebuildIndex(ServerRequest request) {
        String resourceType = request.queryParam("type").orElse(null);

        log.warn("Index rebuild request: type={}", resourceType);

        return indexSyncService
                .rebuildIndex(resourceType)
                .flatMap(count -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(count, "索引重建成功，共索引 " + count + " 条记录")))
                .onErrorResume(e -> {
                    log.error("Index rebuild failed", e);
                    return ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Result.error("索引重建失败：" + e.getMessage()));
                });
    }

    /**
     * 解析整数参数
     */
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
