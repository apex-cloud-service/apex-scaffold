package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.service.OverviewFacadeService;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 跨域只读聚合门面 Handler（方案 D5）。
 *
 * <p>承载 {@code GET /internal/v1/overview}：并行拉取文件/工作流/集成三个只读
 * 统计数据源并聚合。集群内服务直连，不经网关；租户范围由 {@code tenantId}
 * 查询参数显式声明（内部契约，与租户状态查询同口径），调用方的
 * {@code Authorization} 头原样透传给需要认证的数据源。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.2
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InternalOverviewHandler {

    private final OverviewFacadeService overviewFacadeService;

    /**
     * 查询租户维度的跨域概览聚合。
     *
     * @param request 当前请求（查询参数 {@code tenantId}）
     * @return 聚合响应（{@code Result<OverviewResponse>}）
     */
    public Mono<ServerResponse> getOverview(ServerRequest request) {
        String tenantId = request.queryParam("tenantId").map(String::trim).orElse(null);
        if (tenantId == null || tenantId.isBlank() || tenantId.length() > 64) {
            return ServerResponse.badRequest().bodyValue(Result.error(ResultCode.PARAM_ERROR.getMsg()));
        }
        String authorization = request.headers().firstHeader(HttpHeaders.AUTHORIZATION);
        return overviewFacadeService
                .getOverview(tenantId, authorization)
                .flatMap(response -> ServerResponse.ok().bodyValue(Result.success(response)))
                .onErrorResume(error -> {
                    log.error("聚合门面查询失败: tenantId={}", tenantId, error);
                    return ServerResponse.status(500).bodyValue(Result.error(ResultCode.ERROR.getMsg()));
                });
    }
}
