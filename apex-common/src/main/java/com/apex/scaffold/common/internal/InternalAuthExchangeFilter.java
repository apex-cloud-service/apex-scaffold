package com.apex.scaffold.common.internal;

import java.util.Map;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;

/**
 * 为内部客户端请求自动附加 HMAC 签名头。
 *
 * <p>只在请求路径以 {@code /internal/v1/} 开头时生效；外部调用与其他路径不受影响。
 * 这样各服务的 {@code WebClient} 不需要重复拼接签名逻辑，也不会误把内部凭据发到外部。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
public final class InternalAuthExchangeFilter {

    private InternalAuthExchangeFilter() {}

    /**
     * 给 {@link org.springframework.web.reactive.function.client.WebClient.Builder} 追加内部签名过滤器。
     *
     * <p>配置关闭或签名器未配置密钥时返回原 Builder，行为与现状一致，便于测试与本地开发。
     *
     * @param builder WebClient 构建器
     * @param properties 内部认证配置
     * @param signer 签名器（可空）
     * @return 原构建器（链式）
     */
    public static org.springframework.web.reactive.function.client.WebClient.Builder apply(
            org.springframework.web.reactive.function.client.WebClient.Builder builder,
            InternalAuthProperties properties,
            InternalServiceSigner signer) {
        if (properties == null || !properties.isEnabled() || signer == null || !signer.configured()) {
            return builder;
        }
        return builder.filter(of(properties, signer));
    }

    /**
     * 创建过滤器。
     *
     * @param properties 内部认证配置
     * @param signer 签名器
     * @return 过滤器
     */
    public static ExchangeFilterFunction of(InternalAuthProperties properties, InternalServiceSigner signer) {
        return (request, next) -> {
            if (!properties.isEnabled() || !signer.configured()) {
                return next.exchange(request);
            }
            String path = request.url().getPath();
            if (!path.startsWith(InternalAuthWebFilter.INTERNAL_PREFIX)) {
                return next.exchange(request);
            }
            HttpMethod method = request.method();
            String tenant = request.headers().getFirst(InternalServiceSigner.HEADER_TENANT);
            if (tenant == null) {
                tenant = com.apex.scaffold.common.context.TenantContext.currentTenantId()
                        .orElse(null);
            }
            Map<String, String> headers = signer.sign(properties.getServiceName(), method.name(), path, tenant);
            ClientRequest.Builder builder = ClientRequest.from(request);
            headers.forEach(builder::header);
            if (tenant != null && !tenant.isBlank()) {
                builder.header(InternalServiceSigner.HEADER_TENANT, tenant);
            }
            return next.exchange(builder.build());
        };
    }
}
