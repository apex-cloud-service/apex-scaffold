package com.apex.scaffold.jobservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

class JobProxyTest {
    @Test
    void rejectsMissingCredentialAndUnknownTargets() {
        JobProxy proxy = new JobProxy(WebClient.create("http://localhost:1"));
        WebTestClient client =
                WebTestClient.bindToRouterFunction(proxy.routes()).build();
        client.get().uri("/admin-api/v1/jobs").exchange().expectStatus().isUnauthorized();
        for (String credential : new String[] {"Basic test", "Bearer "}) {
            client.get()
                    .uri("/admin-api/v1/jobs")
                    .header("Authorization", credential)
                    .exchange()
                    .expectStatus()
                    .isUnauthorized();
        }
        client.get()
                .uri("/admin-api/v1/jobs/../users")
                .header("Authorization", "Bearer test")
                .exchange()
                .expectStatus()
                .isNotFound();
        client.get()
                .uri("/admin-api/v1/jobs/abc")
                .header("Authorization", "Bearer test")
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    @Test
    void forwardsOnlyTrustedHeadersAndPreservesUpstreamResponse() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        WebClient upstream = WebClient.builder()
                .baseUrl("http://system.internal:8082")
                .exchangeFunction(request -> {
                    captured.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.FORBIDDEN)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body("{\"code\":403}")
                            .build());
                })
                .build();
        WebTestClient client = WebTestClient.bindToRouterFunction(new JobProxy(upstream).routes())
                .build();
        client.put()
                .uri("/admin-api/v1/jobs/123/status?enabled=true")
                .header(HttpHeaders.AUTHORIZATION, "Bearer signed")
                .header("X-Tenant-Id", "forged")
                .header("X-Apex-Seata-Key", "forged")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"enabled\":true}")
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .isEqualTo("{\"code\":403}");
        assertThat(captured.get().url())
                .isEqualTo(URI.create("http://system.internal:8082/admin-api/v1/jobs/123/status?enabled=true"));
        assertThat(captured.get().headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer signed");
        assertThat(captured.get().headers().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(captured.get().headers()).doesNotContainKeys("X-Tenant-Id", "X-Apex-Seata-Key");
    }

    @Test
    void handlesEmptyBodyAndAllRegisteredRoutes() {
        WebClient upstream = WebClient.builder()
                .baseUrl("http://system.internal")
                .exchangeFunction(request ->
                        Mono.just(ClientResponse.create(HttpStatus.NO_CONTENT).build()))
                .build();
        WebTestClient client = WebTestClient.bindToRouterFunction(new JobProxy(upstream).routes())
                .build();
        for (String path : new String[] {"", "/page", "/1", "/1/logs"}) {
            client.get()
                    .uri("/admin-api/v1/jobs" + path)
                    .header("Authorization", "Bearer signed")
                    .exchange()
                    .expectStatus()
                    .isNoContent();
        }
        client.post()
                .uri("/admin-api/v1/jobs")
                .header("Authorization", "Bearer signed")
                .exchange()
                .expectStatus()
                .isNoContent();
        client.post()
                .uri("/admin-api/v1/jobs/1/run")
                .header("Authorization", "Bearer signed")
                .exchange()
                .expectStatus()
                .isNoContent();
        client.put()
                .uri("/admin-api/v1/jobs/1")
                .header("Authorization", "Bearer signed")
                .exchange()
                .expectStatus()
                .isNoContent();
        client.delete()
                .uri("/admin-api/v1/jobs/1")
                .header("Authorization", "Bearer signed")
                .exchange()
                .expectStatus()
                .isNoContent();
    }

    @Test
    void reportsUnavailableUpstreamAsBadGateway() {
        WebClient upstream = WebClient.builder()
                .baseUrl("http://system.internal")
                .exchangeFunction(request -> Mono.error(new WebClientRequestException(
                        new ConnectException("unavailable"), request.method(), request.url(), request.headers())))
                .build();
        WebTestClient client = WebTestClient.bindToRouterFunction(new JobProxy(upstream).routes())
                .build();
        client.get()
                .uri("/admin-api/v1/jobs")
                .header("Authorization", "Bearer signed")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.BAD_GATEWAY);
    }
}
