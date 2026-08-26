package com.apex.scaffold.interfaces.filter;

import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 安全响应头注入过滤器
 *
 * <p>在 HTTP 响应中添加安全相关的响应头，提升应用的安全性。
 * 该过滤器在所有业务过滤器之前执行，确保安全头始终存在。
 *
 * <h2>注入的响应头列表</h2>
 * <table border="1">
 *   <caption>安全响应头明细表</caption>
 *   <tr><th>响应头</th><th>值</th><th>说明</th></tr>
 *   <tr><td>X-Content-Type-Options</td><td>nosniff</td><td>禁止 MIME 类型嗅探</td></tr>
 *   <tr><td>X-Frame-Options</td><td>DENY</td><td>禁止页面被嵌入 iframe，防点击劫持</td></tr>
 *   <tr><td>X-XSS-Protection</td><td>0</td><td>禁用老旧 XSS 过滤器，由 CSP 替代</td></tr>
 *   <tr><td>Content-Security-Policy</td><td>default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'</td><td>内容安全策略，限制资源加载来源</td></tr>
 *   <tr><td>Strict-Transport-Security</td><td>max-age=31536000; includeSubDomains</td><td>强制 HTTPS 连接</td></tr>
 *   <tr><td>Cache-Control</td><td>no-store, no-cache, must-revalidate</td><td>禁止缓存敏感内容</td></tr>
 *   <tr><td>Pragma</td><td>no-cache</td><td>兼容 HTTP/1.0 的缓存控制</td></tr>
 *   <tr><td>Referrer-Policy</td><td>strict-origin-when-cross-origin</td><td>控制 Referer 头信息泄露</td></tr>
 *   <tr><td>Permissions-Policy</td><td>geolocation=(), camera=(), microphone=()</td><td>限制浏览器 API 权限</td></tr>
 * </table>
 *
 * <h2>HSTS 配置</h2>
 * <p>
 * Strict-Transport-Security 头的 max-age 和 includeSubDomains 参数
 * 可通过 {@link SecurityHeadersProperties} 配置，默认 max-age 为 1 年。
 * </p>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 * @see SecurityHeadersProperties
 * @see HttpHeaders
 */
@Component
@Order(-170)
@RequiredArgsConstructor
public class SecurityHeaderWebFilter implements WebFilter {

    /** X-Content-Type-Options 响应头名称 */
    private static final String HEADER_X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";

    /** X-Frame-Options 响应头名称 */
    private static final String HEADER_X_FRAME_OPTIONS = "X-Frame-Options";

    /** X-XSS-Protection 响应头名称 */
    private static final String HEADER_X_XSS_PROTECTION = "X-XSS-Protection";

    /** Content-Security-Policy 响应头名称 */
    private static final String HEADER_CONTENT_SECURITY_POLICY = "Content-Security-Policy";

    /** Strict-Transport-Security 响应头名称 */
    private static final String HEADER_STRICT_TRANSPORT_SECURITY = "Strict-Transport-Security";

    /** Cache-Control 响应头名称 */
    private static final String HEADER_CACHE_CONTROL = "Cache-Control";

    /** Pragma 响应头名称 */
    private static final String HEADER_PRAGMA = "Pragma";

    /** Referrer-Policy 响应头名称 */
    private static final String HEADER_REFERRER_POLICY = "Referrer-Policy";

    /** Permissions-Policy 响应头名称 */
    private static final String HEADER_PERMISSIONS_POLICY = "Permissions-Policy";

    /** X-Content-Type-Options 值：禁止 MIME 类型嗅探 */
    private static final String X_CONTENT_TYPE_OPTIONS_VALUE = "nosniff";

    /** X-Frame-Options 值：禁止嵌入 iframe */
    private static final String X_FRAME_OPTIONS_VALUE = "DENY";

    /** X-XSS-Protection 值：禁用 XSS 过滤 */
    private static final String X_XSS_PROTECTION_VALUE = "0";

    /**
     * Content-Security-Policy 值：内容安全策略
     *
     * <p>限制资源加载来源，仅允许加载同源资源，降低 XSS 攻击风险。
     */
    private static final String CONTENT_SECURITY_POLICY_VALUE =
            "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'";

    /** Cache-Control 值：禁止缓存 */
    private static final String CACHE_CONTROL_VALUE = "no-store, no-cache, must-revalidate";

    /** Pragma 值：兼容 HTTP/1.0 禁止缓存 */
    private static final String PRAGMA_VALUE = "no-cache";

    /** Referrer-Policy 值：跨域时仅发送 origin */
    private static final String REFERRER_POLICY_VALUE = "strict-origin-when-cross-origin";

    /** Permissions-Policy 值：禁用敏感 API 权限 */
    private static final String PERMISSIONS_POLICY_VALUE = "geolocation=(), camera=(), microphone=()";

    /** 安全响应头配置属性，定义 CSP、HSTS、X-Frame-Options 等头的开关和值。 */
    private final SecurityHeadersProperties securityHeadersProperties;

    /**
     * 执行安全响应头注入过滤
     *
     * <p>如果配置启用，向 HTTP 响应中添加所有安全响应头，
     * 然后继续执行过滤器链。
     *
     * @param exchange 当前 HTTP 请求-响应交换对象
     * @param chain    WebFlux 过滤器链
     * @return 表示过滤完成的 Mono
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (securityHeadersProperties.isEnabled()) {
            injectSecurityHeaders(exchange);
        }
        return chain.filter(exchange);
    }

    /**
     * 注入安全响应头
     *
     * <p>向 HTTP 响应中添加所有预定义的安全响应头，涵盖
     * 内容安全、传输安全、缓存控制、权限管理等多个维度。
     *
     * @param exchange 当前 HTTP 请求-响应交换对象
     */
    private void injectSecurityHeaders(ServerWebExchange exchange) {
        HttpHeaders headers = exchange.getResponse().getHeaders();

        headers.set(HEADER_X_CONTENT_TYPE_OPTIONS, X_CONTENT_TYPE_OPTIONS_VALUE);
        headers.set(HEADER_X_FRAME_OPTIONS, X_FRAME_OPTIONS_VALUE);
        headers.set(HEADER_X_XSS_PROTECTION, X_XSS_PROTECTION_VALUE);
        headers.set(HEADER_CONTENT_SECURITY_POLICY, CONTENT_SECURITY_POLICY_VALUE);

        String hstsValue = "max-age=" + securityHeadersProperties.getHstsMaxAge();
        if (securityHeadersProperties.isHstsIncludeSubdomains()) {
            hstsValue += "; includeSubDomains";
        }
        headers.set(HEADER_STRICT_TRANSPORT_SECURITY, hstsValue);

        headers.set(HEADER_CACHE_CONTROL, CACHE_CONTROL_VALUE);
        headers.set(HEADER_PRAGMA, PRAGMA_VALUE);
        headers.set(HEADER_REFERRER_POLICY, REFERRER_POLICY_VALUE);
        headers.set(HEADER_PERMISSIONS_POLICY, PERMISSIONS_POLICY_VALUE);
    }
}
