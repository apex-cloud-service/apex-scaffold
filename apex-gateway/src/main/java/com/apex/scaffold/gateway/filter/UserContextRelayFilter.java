package com.apex.scaffold.gateway.filter;

import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.gateway.support.GatewayWebUtil;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 用户上下文透传过滤器（全局）.
 *
 * <p>对应迁移方案 §5.3 过滤器链第 4 位（order = 20）。
 *
 * <p>读取由 {@link JwtSignatureFilter} 验签后暂存的 {@link Claims}，
 * 解析 {@code sub/accountId/tenantId/clientType} 并注入可信身份头：
 * {@code X-User-Id} / {@code X-Tenant-Id} / {@code X-Client-Type}，供下游服务信任消费。
 *
 * <p><b>安全红线（方案 §5.3）</b>：
 * <ol>
 *   <li>伪造头已在 {@link JwtSignatureFilter} 阶段剥离；本过滤器只做「注入」，绝不透传外部值</li>
 *   <li>Token 缺少 {@code userId/accountId} 主体声明时，<b>严格返回 401</b>，
 *       不再像旧 {@code JwtAuthGatewayFilter} 那样放行空身份（历史缺陷修复）</li>
 * </ol>
 *
 * @author Apex Team
 * @since 1.3.1
 * @see JwtSignatureFilter
 */
@Component
public class UserContextRelayFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(UserContextRelayFilter.class);

    /** 过滤器顺序：验签（10）之后、限流（30）之前 */
    public static final int ORDER = 20;

    /** 请求头：用户 ID（网关注入，服务侧信任） */
    public static final String HEADER_USER_ID = "X-User-Id";

    /** 请求头：租户 ID（网关注入，服务侧信任） */
    public static final String HEADER_TENANT_ID = "X-Tenant-Id";

    /** 请求头：客户端类型（网关注入） */
    public static final String HEADER_CLIENT_TYPE = "X-Client-Type";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Object attr = exchange.getAttributes().get(JwtSignatureFilter.ATTR_CLAIMS);
        if (!(attr instanceof Claims claims)) {
            // 公开路径或未验签请求：无 Claims 可透传，原样放行
            return chain.filter(exchange);
        }

        String userId = resolveFirstNonBlank(claims, "userId", "accountId", "sub");
        String tenantId = resolveFirstNonBlank(claims, "tenantId");
        String clientType = resolveFirstNonBlank(claims, "clientType");

        if (userId == null) {
            // 无主体标识的 Token 视为无效：严格拒绝，绝不注入空身份
            log.debug("JWT 缺少 userId/accountId/sub 声明, 拒绝请求");
            return GatewayWebUtil.writeError(exchange, HttpStatus.UNAUTHORIZED, ResultCode.UNAUTHORIZED);
        }

        ServerHttpRequest mutated = exchange.getRequest()
                .mutate()
                .header(HEADER_USER_ID, userId)
                .header(HEADER_TENANT_ID, tenantId == null ? "" : tenantId)
                .header(HEADER_CLIENT_TYPE, clientType == null ? "" : clientType)
                .build();

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    /**
     * 按候选键顺序取第一个非空声明值.
     *
     * @param claims JWT 声明
     * @param keys   候选键（按优先级）
     * @return 声明值的字符串形式，均缺失时返回 {@code null}
     */
    private String resolveFirstNonBlank(Claims claims, String... keys) {
        for (String key : keys) {
            Object value = claims.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
