package com.apex.scaffold.gateway.filter;

import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.JsonUtil;
import com.apex.scaffold.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关 JWT 验签过滤器（全局）.
 *
 * <p>职责（只做"廉价"的守门动作，不做深度认证）：
 * <ol>
 *   <li>公开路径白名单直接放行</li>
 *   <li>解析 {@code Authorization: Bearer xxx}，校验 JWT 签名与过期时间</li>
 *   <li>删除客户端伪造的身份头，再从 Token 声明注入可信身份头</li>
 * </ol>
 *
 * <p><b>安全红线</b>：先剥离外部传入的 {@code X-User-Id} / {@code X-Tenant-Id} 等内部头，
 * 再注入由验签结果派生的值，防止身份伪造。黑名单、会话、RBAC 由各业务服务二次校验。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Component
public class JwtAuthGatewayFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthGatewayFilter.class);

    /** 请求头：用户 ID（网关注入，服务侧信任） */
    public static final String HEADER_USER_ID = "X-User-Id";

    /** 请求头：租户 ID（网关注入，服务侧信任） */
    public static final String HEADER_TENANT_ID = "X-Tenant-Id";

    /** 请求头：客户端类型（网关注入） */
    public static final String HEADER_CLIENT_TYPE = "X-Client-Type";

    /** 需要剥离的伪造风险头 */
    private static final List<String> FORGED_HEADERS =
            List.of(HEADER_USER_ID, HEADER_TENANT_ID, HEADER_CLIENT_TYPE, "X-Internal-Token");

    /** 公开路径（无需 Token），与 SystemConstants.PUBLIC_PATHS 保持语义一致 */
    private static final List<String> PUBLIC_PATHS = List.of(
            "/",
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            "/api/v1/auth/captcha",
            "/api/v1/auth/captcha/**",
            "/api/v1/auth/sms/code",
            "/api/v1/auth/cl/sms/code",
            "/api/v1/auth/cl/validate-mobile",
            "/api/v1/auth/wechat/mp/authorize",
            "/api/v1/auth/wechat/bind-phone",
            "/api/v1/office/login",
            "/api/v1/office/callback",
            "/api/v1/onboarding/policies",
            "/api/v1/health",
            "/api/v1/info",
            "/health",
            "/oauth2/token",
            "/oauth2/revoke",
            "/oauth2/introspect",
            "/actuator/**",
            "/.well-known/**");

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 内部 API 不对公网暴露：网关强制拒绝（内部调用走集群内直连）
        if (path.startsWith("/internal/")) {
            log.warn("拒绝外部访问内部 API: path={}", path);
            return writeError(exchange, HttpStatus.FORBIDDEN, ResultCode.FORBIDDEN);
        }

        // 剥离客户端可能伪造的身份头（无论是否公开路径）
        ServerHttpRequest sanitized = exchange.getRequest()
                .mutate()
                .headers(headers -> FORGED_HEADERS.forEach(headers::remove))
                .build();

        if (isPublicPath(path)) {
            return chain.filter(exchange.mutate().request(sanitized).build());
        }

        String token = resolveBearerToken(sanitized);
        if (token == null) {
            return writeError(exchange, HttpStatus.UNAUTHORIZED, ResultCode.UNAUTHORIZED);
        }

        Claims claims;
        try {
            claims = JwtUtil.parseToken(token);
        } catch (Exception e) {
            log.debug("JWT 解析失败: path={}, reason={}", path, e.getMessage());
            return writeError(exchange, HttpStatus.UNAUTHORIZED, ResultCode.TOKEN_EXPIRED);
        }
        if (claims == null || JwtUtil.isTokenExpired(token)) {
            return writeError(exchange, HttpStatus.UNAUTHORIZED, ResultCode.TOKEN_EXPIRED);
        }

        return chain.filter(applyIdentityHeaders(exchange, sanitized, claims));
    }

    /**
     * 从 Token 声明派生身份头并注入请求.
     *
     * @param exchange 当前交换
     * @param request  已剥离伪造头的请求
     * @param claims   JWT 声明
     * @return 带可信身份头的交换对象
     */
    private ServerWebExchange applyIdentityHeaders(
            ServerWebExchange exchange, ServerHttpRequest request, Claims claims) {
        String userId = resolveFirstNonBlank(claims, "userId", "accountId");
        String tenantId = resolveFirstNonBlank(claims, "tenantId");
        String clientType = resolveFirstNonBlank(claims, "clientType");

        if (userId == null) {
            // 无主体标识的 Token 视为无效：宁可拒绝，不注入空身份
            log.debug("JWT 缺少 userId/accountId 声明, 拒绝请求");
            return exchange;
        }

        ServerHttpRequest mutated = request.mutate()
                .header(HEADER_USER_ID, userId)
                .header(HEADER_TENANT_ID, tenantId == null ? "" : tenantId)
                .header(HEADER_CLIENT_TYPE, clientType == null ? "" : clientType)
                .build();
        return exchange.mutate().request(mutated).build();
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

    /**
     * 提取 Bearer Token.
     *
     * @param request 请求
     * @return Token 字符串，缺失时返回 {@code null}
     */
    private String resolveBearerToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        String token = header.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    /**
     * 判断是否为公开路径.
     *
     * @param path 请求路径
     * @return {@code true} 表示无需认证
     */
    private boolean isPublicPath(String path) {
        for (String pattern : PUBLIC_PATHS) {
            if (PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 以统一 {@link Result} 结构写出错误响应.
     *
     * @param exchange 当前交换
     * @param status   HTTP 状态码
     * @param code     业务错误码
     * @return 完成信号
     */
    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, ResultCode code) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = JsonUtil.toJson(Result.error(code)).getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
