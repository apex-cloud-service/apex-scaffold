package com.apex.scaffold.office.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ServerSocket;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import redis.embedded.RedisServer;

class OfficeStateServiceTest {
    private static RedisServer redis;
    private static LettuceConnectionFactory connectionFactory;
    private static ReactiveStringRedisTemplate template;
    private static OfficeStateService service;

    @BeforeAll
    static void startRedis() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        redis = RedisServer.newRedisServer()
                .port(port)
                .bind("127.0.0.1")
                .setting("save \"\"")
                .build();
        redis.start();
        connectionFactory = new LettuceConnectionFactory("127.0.0.1", port);
        connectionFactory.afterPropertiesSet();
        template = new ReactiveStringRedisTemplate(connectionFactory);
        service = new OfficeStateService(template);
    }

    @AfterAll
    static void stopRedis() throws Exception {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        if (redis != null) {
            redis.stop();
        }
    }

    @Test
    void consumesStateOnlyOnceAcrossInstances() {
        OfficeStateService other = new OfficeStateService(template);
        StepVerifier.create(issue().flatMapMany(state -> other.consume(state, "tenant-a", "FEISHU", "https://callback")
                        .concatWith(service.consume(state, "tenant-a", "FEISHU", "https://callback"))))
                .expectNext(true, false)
                .verifyComplete();
    }

    @Test
    void wrongTenantDoesNotDestroyValidState() {
        verifyMismatch("tenant-b", "FEISHU", "https://callback");
    }

    @Test
    void wrongPlatformDoesNotDestroyValidState() {
        verifyMismatch("tenant-a", "DINGTALK", "https://callback");
    }

    @Test
    void wrongRedirectDoesNotDestroyValidState() {
        verifyMismatch("tenant-a", "FEISHU", "https://callback/other");
    }

    @Test
    void expiredStateCannotBeConsumed() {
        StepVerifier.create(service.issue("tenant-a", "FEISHU", "https://callback", Duration.ofMillis(50))
                        .delayElement(Duration.ofMillis(150))
                        .flatMap(state -> service.consume(state, "tenant-a", "FEISHU", "https://callback")))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void concurrentConsumersHaveExactlyOneWinner() {
        StepVerifier.create(issue().flatMap(state -> Flux.range(0, 32)
                        .flatMap(
                                index -> new OfficeStateService(template)
                                        .consume(state, "tenant-a", "FEISHU", "https://callback"),
                                32)
                        .filter(Boolean::booleanValue)
                        .count()))
                .expectNext(1L)
                .verifyComplete();
    }

    @Test
    void emptyRedirectRemainsSupported() {
        StepVerifier.create(service.issue("tenant-a", "FEISHU", "", Duration.ofMinutes(1))
                        .flatMap(state -> service.consume(state, "tenant-a", "FEISHU", "")))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void rejectsInvalidIssueArguments() {
        for (Duration ttl : new Duration[] {null, Duration.ZERO, Duration.ofSeconds(-1), Duration.ofNanos(1)}) {
            StepVerifier.create(service.issue("tenant-a", "FEISHU", "", ttl))
                    .expectError(IllegalArgumentException.class)
                    .verify();
        }
        StepVerifier.create(service.issue("", "FEISHU", "", Duration.ofMinutes(1)))
                .expectError(IllegalArgumentException.class)
                .verify();
        StepVerifier.create(service.issue("tenant-a", "", "", Duration.ofMinutes(1)))
                .expectError(IllegalArgumentException.class)
                .verify();
        StepVerifier.create(service.issue("tenant-a", "FEISHU", null, Duration.ofMinutes(1)))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void missingAndMalformedStatesAreRejected() {
        StepVerifier.create(Flux.concat(
                        service.consume("missing", "tenant-a", "FEISHU", ""),
                        service.consume(null, "tenant-a", "FEISHU", ""),
                        service.consume("", "tenant-a", "FEISHU", ""),
                        service.consume("missing", null, "FEISHU", "")))
                .expectNext(false, false, false, false)
                .verifyComplete();
    }

    @Test
    void issuanceIsLazyAndEachSubscriptionCreatesFreshState() {
        Mono<String> issuance = issue();
        StepVerifier.create(Mono.zip(issuance, issuance))
                .assertNext(pair -> assertThat(pair.getT1()).isNotEqualTo(pair.getT2()))
                .verifyComplete();
    }

    private void verifyMismatch(String tenant, String platform, String redirect) {
        StepVerifier.create(issue().flatMapMany(state -> service.consume(state, tenant, platform, redirect)
                        .concatWith(service.consume(state, "tenant-a", "FEISHU", "https://callback"))))
                .expectNext(false, true)
                .verifyComplete();
    }

    private Mono<String> issue() {
        return service.issue("tenant-a", "FEISHU", "https://callback", Duration.ofMinutes(1));
    }
}
