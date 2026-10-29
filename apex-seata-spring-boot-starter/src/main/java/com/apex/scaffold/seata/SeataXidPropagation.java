package com.apex.scaffold.seata;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.apache.seata.core.context.RootContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

/**
 * 仅在 TLS 内部链路上传播 XID；网关必须清除客户端提供的传播凭据。
 * @author Apex Team
 * @since 1.3.1
 */
@lombok.RequiredArgsConstructor
public final class SeataXidPropagation {
    private static final int MAX_XID_LENGTH = 128;
    private static final String XID_PATTERN = "[A-Za-z0-9_.:\\-]+";
    public static final String XID_HEADER = "X-Apex-Seata-Xid";
    public static final String AUTH_HEADER = "X-Apex-Seata-Key";
    private final SeataProperties properties;

    public WebFilter inbound() {
        return (exchange, chain) -> {
            String xid = exchange.getRequest().getHeaders().getFirst(XID_HEADER);
            if (xid == null) {
                return chain.filter(exchange);
            }
            String key = exchange.getRequest().getHeaders().getFirst(AUTH_HEADER);
            boolean trusted = exchange.getRequest().getPath().value().startsWith(properties.getInboundPathPrefix())
                    && key != null
                    && MessageDigest.isEqual(
                            key.getBytes(StandardCharsets.UTF_8),
                            properties.getPropagationSecret().getBytes(StandardCharsets.UTF_8));
            if (!trusted || xid.length() > MAX_XID_LENGTH || !xid.matches(XID_PATTERN)) {
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }
            return chain.filter(exchange)
                    .contextWrite(context -> context.put(SeataTransactionExecutor.XID_CONTEXT_KEY, xid));
        };
    }

    /** 显式挂载到受管 WebClient；清除调用者手工设置的传播头。 */
    public ExchangeFilterFunction outbound() {
        return (request, next) -> Mono.deferContextual(context -> {
            ClientRequest.Builder builder = ClientRequest.from(request).headers(headers -> {
                headers.remove(XID_HEADER);
                headers.remove(AUTH_HEADER);
            });
            String origin = request.url().getScheme() + "://" + request.url().getRawAuthority();
            String xid = context.getOrDefault(SeataTransactionExecutor.XID_CONTEXT_KEY, RootContext.getXID());
            if (xid != null && properties.getAllowedOrigins().contains(origin)) {
                builder.header(XID_HEADER, xid).header(AUTH_HEADER, properties.getPropagationSecret());
            }
            return next.exchange(builder.build());
        });
    }
}
