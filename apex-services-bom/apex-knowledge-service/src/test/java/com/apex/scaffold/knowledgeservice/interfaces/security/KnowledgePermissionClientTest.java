package com.apex.scaffold.knowledgeservice.interfaces.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.knowledgeservice.interfaces.security.KnowledgePermissionClient.Snapshot;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 授权快照客户端测试：配置拒绝、严格解码与远程身份匹配。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
class KnowledgePermissionClientTest {

    /**
     * 构建客户端与配置。
     *
     * @return 客户端与属性二元组
     */
    private record ClientFixture(KnowledgePermissionClient client, KnowledgePermissionProperties properties) {}

    /**
     * 构建默认（未确认共享命名空间）的客户端。
     *
     * @return fixture
     */
    private ClientFixture fixture() {
        KnowledgePermissionProperties properties = new KnowledgePermissionProperties();
        KnowledgePermissionClient client = new KnowledgePermissionClient(properties, WebClient.builder());
        return new ClientFixture(client, properties);
    }

    /**
     * 构建已确认命名空间但配置不完整的客户端。
     *
     * @param baseUrl 基址
     * @param tokens reader 凭据映射
     * @param timeout 超时
     * @return fixture
     */
    private ClientFixture httpsFixture(String baseUrl, Map<String, String> tokens, Duration timeout) {
        KnowledgePermissionProperties properties = new KnowledgePermissionProperties();
        properties.setSharedUserNamespace(true);
        properties.setBaseUrl(baseUrl);
        properties.setReaderTokens(tokens);
        properties.setTimeout(timeout);
        KnowledgePermissionClient client = new KnowledgePermissionClient(properties, WebClient.builder());
        return new ClientFixture(client, properties);
    }

    /**
     * 断言查询被统一拒绝。
     *
     * @param client 客户端
     * @param user 用户
     */
    private void assertDenied(KnowledgePermissionClient client, AuthenticatedUser user) {
        StepVerifier.create(client.permissions(user))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    void unconfirmedNamespaceIsDenied() {
        ClientFixture fixture = fixture();
        assertDenied(fixture.client(), new AuthenticatedUser(1L, "t1"));
    }

    @Test
    void httpBaseUrlIsDenied() {
        ClientFixture fixture = httpsFixture("http://127.0.0.1:8081", Map.of("t1", "reader"), Duration.ofSeconds(3));
        assertDenied(fixture.client(), new AuthenticatedUser(1L, "t1"));
    }

    @Test
    void missingReaderTokenIsDenied() {
        ClientFixture fixture = httpsFixture("https://127.0.0.1:8081", new HashMap<>(), Duration.ofSeconds(3));
        assertDenied(fixture.client(), new AuthenticatedUser(1L, "tenant-missing"));
    }

    @Test
    void blankReaderTokenIsDenied() {
        ClientFixture fixture = httpsFixture("https://127.0.0.1:8081", Map.of("t1", "  "), Duration.ofSeconds(3));
        assertDenied(fixture.client(), new AuthenticatedUser(1L, "t1"));
    }

    @Test
    void oversizedTimeoutIsDenied() {
        ClientFixture fixture = httpsFixture("https://127.0.0.1:8081", Map.of("t1", "reader"), Duration.ofSeconds(31));
        assertDenied(fixture.client(), new AuthenticatedUser(1L, "t1"));
    }

    @Test
    void zeroTimeoutIsDenied() {
        ClientFixture fixture = httpsFixture("https://127.0.0.1:8081", Map.of("t1", "reader"), Duration.ZERO);
        assertDenied(fixture.client(), new AuthenticatedUser(1L, "t1"));
    }

    @Test
    void invalidUserIdIsDenied() {
        ClientFixture fixture = httpsFixture("https://127.0.0.1:8081", Map.of("t1", "reader"), Duration.ofSeconds(3));
        assertDenied(fixture.client(), new AuthenticatedUser(0L, "t1"));
    }

    @Test
    void blankTenantIsDenied() {
        ClientFixture fixture = httpsFixture("https://127.0.0.1:8081", Map.of("t1", "reader"), Duration.ofSeconds(3));
        assertDenied(fixture.client(), new AuthenticatedUser(1L, "  "));
    }

    @Test
    void malformedBaseUrlIsDenied() {
        ClientFixture fixture =
                httpsFixture("https://user:pass@127.0.0.1", Map.of("t1", "reader"), Duration.ofSeconds(3));
        assertDenied(fixture.client(), new AuthenticatedUser(1L, "t1"));

        ClientFixture withQuery = httpsFixture("https://127.0.0.1?x=1", Map.of("t1", "reader"), Duration.ofSeconds(3));
        assertDenied(withQuery.client(), new AuthenticatedUser(1L, "t1"));
    }

    @Test
    void strictMapperParsesActualAuthorizationPayloadWithRolesAndFutureFields() throws Exception {
        ObjectMapper mapper = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        Result<Snapshot> result = mapper.readValue(authorizationPayload(), new TypeReference<Result<Snapshot>>() {});
        assertThat(result.getCode()).isZero();
        assertThat(result.getData()).isEqualTo(new Snapshot("t1", 1L, List.of("kb:space:read")));
    }

    @Test
    void strictClientDecodesAuthorizationPayloadAndUsesTenantReaderCredential() {
        KnowledgePermissionClient client = responseClient(authorizationPayload());
        StepVerifier.create(client.permissions(new AuthenticatedUser(1L, "t1")))
                .assertNext(permissions -> {
                    assertThat(permissions).containsExactly("kb:space:read");
                    assertThat(permissions).isUnmodifiable();
                })
                .verifyComplete();
    }

    @Test
    void compatiblePayloadStillRejectsInvalidIdentityPermissionsAndBusinessCode() {
        String valid = authorizationPayload();
        for (String body : List.of(
                valid.replace("\"t1\"", "\"other\""),
                valid.replace("\"userId\":1", "\"userId\":2"),
                valid.replace("\"code\":0", "\"code\":500"),
                valid.replace("[\"kb:space:read\"]", "null"),
                valid.replace("[\"kb:space:read\"]", "[null]"),
                valid.replace("[\"kb:space:read\"]", "[\" \" ]"))) {
            assertDenied(responseClient(body), new AuthenticatedUser(1L, "t1"));
        }
    }

    private String authorizationPayload() {
        return """
                {"code":0,"msg":"success","traceId":"trace-test","data":{
                  "tenantId":"t1","userId":1,"permissions":["kb:space:read"],
                  "roles":[{"code":"ADMIN","dataScope":"ALL","futureRoleField":true}],
                  "futureField":{"version":2}
                }}
                """;
    }

    private KnowledgePermissionClient responseClient(String body) {
        ObjectMapper mapper = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(codecs -> codecs.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(mapper)))
                .build();
        KnowledgePermissionProperties properties = new KnowledgePermissionProperties();
        properties.setSharedUserNamespace(true);
        properties.setBaseUrl("https://auth.example.test");
        properties.setReaderTokens(Map.of("t1", "tenant-reader", "other", "other-reader"));
        return new KnowledgePermissionClient(
                properties, WebClient.builder().exchangeStrategies(strategies).exchangeFunction(request -> {
                    assertThat(request.url().getPath()).isEqualTo("/auth/v1/authorization/users/1");
                    assertThat(request.headers().getFirst("Authorization")).isEqualTo("Bearer tenant-reader");
                    return Mono.just(ClientResponse.create(HttpStatus.OK, strategies)
                            .header("Content-Type", "application/json")
                            .body(body)
                            .build());
                }));
    }

    @Test
    void snapshotRecordCarriesFields() {
        Snapshot snapshot = new Snapshot("t1", 1L, List.of("kb:space:read"));
        assertThat(snapshot.tenantId()).isEqualTo("t1");
        assertThat(snapshot.permissions()).containsExactly("kb:space:read");
    }
}
