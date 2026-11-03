package com.apex.scaffold.gateway.filter;

import com.apex.scaffold.common.constant.SystemConstants;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.gateway.security.GatewayJwtVerifierService;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * JWT 签名校验过滤器（全局）.
 *
 * <p>对应迁移方案 §5.3 过滤器链第 3 位（order = 10）。
 *
 * <p>相比旧实现（已删除的 {@code JwtAuthGatewayFilter}）的对称验签，本过滤器改用
 * {@link GatewayJwtVerifierService} 完成 <b>JWKS 非对称（RSA/RS256）+ 过渡期 HMAC 双 kid</b>
 * 验签与过期校验（风险 #1 缓解，见迁移方案 §16）。
 *
 * <p>安全职责：
 * <ol>
 *   <li><b>内部 API 强拒</b>：{@code /internal/v1/**} 与旧 {@code /internal/**} 公网强制 403，
 *       内部调用仅允许集群内直连（方案 §6.2）</li>
 *   <li><b>公开路径白名单</b>：复用 {@link SystemConstants#PUBLIC_PATHS}（Nacos 可覆盖），
 *       白名单内跳过验签但保留伪造头剥离</li>
 *   <li><b>统一剥离伪造身份头</b>：无论是否公开路径，先删除客户端可能伪造的
 *       {@code X-User-Id}/{@code X-Tenant-Id}/{@code X-Client-Type}/{@code X-Internal-*}，
 *       防止身份伪造（安全红线，方案 §5.3）</li>
 *   <li><b>验签</b>：对受保护路径解析 {@code Authorization: Bearer xxx}，失败返回 401 Result</li>
 * </ol>
 *
 * <p>本过滤器只做验签与主体声明提取（写入 exchange attribute），
 * 真正往请求注入可信身份头的工作交由 {@link UserContextRelayFilter}（order 20）完成。
 *
 * @author Apex Team
 * @since 1.3.1
 * @see GatewayJwtVerifierService
 * @see UserContextRelayFilter
 */
@Slf4j
@Component
@lombok.RequiredArgsConstructor
public class JwtSignatureFilter implements GlobalFilter, Ordered {

    /** 过滤器顺序：JWKS 验签在 Trace 与 CORS 之后、上下文注入之前 */
    public static final int ORDER = 10;

    /** 内部 API 路径前缀（新）——公网强制 403 */
    private static final String INTERNAL_PREFIX_V1 = "/internal/v1";

    /** 内部 API 路径前缀（旧别名）——同样强制 403 */
    private static final String INTERNAL_PREFIX = "/internal";

    /** 验签通过后 Claims 暂存于 exchange attribute 的键名（供上下文注入过滤器读取） */
    public static final String ATTR_CLAIMS = "gateway.verified.claims";

    private final GatewayJwtVerifierService verifierService;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * 剥离伪造身份头并验证 HTTP JWT，精确 WebSocket 升级交由办公服务消费票据。
     * @param exchange 请求交换对象
     * @param chain 网关后续过滤链
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 内部 API 不对公网暴露：无论是否携带 Token 一律拒绝
        if (isInternalPath(path)) {
            log.warn("拒绝外部访问内部 API: path={}", path);
            return com.apex.scaffold.gateway.support.GatewayWebUtil.writeError(
                    exchange, HttpStatus.FORBIDDEN, ResultCode.FORBIDDEN);
        }

        // 剥离客户端可能伪造的身份头（安全红线：先剥离再注入）
        ServerHttpRequest sanitized = exchange.getRequest()
                .mutate()
                .headers(headers -> {
                    headers.remove("X-User-Id");
                    headers.remove("X-Tenant-Id");
                    headers.remove("X-Client-Type");
                    // X-Client-Scopes / X-Client-Id / X-Open-Api-Capability 是网关在 /open-api/** 注入的可信头，
                    // 外部伪造值一律剥离（能力标记头伪造只会触发服务侧二次校验，仍剥离以保口径纯净）
                    headers.remove("X-Client-Scopes");
                    headers.remove("X-Client-Id");
                    headers.remove("X-Open-Api-Capability");
                    // 外部请求不能注入内部身份或事务上下文；保留原始认证凭据供下游复验。
                    headers.remove("X-Apex-Seata-Xid");
                    headers.remove("X-Apex-Seata-Key");
                    headers.remove("TX_XID");
                    headers.keySet().removeIf(name -> name.regionMatches(true, 0, "X-Internal-", 0, 11));
                })
                .build();

        boolean messageSocketUpgrade = "/api/v1/messages/ws".equals(path)
                && "websocket".equalsIgnoreCase(sanitized.getHeaders().getUpgrade());
        // 公开路径：跳过验签，仅剥离伪造头后继续
        if (isPublicPath(path) || messageSocketUpgrade) {
            // 浏览器握手由 office 原子消费短期票据；身份头仍已剥离，网关不信任票据内身份。
            return chain.filter(exchange.mutate().request(sanitized).build());
        }

        String token = resolveBearerToken(sanitized);
        if (token == null) {
            return com.apex.scaffold.gateway.support.GatewayWebUtil.writeError(
                    exchange, HttpStatus.UNAUTHORIZED, ResultCode.UNAUTHORIZED);
        }

        // JWKS 验签 + 过期校验；失败统一收敛为 401
        Claims claims = verifierService.verifyAndParse(token);
        if (claims == null) {
            log.debug("JWT 验签失败或已过期: path={}", path);
            return com.apex.scaffold.gateway.support.GatewayWebUtil.writeError(
                    exchange, HttpStatus.UNAUTHORIZED, ResultCode.TOKEN_EXPIRED);
        }

        // 将已验证的 Claims 放入 exchange 属性，交给 UserContextRelayFilter 注入身份头
        ServerWebExchange enriched = exchange.mutate().request(sanitized).build();
        enriched.getAttributes().put(ATTR_CLAIMS, claims);
        return chain.filter(enriched);
    }

    /**
     * 判断是否为内部 API 路径.
     *
     * <p>命中 {@code /internal/**}（含新规范 {@code /internal/v1/**}）即拒绝。
     *
     * @param path 请求路径
     * @return {@code true} 表示内部 API
     */
    private boolean isInternalPath(String path) {
        return INTERNAL_PREFIX.equals(path)
                || path.startsWith(INTERNAL_PREFIX + "/")
                || INTERNAL_PREFIX_V1.equals(path)
                || path.startsWith(INTERNAL_PREFIX_V1 + "/")
                || path.startsWith(INTERNAL_PREFIX);
    }

    /**
     * 判断是否为公开路径.
     *
     * <p>复用 {@link SystemConstants#PUBLIC_PATHS} 作为权威白名单，
     * 保持与主应用 SecurityConfig 语义一致（服务可更严格、不宜更宽松）。
     *
     * @param path 请求路径
     * @return {@code true} 表示无需认证
     */
    private boolean isPublicPath(String path) {
        for (String pattern : SystemConstants.PUBLIC_PATHS) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 提取 Bearer Token.
     *
     * @param request 请求
     * @return Token 字符串，缺失返回 {@code null}
     */
    private String resolveBearerToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(SystemConstants.TOKEN_PREFIX)) {
            return null;
        }
        String token = header.substring(SystemConstants.TOKEN_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    /**
     * 返回全局过滤器执行顺序，使 JWT 验签在可信用户上下文转发前完成。
     *
     * @return 固定顺序值 {@value #ORDER}，值越小越先进入过滤链
     */
    @Override
    public int getOrder() {
        return ORDER;
    }
}
