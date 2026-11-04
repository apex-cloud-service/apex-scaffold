package com.apex.scaffold.common.internal;

import com.apex.scaffold.common.context.TenantContext;
import java.util.Map;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.client.reactive.ClientHttpRequestDecorator;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 内部调用出站签名过滤器。
 *
 * <p>在正文编码完成后签名；不从普通中继头推断租户身份。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
public final class InternalAuthExchangeFilter {
    private static final int MAX_BODY_BYTES = 1024 * 1024;

    private InternalAuthExchangeFilter() {}

    /**
     * 为客户端构建器挂载内部签名过滤器。
     *
     * @param builder 客户端构建器
     * @param properties 内部认证配置；为空时原样返回
     * @param signer 内部签名器；为空时原样返回
     * @return 挂载后的构建器
     */
    public static WebClient.Builder apply(
            WebClient.Builder builder, InternalAuthProperties properties, InternalServiceSigner signer) {
        return properties == null || signer == null ? builder : builder.filter(of(properties, signer));
    }

    /**
     * 创建内部签名交换过滤器。
     *
     * @param properties 内部认证配置
     * @param signer 内部签名器
     * @return 交换过滤器
     */
    public static ExchangeFilterFunction of(InternalAuthProperties properties, InternalServiceSigner signer) {
        return (request, next) -> Mono.deferContextual(context -> {
            if (!request.url().getPath().startsWith(InternalAuthWebFilter.INTERNAL_PREFIX)) {
                return next.exchange(request);
            }
            if (!properties.isEnabled()) {
                return next.exchange(request);
            }
            if (!signer.configured()) {
                return Mono.error(new IllegalStateException("Internal signing key is not configured"));
            }
            String explicit = request.headers().getFirst(InternalServiceSigner.HEADER_TENANT);
            String tenant = explicit != null ? explicit : context.getOrDefault(TenantContext.REACTOR_CONTEXT_KEY, null);
            String target = request.url().getRawPath();
            if (request.url().getRawQuery() != null) {
                target += "?" + request.url().getRawQuery();
            }
            String signedTarget = target;
            ClientRequest signed = ClientRequest.from(request)
                    .body((output, bodyContext) -> request.body()
                            .insert(
                                    new ClientHttpRequestDecorator(output) {
                                        private void sign(byte[] body) {
                                            Map<String, String> values = signer.sign(
                                                    properties.getServiceName(),
                                                    request.method().name(),
                                                    signedTarget,
                                                    tenant,
                                                    body);
                                            getHeaders().remove(InternalServiceSigner.HEADER_TENANT);
                                            values.forEach(getHeaders()::set);
                                        }

                                        @Override
                                        public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                                            return DataBufferUtils.join(body, MAX_BODY_BYTES)
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
                                                        sign(bytes);
                                                        return getDelegate()
                                                                .writeWith(Mono.just(bufferFactory()
                                                                        .wrap(bytes)));
                                                    });
                                        }

                                        @Override
                                        public Mono<Void> writeAndFlushWith(
                                                Publisher<? extends Publisher<? extends DataBuffer>> body) {
                                            return writeWith(Flux.from(body).concatMap(Flux::from));
                                        }

                                        @Override
                                        public Mono<Void> setComplete() {
                                            sign(new byte[0]);
                                            return getDelegate().setComplete();
                                        }
                                    },
                                    bodyContext))
                    .build();
            return next.exchange(signed);
        });
    }
}
