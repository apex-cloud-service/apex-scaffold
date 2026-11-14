package com.apex.scaffold.seata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.net.URI;
import org.apache.seata.core.context.RootContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

class SeataSafetyTest {
    private SeataProperties properties() {
        SeataProperties properties = new SeataProperties();
        properties.setPropagationSecret("independent-internal-test-secret-123456");
        properties.getAllowedOrigins().add("https://service.internal");
        return properties;
    }

    @Test
    void invalidConfigurationFailsBeforeConnectingToTc() {
        SeataAutoConfiguration configuration = new SeataAutoConfiguration();
        SeataProperties properties = properties();
        org.springframework.mock.env.MockEnvironment environment = new org.springframework.mock.env.MockEnvironment();
        assertThatThrownBy(() -> configuration.seataClientLifecycle(properties, environment))
                .isInstanceOf(IllegalStateException.class);
        properties.setApplicationId("test-service");
        assertThatThrownBy(() -> configuration.seataClientLifecycle(properties, environment))
                .isInstanceOf(IllegalStateException.class);
        properties.setTransactionServiceGroup("test-group");
        properties.setTimeoutMillis(0);
        assertThatThrownBy(() -> configuration.seataClientLifecycle(properties, environment))
                .isInstanceOf(IllegalStateException.class);
        properties.setTimeoutMillis(30000);
        properties.setPropagationSecret(null);
        assertThatThrownBy(() -> configuration.seataClientLifecycle(properties, environment))
                .isInstanceOf(IllegalStateException.class);
        properties.setPropagationSecret("short");
        assertThatThrownBy(() -> configuration.seataClientLifecycle(properties, environment))
                .isInstanceOf(IllegalStateException.class);
        properties.setPropagationSecret(properties().getPropagationSecret());
        properties.setInboundPathPrefix(null);
        assertThatThrownBy(() -> configuration.seataClientLifecycle(properties, environment))
                .isInstanceOf(IllegalStateException.class);
        properties.setInboundPathPrefix("/public/");
        assertThatThrownBy(() -> configuration.seataClientLifecycle(properties, environment))
                .isInstanceOf(IllegalStateException.class);
        properties.setInboundPathPrefix("/internal/seata/");
        properties.getAllowedOrigins().add("http://insecure.internal");
        assertThatThrownBy(() -> configuration.seataClientLifecycle(properties, environment))
                .isInstanceOf(IllegalStateException.class);
        properties.getAllowedOrigins().remove("http://insecure.internal");
        assertThat(configuration.seataClientLifecycle(properties, environment)).isNotNull();
        environment.setProperty("spring.datasource.dynamic.primary", "master");
        assertThatThrownBy(() -> configuration.seataClientLifecycle(properties, environment))
                .hasMessageContaining("单数据源");
        org.springframework.mock.env.MockEnvironment dynamic = new org.springframework.mock.env.MockEnvironment()
                .withProperty("spring.datasource.dynamic.seata", "true");
        assertThatThrownBy(() -> configuration.seataClientLifecycle(properties, dynamic))
                .hasMessageContaining("单数据源");
    }

    @Test
    void rejectsMalformedAndOverlongAuthenticatedXid() {
        SeataProperties properties = properties();
        for (String xid : new String[] {"invalid xid", "x".repeat(129)}) {
            MockServerWebExchange exchange =
                    MockServerWebExchange.from(MockServerHttpRequest.get("/internal/seata/write")
                            .header(SeataXidPropagation.XID_HEADER, xid)
                            .header(SeataXidPropagation.AUTH_HEADER, properties.getPropagationSecret()));
            new SeataXidPropagation(properties)
                    .inbound()
                    .filter(exchange, ignored -> Mono.error(new AssertionError("invalid xid")))
                    .block();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Test
    void noTransactionPassesThroughAndOutboxRemainsUsable() throws Throwable {
        SeataXidPropagation propagation = new SeataXidPropagation(properties());
        propagation
                .inbound()
                .filter(MockServerWebExchange.from(MockServerHttpRequest.get("/api/users")), ignored -> Mono.empty())
                .block();
        ClientRequest request = ClientRequest.create(
                        org.springframework.http.HttpMethod.GET, URI.create("https://service.internal/read"))
                .build();
        propagation
                .outbound()
                .filter(request, actual -> {
                    assertThat(actual.headers()).doesNotContainKey(SeataXidPropagation.XID_HEADER);
                    return Mono.just(ClientResponse.create(HttpStatus.OK).build());
                })
                .block();
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        when(point.proceed()).thenReturn("sync", Mono.just("async"));
        SeataOutboxGuard guard = new SeataOutboxGuard();
        assertThat(guard.guard(point)).isEqualTo("sync");
        assertThat(((Mono<?>) guard.guard(point)).block()).isEqualTo("async");
    }

    @Test
    void disabledCreatesNoClientOrProxy() {
        new ReactiveWebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SeataAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(SeataTransactionExecutor.class));
    }

    @Test
    void rejectsPublicOrUnauthenticatedXid() {
        SeataXidPropagation propagation = new SeataXidPropagation(properties());
        for (String path : new String[] {"/api/users", "/internal/seata/write"}) {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get(path).header(SeataXidPropagation.XID_HEADER, "tc:8091:1"));
            propagation
                    .inbound()
                    .filter(exchange, ignored -> Mono.error(new AssertionError("untrusted")))
                    .block();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Test
    void trustedInboundUsesReactorContextOnly() {
        SeataProperties properties = properties();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/internal/seata/write")
                .header(SeataXidPropagation.XID_HEADER, "tc:8091:1")
                .header(SeataXidPropagation.AUTH_HEADER, properties.getPropagationSecret()));
        new SeataXidPropagation(properties)
                .inbound()
                .filter(
                        exchange,
                        ignored -> Mono.deferContextual(context -> {
                            assertThat(context.get(SeataTransactionExecutor.XID_CONTEXT_KEY)
                                            .toString())
                                    .isEqualTo("tc:8091:1");
                            assertThat(RootContext.getXID()).isNull();
                            return Mono.empty();
                        }))
                .block();
    }

    @Test
    void outboundRemovesSpoofedHeadersAndRestrictsOrigin() {
        SeataXidPropagation propagation = new SeataXidPropagation(properties());
        for (String origin : new String[] {"https://service.internal", "https://public.example"}) {
            ClientRequest request = ClientRequest.create(
                            org.springframework.http.HttpMethod.POST, URI.create(origin + "/write"))
                    .header(SeataXidPropagation.XID_HEADER, "spoofed")
                    .header(SeataXidPropagation.AUTH_HEADER, "spoofed")
                    .build();
            propagation
                    .outbound()
                    .filter(request, actual -> {
                        assertThat(actual.headers().getFirst(SeataXidPropagation.XID_HEADER))
                                .isEqualTo(origin.contains("service.internal") ? "tc:8091:1" : null);
                        return Mono.just(ClientResponse.create(HttpStatus.OK).build());
                    })
                    .contextWrite(context -> context.put(SeataTransactionExecutor.XID_CONTEXT_KEY, "tc:8091:1"))
                    .block();
        }
    }

    @Test
    void outboxRejectsSynchronousAndReactiveTransaction() throws Throwable {
        SeataOutboxGuard guard = new SeataOutboxGuard();
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        RootContext.bind("tc:8091:1");
        try {
            assertThatThrownBy(() -> guard.guard(point)).isInstanceOf(IllegalStateException.class);
            verifyNoInteractions(point);
        } finally {
            RootContext.unbind();
        }
        when(point.proceed()).thenReturn(Mono.just("event"));
        Mono<?> guarded = (Mono<?>) guard.guard(point);
        assertThatThrownBy(() -> guarded.contextWrite(c -> c.put(SeataTransactionExecutor.XID_CONTEXT_KEY, "tc:8091:1"))
                        .block())
                .isInstanceOf(IllegalStateException.class);
    }
}
