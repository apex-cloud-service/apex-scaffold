package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.capability.CapabilityCatalog;
import com.apex.scaffold.application.service.TenantPackageService;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 能力目录与租户订阅 Handler（方案 M4 / 批次 D4）。
 *
 * <p>端点：
 * <ul>
 *   <li>{@code GET /api/v2/capabilities}——能力目录（平台级，供管理端展示）</li>
 *   <li>{@code GET /api/v2/capabilities/subscribed}——当前租户已订阅能力</li>
 * </ul>
 *
 * <p>订阅判定复用套餐 {@code features}（B3）：{@code unbounded=true} 表示订阅全部能力；
 * {@code unbounded=false} 时按编码精确匹配，{@code features} 为空表示未订阅任何能力
 * （不以空集合兜底为全量，避免配置缺失被静默放大为全开）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CapabilityHandler {

    private final TenantPackageService tenantPackageService;

    /**
     * 能力目录（全部已登记能力）。
     *
     * @param request 当前请求
     * @return 能力列表
     */
    @Operation(
            operationId = "listCapabilities",
            summary = "查询能力目录",
            description = "返回平台已登记的全部能力（展示顺序 = 登记顺序）；需 tenant:read 权限")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Result 封装的能力列表",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = CapabilityCatalog.Capability.class))),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 tenant:read 权限")
    })
    @RequirePermission("tenant:read")
    public Mono<ServerResponse> listCapabilities(ServerRequest request) {
        return ServerResponse.ok().bodyValue(Result.success(CapabilityCatalog.all()));
    }

    /**
     * 当前租户已订阅能力。
     *
     * @param request 当前请求
     * @return 已订阅能力列表
     */
    @Operation(
            operationId = "listSubscribedCapabilities",
            summary = "查询当前租户已订阅能力",
            description = "按当前可信租户上下文过滤能力目录：复用套餐 features 判定，" + "unbounded=true 表示全量订阅，features 为空表示未订阅任何能力")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Result 封装的已订阅能力列表",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = CapabilityCatalog.Capability.class))),
        @ApiResponse(responseCode = "500", description = "缺少可信租户上下文或查询失败")
    })
    public Mono<ServerResponse> listSubscribed(ServerRequest request) {
        return Mono.deferContextual(context -> {
                    String tenant = context.getOrDefault(
                            com.apex.scaffold.common.context.TenantContext.REACTOR_CONTEXT_KEY, null);
                    if (tenant == null || tenant.isBlank()) {
                        return Mono.error(new IllegalStateException("缺少可信租户上下文"));
                    }
                    return tenantPackageService.resolveScope(tenant).map(scope -> CapabilityCatalog.all().stream()
                            .filter(capability -> scope.allowsCapability(capability.code()))
                            .toList());
                })
                .flatMap(capabilities -> ServerResponse.ok().bodyValue(Result.success(capabilities)))
                .onErrorResume(error -> {
                    log.error("能力订阅查询失败", error);
                    return ServerResponse.status(500).bodyValue(Result.error(ResultCode.ERROR.getMsg()));
                });
    }
}
