package com.apex.scaffold.infrastructure.remote;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.internal.InternalAuthExchangeFilter;
import com.apex.scaffold.common.internal.InternalAuthProperties;
import com.apex.scaffold.common.internal.InternalServiceSigner;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.contract.dto.internal.OpenAppRegistrationRequest;
import com.apex.scaffold.contract.dto.internal.OpenAppRegistrationResponse;
import com.apex.scaffold.domain.gateway.OpenAppRegistryGateway;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 开放应用注册源远程适配器。
 *
 * <p>默认以内部签名调用 auth 源；网络、签名或业务失败均不写本地副本，也不降级为成功。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Component
public class AuthOpenAppRegistryRemoteAdapter implements OpenAppRegistryGateway {
    private static final String PATH = "/internal/v1/open-apps";
    private final WebClient client;
    private final String baseUrl;

    /**
     * Spring 主构造器（唯一 public 构造器，Spring 按唯一构造器注入，无需 {@code @Autowired}）：
     * 按内部认证配置构建签名 WebClient。
     *
     * @param baseUrl 认证服务基础地址
     * @param properties 内部认证配置
     * @param signer 内部签名器
     * @param builderProvider Boot 管理的客户端构建器提供者
     */
    public AuthOpenAppRegistryRemoteAdapter(
            @Value("${apex.open-app.auth-base-url:http://localhost:8081}") String baseUrl,
            InternalAuthProperties properties,
            InternalServiceSigner signer,
            org.springframework.beans.factory.ObjectProvider<WebClient.Builder> builderProvider) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.client = InternalAuthExchangeFilter.apply(
                        builderProvider.getIfAvailable(WebClient::builder), properties, signer)
                .build();
    }

    /**
     * 校验并规范化认证服务基础地址。
     *
     * @param baseUrl 认证服务基础地址
     * @return 去除末尾斜杠的地址
     */
    private static String normalizeBaseUrl(String baseUrl) {
        java.net.URI endpoint = java.net.URI.create(baseUrl);
        if (endpoint.getHost() == null
                || endpoint.getUserInfo() != null
                || endpoint.getQuery() != null
                || endpoint.getFragment() != null
                || !("http".equals(endpoint.getScheme()) || "https".equals(endpoint.getScheme()))) {
            throw new IllegalArgumentException("Invalid auth registration endpoint");
        }
        return baseUrl.replaceAll("/+$", "");
    }

    @Override
    public Mono<OpenAppRegistrationResponse> create(OpenAppRegistrationRequest request) {
        return call(HttpMethod.POST, PATH, request, responseType());
    }

    @Override
    public Mono<OpenAppRegistrationResponse> update(String clientId, OpenAppRegistrationRequest request) {
        return call(HttpMethod.PUT, path(clientId), request, responseType());
    }

    @Override
    public Mono<OpenAppRegistrationResponse> get(String clientId) {
        return call(HttpMethod.GET, path(clientId), null, responseType());
    }

    @Override
    public Flux<OpenAppRegistrationResponse> list(int page, int size) {
        return call(
                        HttpMethod.GET,
                        PATH + "?page=" + page + "&size=" + size,
                        null,
                        new ParameterizedTypeReference<Result<List<OpenAppRegistrationResponse>>>() {})
                .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Mono<OpenAppRegistrationResponse> rotateSecret(String clientId) {
        return call(HttpMethod.POST, path(clientId) + "/reset-secret", null, responseType());
    }

    @Override
    public Mono<Void> delete(String clientId) {
        return call(HttpMethod.DELETE, path(clientId), null, new ParameterizedTypeReference<Result<Boolean>>() {})
                .then();
    }

    @Override
    public Mono<com.apex.scaffold.contract.dto.internal.OpenAppRegistrationResponse> importApp(
            com.apex.scaffold.contract.dto.internal.OpenAppImportRequest request) {
        return call(HttpMethod.POST, PATH + "/import", request, responseType());
    }

    private <T> Mono<T> call(HttpMethod method, String path, Object body, ParameterizedTypeReference<Result<T>> type) {
        return Mono.deferContextual(context -> {
            String tenant = context.getOrDefault(TenantContext.REACTOR_CONTEXT_KEY, null);
            if (tenant == null || tenant.isBlank()) {
                return Mono.error(new BusinessException(ResultCode.FORBIDDEN));
            }
            WebClient.RequestBodySpec request =
                    client.method(method).uri(baseUrl + path).accept(MediaType.APPLICATION_JSON);
            WebClient.RequestHeadersSpec<?> outgoing = body == null
                    ? request
                    : request.contentType(MediaType.APPLICATION_JSON).bodyValue(body);
            return outgoing.retrieve()
                    .bodyToMono(type)
                    .switchIfEmpty(Mono.error(new IllegalStateException("Empty auth registration response")))
                    .map(result -> {
                        if (!Integer.valueOf(ResultCode.SUCCESS.getCode()).equals(result.getCode())) {
                            throw new BusinessException(result.getCode(), result.getMsg());
                        }
                        if (result.getData() == null) {
                            throw new IllegalStateException("Missing auth registration data");
                        }
                        return result.getData();
                    })
                    .timeout(Duration.ofSeconds(3));
        });
    }

    private static String path(String clientId) {
        if (clientId == null || !clientId.matches("[A-Za-z0-9._-]{1,100}")) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }
        return UriComponentsBuilder.fromPath(PATH)
                .pathSegment(clientId)
                .build()
                .encode()
                .toUriString();
    }

    private static ParameterizedTypeReference<Result<OpenAppRegistrationResponse>> responseType() {
        return new ParameterizedTypeReference<>() {};
    }
}
