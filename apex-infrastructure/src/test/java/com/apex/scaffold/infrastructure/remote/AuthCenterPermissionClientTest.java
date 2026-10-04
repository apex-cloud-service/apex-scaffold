package com.apex.scaffold.infrastructure.remote;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.infrastructure.remote.config.AuthCenterPermissionProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

class AuthCenterPermissionClientTest {
    @Test
    void missingMappingMustDenyWithoutCallingHttp() {
        var properties = new AuthCenterPermissionProperties();
        var client = new AuthCenterPermissionClient(
                properties,
                WebClient.builder()
                        .exchangeFunction(request -> {
                            throw new AssertionError("HTTP must not be called");
                        })
                        .build());
        StepVerifier.create(client.query("main-tenant", 7L))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void mappingUsesBothTenantAndUser() {
        var properties = new AuthCenterPermissionProperties();
        properties.setMappings(
                java.util.List.of(new AuthCenterPermissionProperties.Mapping("main", 7L, "center", 99L)));
        assertThat(properties.resolve("main", 7L).centerUserId()).isEqualTo(99L);
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class, () -> properties.resolve("other", 7L));
    }

    @Test
    void mappedIdentityAndBearerAreUsed() {
        AuthCenterPermissionProperties properties = configured();
        WebClient http = WebClient.builder()
                .exchangeFunction(request -> {
                    assertThat(request.url().getPath()).isEqualTo("/auth/v1/authorization/users/99");
                    assertThat(request.headers().getFirst("Authorization")).isEqualTo("Bearer test-token");
                    return reactor.core.publisher.Mono.just(
                            org.springframework.web.reactive.function.client.ClientResponse.create(
                                            org.springframework.http.HttpStatus.OK)
                                    .header("Content-Type", "application/json")
                                    .body(
                                            "{\"code\":0,\"data\":{\"tenantId\":\"center\",\"userId\":99,\"permissions\":[\"user:read\"],\"roles\":[]}}")
                                    .build());
                })
                .build();
        StepVerifier.create(new AuthCenterPermissionClient(properties, http).query("main", 7L))
                .assertNext(data -> {
                    assertThat(data.tenantId()).isEqualTo("main");
                    assertThat(data.userId()).isEqualTo(7L);
                    assertThat(data.permissions()).containsExactly("user:read");
                })
                .verifyComplete();
    }

    @Test
    void wrongResponseTenantMustDeny() {
        WebClient http = WebClient.builder()
                .exchangeFunction(request -> reactor.core.publisher.Mono.just(
                        org.springframework.web.reactive.function.client.ClientResponse.create(
                                        org.springframework.http.HttpStatus.OK)
                                .header("Content-Type", "application/json")
                                .body(
                                        "{\"code\":0,\"data\":{\"tenantId\":\"foreign\",\"userId\":99,\"permissions\":[],\"roles\":[]}}")
                                .build()))
                .build();
        StepVerifier.create(new AuthCenterPermissionClient(configured(), http).query("main", 7L))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void remoteFailureMustPropagate() {
        WebClient http = WebClient.builder()
                .exchangeFunction(
                        request -> reactor.core.publisher.Mono.error(new IllegalStateException("Unavailable")))
                .build();
        StepVerifier.create(new AuthCenterPermissionClient(configured(), http).query("main", 7L))
                .expectError()
                .verify();
    }

    @Test
    void actualHttpTransportDecodesResultAndMapsIdentity() throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        var path = new java.util.concurrent.atomic.AtomicReference<String>();
        var bearer = new java.util.concurrent.atomic.AtomicReference<String>();
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            bearer.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body =
                    "{\"code\":0,\"data\":{\"tenantId\":\"center\",\"userId\":99,\"permissions\":[\"user:read\"],\"roles\":[{\"code\":\"USER\",\"dataScope\":\"SELF\"}]}}"
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            WebClient http = WebClient.builder()
                    .clientConnector(new org.springframework.http.client.reactive.JdkClientHttpConnector())
                    .filter((request, next) ->
                            next.exchange(org.springframework.web.reactive.function.client.ClientRequest.from(request)
                                    .url(java.net.URI.create("http://127.0.0.1:"
                                            + server.getAddress().getPort()
                                            + request.url().getPath()))
                                    .build()))
                    .build();
            StepVerifier.create(new AuthCenterPermissionClient(configured(), http).query("main", 7L))
                    .assertNext(data -> {
                        assertThat(data.tenantId()).isEqualTo("main");
                        assertThat(data.userId()).isEqualTo(7L);
                        assertThat(data.roles().get(0).dataScope()).isEqualTo("SELF");
                    })
                    .verifyComplete();
            assertThat(path.get()).isEqualTo("/auth/v1/authorization/users/99");
            assertThat(bearer.get()).isEqualTo("Bearer test-token");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void timeoutCannotGrantAuthorization() {
        WebClient http = WebClient.builder()
                .exchangeFunction(request -> reactor.core.publisher.Mono.never())
                .build();
        StepVerifier.withVirtualTime(() -> new AuthCenterPermissionClient(configured(), http).query("main", 7L))
                .thenAwait(java.time.Duration.ofSeconds(4))
                .expectError(java.util.concurrent.TimeoutException.class)
                .verify();
    }

    @Test
    void repeatedFailuresOpenCircuitWithoutFallback() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        WebClient http = WebClient.builder()
                .exchangeFunction(request -> {
                    calls.incrementAndGet();
                    return reactor.core.publisher.Mono.error(new IllegalStateException("Unavailable"));
                })
                .build();
        var client = new AuthCenterPermissionClient(configured(), http);
        for (int attempt = 0; attempt < 100; attempt++) {
            StepVerifier.create(client.query("main", 7L)).expectError().verify();
        }
        StepVerifier.create(client.query("main", 7L))
                .expectError(io.github.resilience4j.circuitbreaker.CallNotPermittedException.class)
                .verify();
        assertThat(calls.get()).isEqualTo(100);
    }

    @Test
    void malformedAuthorizationEntriesMustDeny() {
        for (String entries : java.util.List.of(
                "\"permissions\":[\" \"],\"roles\":[]",
                "\"permissions\":[],\"roles\":[{\"code\":\"\",\"dataScope\":\"SELF\"}]",
                "\"permissions\":[],\"roles\":[{\"code\":\"USER\"}]")) {
            WebClient http = WebClient.builder()
                    .exchangeFunction(request -> reactor.core.publisher.Mono.just(
                            org.springframework.web.reactive.function.client.ClientResponse.create(
                                            org.springframework.http.HttpStatus.OK)
                                    .header("Content-Type", "application/json")
                                    .body("{\"code\":0,\"data\":{\"tenantId\":\"center\",\"userId\":99," + entries
                                            + "}}")
                                    .build()))
                    .build();
            StepVerifier.create(new AuthCenterPermissionClient(configured(), http).query("main", 7L))
                    .expectError(IllegalStateException.class)
                    .verify();
        }
    }

    @Test
    void emptyAndMismatchedHttpResponsesMustDeny() {
        for (String body : java.util.List.of(
                "",
                "{\"code\":0,\"data\":null}",
                "{\"code\":0,\"data\":{\"tenantId\":\"center\",\"userId\":100,\"permissions\":[],\"roles\":[]}}",
                "{\"code\":403,\"data\":{\"tenantId\":\"center\",\"userId\":99,\"permissions\":[],\"roles\":[]}}")) {
            WebClient http = WebClient.builder()
                    .exchangeFunction(request -> reactor.core.publisher.Mono.just(
                            org.springframework.web.reactive.function.client.ClientResponse.create(
                                            org.springframework.http.HttpStatus.OK)
                                    .header("Content-Type", "application/json")
                                    .body(body)
                                    .build()))
                    .build();
            StepVerifier.create(new AuthCenterPermissionClient(configured(), http).query("main", 7L))
                    .expectError()
                    .verify();
        }
    }

    private AuthCenterPermissionProperties configured() {
        AuthCenterPermissionProperties properties = new AuthCenterPermissionProperties();
        properties.setBaseUrl("https://center.example");
        properties.setAccessToken("test-token");
        properties.setMappings(
                java.util.List.of(new AuthCenterPermissionProperties.Mapping("main", 7L, "center", 99L)));
        return properties;
    }
}
