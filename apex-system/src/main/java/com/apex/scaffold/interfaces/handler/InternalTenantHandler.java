package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.service.TenantApplicationService;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 租户状态内部查询 Handler（统一认证收口 A6）。
 *
 * <p>承载 {@code GET /internal/v1/tenants/{code}/status}：供认证中心登录前置校验
 * 消费，返回租户存在性、状态机、过期与配额摘要。集群内服务直连，不经网关；
 * 未知租户以 {@code exists=false} 数据化返回（HTTP 200），便于消费方缓存负结果。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InternalTenantHandler {

    private final TenantApplicationService tenantApplicationService;

    /**
     * 查询租户认证期状态。
     *
     * @param request 当前请求（路径参数 {@code code}）
     * @return 状态快照（{@code Result<TenantApplicationService.TenantStatusSnapshot>}）
     */
    public Mono<ServerResponse> getStatus(ServerRequest request) {
        String code = request.pathVariable("code");
        if (code == null || code.isBlank() || code.length() > 64) {
            return ServerResponse.badRequest().bodyValue(Result.error(ResultCode.PARAM_ERROR.getMsg()));
        }
        return tenantApplicationService
                .getTenantStatusByCode(code)
                .flatMap(snapshot -> ServerResponse.ok().bodyValue(Result.success(snapshot)))
                .onErrorResume(error -> {
                    log.error("租户状态内部查询失败: code={}", code, error);
                    return ServerResponse.status(500).bodyValue(Result.error(ResultCode.ERROR.getMsg()));
                });
    }
}
