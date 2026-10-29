package com.apex.scaffold.infrastructure.remote;

import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.gateway.UnifiedAuthorizationGateway;
import com.apex.scaffold.infrastructure.remote.config.AuthCenterPermissionProperties;
import com.apex.scaffold.infrastructure.remote.config.ManagedWebClientFactory;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 通过受保护中心 API 查询显式映射身份的授权。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Component
public class AuthCenterPermissionClient implements UnifiedAuthorizationGateway {
    private final AuthCenterPermissionProperties properties;
    private final WebClient client;
    private final CircuitBreaker breaker = CircuitBreaker.ofDefaults("auth-center-permission");

    /**
     * 容器装配优先使用专属客户端，否则使用本容器受管连接池。
     *
     * @param properties 认证中心权限客户端配置
     * @param client 专属 WebClient，未装配专属客户端时为 null
     * @param factory 受管 WebClient 工厂，兜底创建连接池客户端
     */
    public AuthCenterPermissionClient(
            AuthCenterPermissionProperties properties,
            @Qualifier("authCenterPermissionWebClient") @Nullable WebClient client,
            ManagedWebClientFactory factory) {
        this.properties = properties;
        this.client =
                java.util.Objects.requireNonNull(client != null ? client : factory.create(), "授权查询需要显式客户端或容器受管工厂");
    }

    @Override
    public Mono<Authorization> query(String tenantId, Long userId) {
        return Mono.defer(() -> {
            AuthCenterPermissionProperties.Mapping mapping = properties.resolve(tenantId, userId);
            String baseUrl = properties.getBaseUrl();
            String token = properties.getAccessToken();
            java.net.URI endpoint = baseUrl == null ? null : java.net.URI.create(baseUrl);
            if (endpoint == null
                    || !"https".equalsIgnoreCase(endpoint.getScheme())
                    || endpoint.getHost() == null
                    || endpoint.getUserInfo() != null
                    || endpoint.getQuery() != null
                    || endpoint.getFragment() != null
                    || token == null
                    || token.isBlank()) {
                return Mono.error(new IllegalStateException("Secure authorization endpoint unavailable"));
            }
            return client.get()
                    .uri(baseUrl + "/auth/v1/authorization/users/" + mapping.centerUserId())
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Result<Authorization>>() {})
                    .switchIfEmpty(Mono.error(new IllegalStateException("Empty authorization response")))
                    .map(result -> {
                        Authorization data = result.getData();
                        if (!validResponse(result, data, mapping) || hasInvalidEntries(data)) {
                            throw new IllegalStateException("Authorization response identity mismatch");
                        }
                        return new Authorization(tenantId, userId, ListCopy.permissions(data), ListCopy.roles(data));
                    })
                    .timeout(Duration.ofSeconds(3))
                    .transformDeferred(CircuitBreakerOperator.of(breaker));
        });
    }

    private boolean validResponse(
            Result<Authorization> result, Authorization data, AuthCenterPermissionProperties.Mapping mapping) {
        return Integer.valueOf(ResultCode.SUCCESS.getCode()).equals(result.getCode())
                && data != null
                && mapping.centerTenantId().equals(data.tenantId())
                && mapping.centerUserId().equals(data.userId())
                && data.permissions() != null
                && data.roles() != null;
    }

    private boolean hasInvalidEntries(Authorization data) {
        return data.permissions().stream().anyMatch(code -> !validCode(code))
                || data.roles().stream().anyMatch(role -> !validRole(role));
    }

    private boolean validRole(Role role) {
        return role != null && validCode(role.code()) && validCode(role.dataScope());
    }

    private boolean validCode(String code) {
        return code != null && !code.isBlank();
    }

    private static final class ListCopy {
        static java.util.List<String> permissions(Authorization data) {
            return java.util.List.copyOf(data.permissions());
        }

        static java.util.List<Role> roles(Authorization data) {
            return java.util.List.copyOf(data.roles());
        }
    }
}
