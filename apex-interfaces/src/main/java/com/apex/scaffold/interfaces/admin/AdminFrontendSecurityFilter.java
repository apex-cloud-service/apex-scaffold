package com.apex.scaffold.interfaces.admin;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Adds a strict browser security boundary to every embedded administration asset.
 *
 * <p>仅对 {@code /admin} 及其子路径的响应附加防缓存与安全响应头（CSP、Referrer-Policy、
 * Permissions-Policy、COOP、CORP、X-Content-Type-Options、X-Frame-Options），
 * 其余路径直接透传，不引入任何额外处理开销。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Component
@Order(-160)
public class AdminFrontendSecurityFilter implements WebFilter {

    private static final String CONTENT_SECURITY_POLICY = "default-src 'none'; "
            + "script-src 'self'; style-src 'self'; img-src 'self' data: blob: https:; "
            + "connect-src 'self'; font-src 'self'; base-uri 'none'; form-action 'self'; "
            + "frame-ancestors 'none'; object-src 'none'; manifest-src 'self'";

    /**
     * 处理过滤器链中的单个请求。
     *
     * <p>非管理路径直接放行；管理路径在响应提交前注册安全头写入回调，之后继续执行
     * 过滤器链。</p>
     *
     * @param exchange 当前 WebFlux 交换对象
     * @param chain 过滤器链
     * @return 过滤器链处理结果
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!(path.equals("/admin") || path.startsWith("/admin/"))) {
            return chain.filter(exchange);
        }
        exchange.getResponse().beforeCommit(() -> {
            applyStrictHeaders(exchange.getResponse().getHeaders());
            return Mono.empty();
        });
        return chain.filter(exchange);
    }

    /**
     * 为管理端响应写入完整的浏览器安全与防缓存响应头。
     *
     * <p>通过 {@code beforeCommit} 在响应真正提交前调用，确保无论后续过滤器链成功
     * 或失败返回错误页，安全头均不会被遗漏。</p>
     *
     * @param headers 响应头集合，将被就地修改
     */
    private void applyStrictHeaders(HttpHeaders headers) {
        headers.set(HttpHeaders.CACHE_CONTROL, "no-store, max-age=0");
        headers.set(HttpHeaders.PRAGMA, "no-cache");
        headers.set("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=(), usb=()");
        headers.set("Cross-Origin-Opener-Policy", "same-origin");
        headers.set("Cross-Origin-Resource-Policy", "same-origin");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "DENY");
    }
}
