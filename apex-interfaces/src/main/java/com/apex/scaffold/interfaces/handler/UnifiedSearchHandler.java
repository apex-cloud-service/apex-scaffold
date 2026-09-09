package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.service.IndexSyncService;
import com.apex.scaffold.application.service.SearchService;
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
@Tag(name = "全文搜索", description = "系统资源全文搜索与索引管理接口")
public class UnifiedSearchHandler {

    private final SearchService searchService;
    private final IndexSyncService indexSyncService;

    /**
     * 统一搜索。
     *
     * <p>根据关键词在指定资源类型中进行全文搜索，支持分页查询。</p>
     *
     * @param request ServerRequest，查询参数：keyword, types, page, size
     * @return 搜索结果分页列表
     */
    @Operation(
            operationId = "unifiedSearch",
            summary = "全文搜索",
            description = "根据关键词在指定资源类型中进行全文搜索，支持分页查询。可搜索的资源类型包括用户、角色、菜单、部门等系统资源")
    @Parameter(
            name = "keyword",
            description = "搜索关键词",
            required = true,
            in = ParameterIn.QUERY,
            schema = @Schema(type = "string"))
    @Parameter(
            name = "types",
            description = "资源类型，多个类型用逗号分隔（如：user,role,menu），不传则搜索所有类型",
            required = false,
            in = ParameterIn.QUERY,
            schema = @Schema(type = "string"))
    @Parameter(
            name = "page",
            description = "页码，从1开始",
            required = false,
            in = ParameterIn.QUERY,
            schema = @Schema(type = "integer", defaultValue = "1"))
    @Parameter(
            name = "size",
            description = "每页大小，最大100",
            required = false,
            in = ParameterIn.QUERY,
            schema = @Schema(type = "integer", defaultValue = "20"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "搜索成功",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class))),
        @ApiResponse(responseCode = "400", description = "参数错误"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要system:search:query权限"),
        @ApiResponse(responseCode = "500", description = "搜索失败，服务器内部错误")
    })
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
     * 重建搜索索引。
     *
     * <p>重建指定资源类型或所有资源的全文搜索索引。此操作会清空现有索引并重新建立，适用于索引损坏或数据不一致时。</p>
     *
     * @param request ServerRequest，查询参数：type
     * @return 重建结果，包含索引的记录数
     */
    @Operation(
            operationId = "rebuildSearchIndex",
            summary = "重建搜索索引",
            description = "重建指定资源类型或所有资源的全文搜索索引。此操作会清空现有索引并重新建立，适用于索引损坏或数据不一致时")
    @Parameter(
            name = "type",
            description = "资源类型（如：user, role, menu），不传则重建所有资源的索引",
            required = false,
            in = ParameterIn.QUERY,
            schema = @Schema(type = "string"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "重建成功",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class))),
        @ApiResponse(responseCode = "400", description = "参数错误，资源类型不存在"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要system:search:rebuild权限"),
        @ApiResponse(responseCode = "500", description = "索引重建失败，服务器内部错误")
    })
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
