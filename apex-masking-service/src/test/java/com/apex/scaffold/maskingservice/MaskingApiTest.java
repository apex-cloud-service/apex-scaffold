package com.apex.scaffold.maskingservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.reactive.server.WebTestClient;

/** 脱敏服务鉴权、租户与错误响应集成测试。 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
            "apex.masking.issuer=https://issuer.example",
            "apex.masking.jwk-set-uri=https://issuer.example/jwks",
            "apex.masking.audience=masking",
            "apex.masking.versions.v1.tenants[0]=tenant-a",
            "apex.masking.versions.v1.fields.phone.type=PHONE"
        })
@AutoConfigureWebTestClient
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class MaskingApiTest {
    private final WebTestClient client;

    MaskingApiTest(WebTestClient client) {
        this.client = client;
    }

    @Test
    void exposesTypedMaskingOpenApi() {
        authorized("tenant-a")
                .get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.paths['/api/v1/masking/batch'].post.operationId")
                .isEqualTo("maskingBatch")
                .jsonPath("$.paths['/api/v1/masking/rules'].get.operationId")
                .isEqualTo("maskingRules")
                .jsonPath(
                        "$.paths['/api/v1/masking/batch'].post.requestBody.content['application/json'].schema['$ref']")
                .isEqualTo("#/components/schemas/MaskingRequest")
                .jsonPath("$.components.schemas.MaskingBatchResult.properties.data['$ref']")
                .isEqualTo("#/components/schemas/MaskingBatchData")
                .jsonPath(
                        "$.components.schemas.MaskingRulesResult.properties.data.additionalProperties.additionalProperties['$ref']")
                .isEqualTo("#/components/schemas/Rule");
    }

    @Test
    void requiresAuthenticationAndScope() {
        client.post().uri("/api/v1/masking/batch").exchange().expectStatus().isUnauthorized();
        client.mutateWith(mockJwt())
                .post()
                .uri("/api/v1/masking/batch")
                .exchange()
                .expectStatus()
                .isForbidden();
    }

    @Test
    void masksOnlyForSignedTenantAndConfiguredVersion() {
        authorized("tenant-a")
                .post()
                .uri("/api/v1/masking/batch")
                .bodyValue("{\"version\":\"v1\",\"documents\":[{\"phone\":\"13800138000\"}]}")
                .header("Content-Type", "application/json")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.documents[0].phone")
                .isEqualTo("138****8000");
        authorized("tenant-b")
                .post()
                .uri("/api/v1/masking/batch")
                .bodyValue("{\"version\":\"v1\",\"documents\":[{}]}")
                .header("Content-Type", "application/json")
                .header("X-Tenant-Id", "tenant-a")
                .exchange()
                .expectStatus()
                .isForbidden();
        authorized(null)
                .get()
                .uri("/api/v1/masking/rules")
                .exchange()
                .expectStatus()
                .isForbidden();
    }

    @Test
    void invalidRequestNeverEchoesInput() {
        authorized("tenant-a")
                .post()
                .uri("/api/v1/masking/batch")
                .header("Content-Type", "application/json")
                .bodyValue("{\"version\":\"secret-value\",\"documents\":[]}")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody(String.class)
                .value(body -> assertThat(body).doesNotContain("secret-value"));
    }

    @Test
    void clientCannotOverrideRulesAndUnknownVersionIsDenied() {
        authorized("tenant-a")
                .post()
                .uri("/api/v1/masking/batch")
                .header("Content-Type", "application/json")
                .bodyValue("{\"version\":\"v1\",\"documents\":[{}],\"rules\":\"secret-value\"}")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody(String.class)
                .value(body -> assertThat(body).doesNotContain("secret-value"));
        authorized("tenant-a")
                .post()
                .uri("/api/v1/masking/batch")
                .header("Content-Type", "application/json")
                .bodyValue("{\"version\":\"unknown\",\"documents\":[{}]}")
                .exchange()
                .expectStatus()
                .isForbidden();
    }

    @Test
    void listsOnlyAuthorizedVersionsAndRejectsOversizedBody() {
        authorized("tenant-a")
                .get()
                .uri("/api/v1/masking/rules")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.v1.phone.type")
                .isEqualTo("PHONE");
        authorized("tenant-b")
                .get()
                .uri("/api/v1/masking/rules")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.v1")
                .doesNotExist();
        authorized("tenant-a")
                .post()
                .uri("/api/v1/masking/batch")
                .header("Content-Type", "application/json")
                .bodyValue("{\"version\":\"v1\",\"documents\":[{\"phone\":\"" + "x".repeat(270000) + "\"}]}")
                .exchange()
                .expectStatus()
                .isEqualTo(413);
    }

    private WebTestClient authorized(String tenant) {
        return client.mutateWith(mockJwt()
                .jwt(jwt -> {
                    if (tenant != null) jwt.claim("tenant_id", tenant);
                })
                .authorities(
                        new SimpleGrantedAuthority("SCOPE_masking:apply"),
                        new SimpleGrantedAuthority("SCOPE_masking:rules")));
    }
}
