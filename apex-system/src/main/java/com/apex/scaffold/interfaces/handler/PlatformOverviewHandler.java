package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.dto.internal.OverviewResponse;
import com.apex.scaffold.application.service.OverviewFacadeService;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 业务端跨域只读聚合 Handler（方案 D5 对外口径）。
 *
 * <p>承载 {@code GET /api/v2/overview}：与管理端的内部端点
 * {@code /internal/v1/overview} 共用 {@link OverviewFacadeService}，差异在于
 * <ul>
 *   <li>租户取自可信租户上下文（不接受查询参数覆盖，避免跨租户读取）；</li>
 *   <li>经网关暴露给管理后台，受 {@code system:monitor:view} 权限码控制；</li>
 *   <li>数据源自身不可达时按 DEGRADED 返回部分结果，不整体失败。</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.2
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformOverviewHandler {

    private final OverviewFacadeService overviewFacadeService;

    /**
     * 查询当前租户的跨域概览聚合。
     *
     * @param request 当前请求（无参数；租户来自可信上下文）
     * @return 聚合响应（{@code Result<OverviewResponse>}）
     */
    @Operation(
            operationId = "platformOverview",
            summary = "查询当前租户跨域概览",
            description =
                    "按当前可信租户上下文聚合文件/工作流/集成统计；不接受查询参数覆盖租户，" + "任一数据源不可达时该源按 DEGRADED 降级（部分可用）；需 system:monitor:view 权限")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Result 封装的聚合响应（降级源以 DEGRADED 呈现）",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = OverviewResponse.class))),
        @ApiResponse(responseCode = "500", description = "缺少可信租户上下文或聚合失败"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 system:monitor:view 权限")
    })
    @RequirePermission("system:monitor:view")
    public Mono<ServerResponse> overview(ServerRequest request) {
        return Mono.deferContextual(context -> {
                    String tenantId = context.getOrDefault(TenantContext.REACTOR_CONTEXT_KEY, null);
                    if (tenantId == null || tenantId.isBlank()) {
                        return Mono.error(new IllegalStateException("缺少可信租户上下文"));
                    }
                    String authorization = request.headers().firstHeader(HttpHeaders.AUTHORIZATION);
                    return overviewFacadeService.getOverview(tenantId, authorization);
                })
                .flatMap(response -> ServerResponse.ok().bodyValue(Result.success(response)))
                .onErrorResume(error -> {
                    log.error("业务端聚合概览查询失败", error);
                    return ServerResponse.status(500).bodyValue(Result.error(ResultCode.ERROR.getMsg()));
                });
    }
}
