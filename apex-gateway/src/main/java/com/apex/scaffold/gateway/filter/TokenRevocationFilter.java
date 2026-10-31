package com.apex.scaffold.gateway.filter;

import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.gateway.security.RevocationCacheService;
import com.apex.scaffold.gateway.support.GatewayWebUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关令牌撤销过滤器（统一认证收口 A3）：验签通过的令牌命中本地撤销缓存即 401。
 *
 * <p>紧随 {@link JwtSignatureFilter}（order 10）执行，仅消费其放入 exchange 属性的
 * 已验签 Claims；缓存由 {@link RevocationCacheService} 周期拉取认证中心撤销增量维护。
 * 未启用撤销流或令牌无 {@code jti} 声明时直接放行（与撤销流启用前行为一致）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenRevocationFilter implements GlobalFilter, Ordered {

    /** 验签过滤器声明：已撤销令牌与过期令牌同口径收敛为 401 */
    private static final int ORDER_AFTER_SIGNATURE = 11;

    private final RevocationCacheService revocationCacheService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!revocationCacheService.enabled()) {
            return chain.filter(exchange);
        }
        Object attribute = exchange.getAttribute(JwtSignatureFilter.ATTR_CLAIMS);
        if (!(attribute instanceof Claims claims)) {
            return chain.filter(exchange);
        }
        String jti = claims.getId();
        if (revocationCacheService.isRevoked(jti)) {
            log.debug(
                    "命中撤销缓存，拒绝已撤销令牌: jti={}, path={}",
                    jti,
                    exchange.getRequest().getPath().value());
            return GatewayWebUtil.writeError(exchange, HttpStatus.UNAUTHORIZED, ResultCode.TOKEN_EXPIRED);
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return ORDER_AFTER_SIGNATURE;
    }
}
