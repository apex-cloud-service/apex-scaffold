package com.apex.scaffold.common.internal;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 内部契约请求签名过滤器（{@code /internal/v1/**}）。
 *
 * <p>把内部端点从"网络信任 + permitAll"改为"共享密钥 HMAC 签名 + 短时间窗 + nonce 防重放"。
 * 不依赖用户 JWT，也不引入第二授权服务器；因此登录前的租户状态查询、网关撤销流拉取等
 * 引导链路可以继续使用。签名串包含方法、路径与显式租户，拒绝篡改与跨租户重放。
 *
 * <p>{@code apex.internal-auth.enabled=false} 时完全跳过（仅供本地开发）；
 * 启用但未配置密钥时一律 503 拒绝，避免误以为已受保护。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
public class InternalAuthWebFilter implements WebFilter, Ordered {

    /** 内部契约前缀。 */
    public static final String INTERNAL_PREFIX = "/internal/v1/";

    private final InternalAuthProperties properties;

    private final InternalServiceSigner signer;

    private final boolean loopbackOnly;

    /**
     * 构造过滤器。
     *
     * @param properties 内部认证配置
     * @param signer 签名器
     * @param loopbackOnly 是否额外要求直连地址为回环（本地开发加固）
     */
    public InternalAuthWebFilter(
            InternalAuthProperties properties, InternalServiceSigner signer, boolean loopbackOnly) {
        this.properties = properties;
        this.signer = signer;
        this.loopbackOnly = loopbackOnly;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith(INTERNAL_PREFIX)) {
            return chain.filter(exchange);
        }
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }
        for (String exempt : properties.getExemptPaths()) {
            if (path.startsWith(exempt)) {
                return chain.filter(exchange);
            }
        }
        if (!signer.configured()) {
            return reject(exchange, HttpStatus.SERVICE_UNAVAILABLE);
        }
        var headers = exchange.getRequest().getHeaders();
        boolean valid = signer.verify(
                headers.getFirst(InternalServiceSigner.HEADER_SERVICE),
                headers.getFirst(InternalServiceSigner.HEADER_TIMESTAMP),
                headers.getFirst(InternalServiceSigner.HEADER_NONCE),
                headers.getFirst(InternalServiceSigner.HEADER_SIGNATURE),
                exchange.getRequest().getMethod().name(),
                path,
                headers.getFirst(InternalServiceSigner.HEADER_TENANT));
        if (!valid) {
            return reject(exchange, HttpStatus.UNAUTHORIZED);
        }
        if (loopbackOnly && !isLoopback(exchange)) {
            return reject(exchange, HttpStatus.FORBIDDEN);
        }
        return chain.filter(exchange);
    }

    private boolean isLoopback(ServerWebExchange exchange) {
        var remote = exchange.getRequest().getRemoteAddress();
        if (remote == null || remote.getAddress() == null) {
            return false;
        }
        return remote.getAddress().isLoopbackAddress();
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = "{\"code\":401,\"msg\":\"内部调用未通过认证\"}".getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }

    @Override
    public int getOrder() {
        // 先于业务安全链与能力守卫，保证内部端点在任何情况下都先验签。
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }

    /**
     * 按配置构建签名器。
     *
     * @param properties 配置
     * @param clock 时钟
     * @return 签名器
     */
    public static InternalServiceSigner signer(InternalAuthProperties properties, Clock clock) {
        return new InternalServiceSigner(
                properties.getSecret(), Duration.ofSeconds(Math.max(1, properties.getAllowedSkewSeconds())), clock);
    }

    /**
     * 读取当前配置的调用头（供内部客户端复用）。
     *
     * @param properties 配置
     * @param signer 签名器
     * @param method 方法
     * @param path 路径
     * @param tenant 租户
     * @return 请求头
     */
    public static Map<String, String> signHeaders(
            InternalAuthProperties properties,
            InternalServiceSigner signer,
            String method,
            String path,
            String tenant) {
        return signer.sign(properties.getServiceName(), method, path, tenant);
    }
}
