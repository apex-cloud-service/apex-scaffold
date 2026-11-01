package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.capability.CapabilityCatalog;
import com.apex.scaffold.application.service.TenantPackageService;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
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
 * <p>订阅判定复用套餐 {@code features}（B3）：{@code features} 为空表示全量订阅
 * （与套餐"空集合=全量"语义一致），否则按编码精确匹配。
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
