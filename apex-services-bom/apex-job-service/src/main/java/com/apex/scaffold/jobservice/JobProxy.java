package com.apex.scaffold.jobservice;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 固定 system 任务接口代理；认证、权限与租户以 system 校验结果为准。
 *
 * @author Apex Team
 * @since 1.3.1
 */
public class JobProxy {
    private static final String ROOT = "/admin-api/v1/jobs";
    /** Bearer 认证方案前缀，与 system 端 JWT 解析保持一致。 */
    private static final String BEARER_PREFIX = "Bearer ";
    /** 仅接受纯数字、非零开头、最长 18 位的路径 ID。 */
    private static final String ID_PATTERN = "[1-9][0-9]{0,17}";

    private final WebClient client;

    /**
     * 构造任务代理。
     *
     * @param client 指向 system 管理端口的 WebClient
     */
    public JobProxy(WebClient client) {
        this.client = client;
    }

    /**
     * 构建任务代理路由。
     *
     * <p>仅公开任务 CRUD、状态、触发与日志路径，不提供任意目标代理。
     *
     * @return 转发到 system 管理端口任务接口的路由函数
     */
    public RouterFunction<ServerResponse> routes() {
        return RouterFunctions.route()
                .GET(ROOT, this::forward)
                .GET(ROOT + "/page", this::forward)
                .POST(ROOT, this::forward)
                .GET(ROOT + "/{id}", this::forward)
                .PUT(ROOT + "/{id}", this::forward)
                .DELETE(ROOT + "/{id}", this::forward)
                .PUT(ROOT + "/{id}/status", this::forward)
                .POST(ROOT + "/{id}/run", this::forward)
                .GET(ROOT + "/{id}/logs", this::forward)
                .build();
    }

    /**
     * 校验协议输入并转发凭据。
     *
     * <p>缺失或非法 {@code Authorization} 直接返回 401；不转发客户端租户及内部认证头，
     * 由 system 管理端口按自身安全策略处理。
     *
     * @param request 原始请求，需携带 Bearer 令牌
     * @return 转发结果；令牌缺失或格式非法时为 401 空响应
     */
    public Mono<ServerResponse> forward(ServerRequest request) {
        String authorization = request.headers().firstHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null
                || !authorization.startsWith(BEARER_PREFIX)
                || authorization.length() <= BEARER_PREFIX.length()) {
            return ServerResponse.status(HttpStatus.UNAUTHORIZED).build();
        }
        String id = request.pathVariables().get("id");
        if (id != null && !id.matches(ID_PATTERN)) {
            return ServerResponse.badRequest().build();
        }
        WebClient.RequestBodySpec upstream = client.method(request.method())
                .uri(builder -> builder.path(request.path())
                        .queryParams(request.queryParams())
                        .build())
                .header(HttpHeaders.AUTHORIZATION, authorization);
        if (request.headers().contentType().isPresent()) {
            upstream.contentType(request.headers().contentType().get());
        }
        boolean hasBody = request.method() == HttpMethod.POST || request.method() == HttpMethod.PUT;
        Mono<byte[]> body =
                hasBody ? request.bodyToMono(byte[].class).defaultIfEmpty(new byte[0]) : Mono.just(new byte[0]);
        return body.flatMap(bytes -> upstream.bodyValue(bytes).exchangeToMono(response -> {
                    ServerResponse.BodyBuilder result = ServerResponse.status(response.statusCode());
                    response.headers().contentType().ifPresent(result::contentType);
                    return response.bodyToMono(byte[].class)
                            .defaultIfEmpty(new byte[0])
                            .flatMap(result::bodyValue);
                }))
                .onErrorResume(
                        org.springframework.web.reactive.function.client.WebClientRequestException.class,
                        error -> ServerResponse.status(HttpStatus.BAD_GATEWAY).build());
    }
}
