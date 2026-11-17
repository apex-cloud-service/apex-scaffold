package com.apex.scaffold.integrationservice.interfaces.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 集成服务权限快照客户端测试：配置校验、身份匹配与失败拒绝。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@DisplayName("集成服务权限快照客户端")
class IntegrationPermissionClientTest {

    private IntegrationPermissionProperties properties;

    private final AuthenticatedUser user = new AuthenticatedUser(7L, null, "t-1");

    @BeforeEach
    void setUp() {
        properties = new IntegrationPermissionProperties();
        properties.setSharedUserNamespace(true);
        properties.setBaseUrl("https://auth.example.com");
        properties.setTimeout(Duration.ofSeconds(2));
        properties.setReaderTokens(Map.of("t-1", "reader-token"));
    }

    @Test
    void strictMapperAcceptsAuthRolesAndUnknownFutureFields() throws Exception {
        ObjectMapper mapper = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        var snapshot = mapper.readValue(
                """
                {"tenantId":"t-1","userId":7,"permissions":["iot:device:read"],
                 "roles":[{"code":"ADMIN","dataScope":"ALL"}],"futureField":{"version":2}}
                """,
                IntegrationPermissionClient.Snapshot.class);
        assertThat(snapshot)
                .isEqualTo(new IntegrationPermissionClient.Snapshot("t-1", 7L, java.util.List.of("iot:device:read")));
    }

    @Test
    @DisplayName("共享命名空间未确认时拒绝")
    void shouldDenyWhenNamespaceNotShared() {
        properties.setSharedUserNamespace(false);
        IntegrationPermissionClient client = client(exchange -> Mono.empty());

        StepVerifier.create(client.permissions(user))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    @DisplayName("非 HTTPS 基址时拒绝")
    void shouldDenyWhenBaseUrlNotHttps() {
        properties.setBaseUrl("http://auth.example.com");
        IntegrationPermissionClient client = client(exchange -> Mono.empty());

        StepVerifier.create(client.permissions(user))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    @DisplayName("缺少租户 reader 凭据时拒绝")
    void shouldDenyWhenTokenMissing() {
        properties.setReaderTokens(Map.of());
        IntegrationPermissionClient client = client(exchange -> Mono.empty());

        StepVerifier.create(client.permissions(user))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    @DisplayName("超时超过三十秒上限时拒绝")
    void shouldDenyWhenTimeoutTooLong() {
        properties.setTimeout(Duration.ofSeconds(31));
        IntegrationPermissionClient client = client(exchange -> Mono.empty());

        StepVerifier.create(client.permissions(user))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    @DisplayName("身份匹配的成功快照返回权限码")
    void shouldReturnPermissionsForMatchingIdentity() {
        IntegrationPermissionClient client = client(exchange -> Mono.just(jsonResponse(
                "{\"code\":0,\"data\":{\"tenantId\":\"t-1\",\"userId\":7,\"permissions\":[\"iot:device:read\"]}}")));

        StepVerifier.create(client.permissions(user))
                .assertNext(permissions -> assertThat(permissions).containsExactly("iot:device:read"))
                .verifyComplete();
    }

    @Test
    @DisplayName("租户不匹配的快照拒绝")
    void shouldDenyWhenTenantMismatch() {
        IntegrationPermissionClient client = client(exchange -> Mono.just(
                jsonResponse("{\"code\":0,\"data\":{\"tenantId\":\"other\",\"userId\":7,\"permissions\":[]}}")));

        StepVerifier.create(client.permissions(user))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    @DisplayName("业务码非成功时拒绝")
    void shouldDenyWhenResultCodeNotSuccess() {
        IntegrationPermissionClient client = client(exchange -> Mono.just(
                jsonResponse("{\"code\":500,\"data\":{\"tenantId\":\"t-1\",\"userId\":7,\"permissions\":[]}}")));

        StepVerifier.create(client.permissions(user))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    @DisplayName("权限码含空白时拒绝")
    void shouldDenyWhenPermissionCodeBlank() {
        IntegrationPermissionClient client = client(exchange -> Mono.just(
                jsonResponse("{\"code\":0,\"data\":{\"tenantId\":\"t-1\",\"userId\":7,\"permissions\":[\" \"]}}")));

        StepVerifier.create(client.permissions(user))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    @DisplayName("HTTP 错误时统一拒绝")
    void shouldDenyOnHttpError() {
        IntegrationPermissionClient client = client(exchange -> Mono.just(
                ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).build()));

        StepVerifier.create(client.permissions(user))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    private IntegrationPermissionClient client(ExchangeFunction function) {
        return new IntegrationPermissionClient(properties, WebClient.builder().exchangeFunction(function));
    }

    private ClientResponse jsonResponse(String body) {
        return ClientResponse.create(HttpStatus.OK)
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }
}
