package com.apex.scaffold.common.internal;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.JsonUtil;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 内部调用验签过滤器。
 *
 * <p>对正文与查询串完整验签，校验调用方路径白名单并固定可信租户。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
public class InternalAuthWebFilter implements WebFilter, Ordered {
    public static final String INTERNAL_PREFIX = "/internal/v1/";
    private static final int MAX_BODY_BYTES = 1024 * 1024;
    private final InternalAuthProperties properties;
    private final InternalServiceSigner signer;
    private final boolean loopbackOnly;
    private final AntPathMatcher paths = new AntPathMatcher();

    /**
     * 构造过滤器。
     *
     * @param properties 内部认证配置
     * @param signer 内部签名器
     * @param loopbackOnly 是否只接受回环地址调用
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
        if (!path.startsWith(INTERNAL_PREFIX) || !properties.isEnabled()) {
            return chain.filter(exchange);
        }
        if (properties.getExemptPaths().contains(path)) {
            return chain.filter(exchange);
        }
        if (!signer.configured()) {
            return reject(exchange, HttpStatus.SERVICE_UNAVAILABLE, ResultCode.SERVICE_UNAVAILABLE);
        }
        var headers = exchange.getRequest().getHeaders();
        String caller = headers.getFirst(InternalServiceSigner.HEADER_SERVICE);
        String tenant = headers.getFirst(InternalServiceSigner.HEADER_TENANT);
        if (caller == null
                || properties.getCallers().getOrDefault(caller, java.util.List.of()).stream()
                        .noneMatch(pattern -> paths.match(pattern, path))) {
            return reject(exchange, HttpStatus.FORBIDDEN, ResultCode.FORBIDDEN);
        }
        boolean unscoped = properties.getUnscopedPaths().stream().anyMatch(pattern -> paths.match(pattern, path));
        if (!unscoped && (tenant == null || tenant.isBlank())) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, ResultCode.UNAUTHORIZED);
        }
        String target = exchange.getRequest().getURI().getRawPath();
        if (exchange.getRequest().getURI().getRawQuery() != null) {
            target += "?" + exchange.getRequest().getURI().getRawQuery();
        }
        String signedTarget = target;
        return DataBufferUtils.join(exchange.getRequest().getBody(), MAX_BODY_BYTES)
                .map(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    try {
                        buffer.read(bytes);
                        return bytes;
                    } finally {
                        DataBufferUtils.release(buffer);
                    }
                })
                .defaultIfEmpty(new byte[0])
                .flatMap(bytes -> {
                    boolean valid = signer.verify(
                            caller,
                            headers.getFirst(InternalServiceSigner.HEADER_TIMESTAMP),
                            headers.getFirst(InternalServiceSigner.HEADER_NONCE),
                            headers.getFirst(InternalServiceSigner.HEADER_SIGNATURE),
                            exchange.getRequest().getMethod().name(),
                            signedTarget,
                            tenant,
                            bytes);
                    if (!valid) {
                        return reject(exchange, HttpStatus.UNAUTHORIZED, ResultCode.UNAUTHORIZED);
                    }
                    if (loopbackOnly && !isLoopback(exchange)) {
                        return reject(exchange, HttpStatus.FORBIDDEN, ResultCode.FORBIDDEN);
                    }
                    String supplied = headers.getFirst("X-Tenant-Id");
                    String queried = exchange.getRequest().getQueryParams().getFirst("tenantId");
                    if ((supplied != null && !supplied.equals(tenant))
                            || (queried != null && !queried.equals(tenant))) {
                        return reject(exchange, HttpStatus.FORBIDDEN, ResultCode.FORBIDDEN);
                    }
                    var request = new ServerHttpRequestDecorator(exchange.getRequest()) {
                        @Override
                        public Flux<DataBuffer> getBody() {
                            return Flux.defer(() -> Flux.just(
                                    exchange.getResponse().bufferFactory().wrap(bytes)));
                        }
                    };
                    ServerWebExchange trusted =
                            exchange.mutate().request(request).build();
                    trusted.getAttributes()
                            .put(InternalServiceIdentity.ATTR_IDENTITY, new InternalServiceIdentity(caller, tenant));
                    Mono<Void> invocation = chain.filter(trusted);
                    return tenant == null || tenant.isBlank()
                            ? invocation
                            : TenantContext.withTenant(invocation).contextWrite(TenantContext.withTenant(tenant));
                });
    }

    private boolean isLoopback(ServerWebExchange exchange) {
        var remote = exchange.getRequest().getRemoteAddress();
        return remote != null
                && remote.getAddress() != null
                && remote.getAddress().isLoopbackAddress();
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, ResultCode code) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes = JsonUtil.toJson(Result.error(code)).getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }

    /**
     * 按配置构造签名器。
     *
     * @param properties 内部认证配置
     * @param clock 计算签名时间戳所用的时钟
     * @return 内部签名器
     */
    public static InternalServiceSigner signer(InternalAuthProperties properties, Clock clock) {
        return new InternalServiceSigner(
                properties.getSecret(), Duration.ofSeconds(Math.max(1, properties.getAllowedSkewSeconds())), clock);
    }

    /**
     * 生成内部调用签名头。
     *
     * @param properties 内部认证配置
     * @param signer 内部签名器
     * @param method HTTP 方法
     * @param path 请求路径
     * @param tenant 租户编码
     * @return 签名字段与取值
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
