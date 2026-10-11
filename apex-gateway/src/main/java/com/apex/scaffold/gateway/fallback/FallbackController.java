package com.apex.scaffold.gateway.fallback;

import static org.springframework.web.reactive.function.server.RequestPredicates.path;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 网关熔断兜底路由注册.
 *
 * <p>将内部端点 {@code /__gateway/fallback} 绑定到 {@link FallbackHandler#fallback}，
 * 供各路由 {@code CircuitBreaker} 过滤器的 {@code fallbackUri} 使用。
 *
 * <p><b>安全提示</b>：该端点仅供网关内部转发使用，不对业务开放；
 * 需在网关路由谓词中确保该路径不被外部直接命中（不纳入对外路由）。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Configuration
@RequiredArgsConstructor
public class FallbackController {

    private final FallbackHandler handler;

    /**
     * 注册熔断兜底函数式路由.
     *
     * @return {@code /__gateway/fallback} 的路由函数
     */
    @Bean
    public RouterFunction<ServerResponse> gatewayFallbackRoute() {
        return route(path("/__gateway/fallback"), request -> handler.fallback(request.exchange()));
    }
}
