package com.apex.scaffold.auth.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStreamCommands;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStreamOperations;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

class UnifiedAuditServiceTest {
    @Test
    void protectsImmutableDetailsAndBoundsRedisPayload() {
        ReactiveRedisTemplate<String, String> redis = mock(ReactiveRedisTemplate.class);
        ReactiveStreamOperations<String, Object, Object> streams = mock(ReactiveStreamOperations.class);
        when(redis.opsForStream()).thenReturn(streams);
        when(streams.add(anyString(), anyMap(), any(RedisStreamCommands.XAddOptions.class)))
                .thenReturn(Mono.just(RecordId.of("1-0")));
        UnifiedAuditService service = new UnifiedAuditService(redis, new ObjectMapper().findAndRegisterModules());
        Map<String, Object> details = Map.of("password", "private-password", "resource", "resource-value");
        StepVerifier.create(service.logAuthorizationEvent("CHECK", "user", "tenant", "resource", true, details))
                .assertNext(log -> {
                    assertThat(log.getDetails())
                            .containsEntry("password", "***")
                            .containsEntry("resourceId", "resource");
                    assertThat(log.getDetails().toString()).doesNotContain("private-password");
                })
                .verifyComplete();
        assertThat(details).hasSize(2).containsEntry("password", "private-password");
        StepVerifier.create(service.logAuthenticationEvent(
                        "x".repeat(100000), "user", "tenant", "ip", false, "secret", details))
                .assertNext(log -> {
                    assertThat(log.getAction()).hasSize(64);
                    assertThat(log.getErrorMessage()).isEqualTo("[omitted]");
                })
                .verifyComplete();
    }

    @Test
    void rejectsSaturationAndReleasesAdmissionOnCancellationAndFailure() {
        ReactiveRedisTemplate<String, String> redis = mock(ReactiveRedisTemplate.class);
        ReactiveStreamOperations<String, Object, Object> streams = mock(ReactiveStreamOperations.class);
        when(redis.opsForStream()).thenReturn(streams);
        Sinks.One<RecordId> pending = Sinks.one();
        when(streams.add(anyString(), anyMap(), any(RedisStreamCommands.XAddOptions.class)))
                .thenReturn(pending.asMono());
        UnifiedAuditService service = new UnifiedAuditService(redis, new ObjectMapper().findAndRegisterModules());
        ArrayList<Disposable> subscriptions = new ArrayList<>();
        try {
            for (int i = 0; i < 256; i++) {
                subscriptions.add(service.logSystemEvent("CHECK", "system", true, Map.of())
                        .subscribe());
            }
            StepVerifier.create(service.logSystemEvent("CHECK", "system", true, Map.of()))
                    .verifyComplete();
            assertThat(service.getDroppedCount()).isEqualTo(1);
            subscriptions.get(0).dispose();
            when(streams.add(anyString(), anyMap(), any(RedisStreamCommands.XAddOptions.class)))
                    .thenReturn(Mono.error(new IllegalStateException("secret")));
            StepVerifier.create(service.logSystemEvent("CHECK", "system", true, Map.of()))
                    .verifyComplete();
            assertThat(service.getFailedCount()).isEqualTo(1);
            when(streams.add(anyString(), anyMap(), any(RedisStreamCommands.XAddOptions.class)))
                    .thenReturn(Mono.just(RecordId.of("1-0")));
            StepVerifier.create(service.logSystemEvent("CHECK", "system", true, Map.of()))
                    .expectNextCount(1)
                    .verifyComplete();
            assertThat(service.getDroppedCount()).isEqualTo(1);
        } finally {
            subscriptions.forEach(Disposable::dispose);
        }
    }

    @Test
    void usesTraceFromEachSubscriptionContext() {
        ReactiveRedisTemplate<String, String> redis = mock(ReactiveRedisTemplate.class);
        ReactiveStreamOperations<String, Object, Object> streams = mock(ReactiveStreamOperations.class);
        when(redis.opsForStream()).thenReturn(streams);
        when(streams.add(anyString(), anyMap(), any(RedisStreamCommands.XAddOptions.class)))
                .thenReturn(Mono.just(RecordId.of("1-0")));
        UnifiedAuditService service = new UnifiedAuditService(redis, new ObjectMapper().findAndRegisterModules());
        Mono<UnifiedAuditService.AuditLog> event = service.logSystemEvent("CHECK", "system", true, Map.of());
        StepVerifier.create(event.contextWrite(context -> context.put("traceId", "request-a")))
                .assertNext(log -> assertThat(log.getTraceId()).isEqualTo("request-a"))
                .verifyComplete();
        StepVerifier.create(event.contextWrite(context -> context.put("traceId", "b".repeat(100))))
                .assertNext(log -> assertThat(log.getTraceId()).isEqualTo("b".repeat(64)))
                .verifyComplete();
    }

    @Test
    void boundsSecurityAlertsAndRestoresAdmissionAfterCancellation() {
        ReactiveRedisTemplate<String, String> redis = mock(ReactiveRedisTemplate.class);
        ReactiveStreamOperations<String, Object, Object> streams = mock(ReactiveStreamOperations.class);
        when(redis.opsForStream()).thenReturn(streams);
        Sinks.One<RecordId> pending = Sinks.one();
        when(streams.add(anyString(), anyMap(), any(RedisStreamCommands.XAddOptions.class)))
                .thenAnswer(invocation -> invocation.<String>getArgument(0).contains("alert")
                        ? pending.asMono()
                        : Mono.just(RecordId.of("1-0")));
        UnifiedAuditService service = new UnifiedAuditService(redis, new ObjectMapper().findAndRegisterModules());
        ArrayList<Disposable> subscriptions = new ArrayList<>();
        try {
            for (int i = 0; i < 64; i++) {
                subscriptions.add(service.logSecurityEvent("CHECK", "user", "tenant", "ip", "HIGH", null, Map.of())
                        .subscribe());
            }
            StepVerifier.create(service.logSecurityEvent("CHECK", "user", "tenant", "ip", "HIGH", null, Map.of()))
                    .expectNextCount(1)
                    .verifyComplete();
            assertThat(service.getDroppedCount()).isEqualTo(1);
            subscriptions.get(0).dispose();
            when(streams.add(anyString(), anyMap(), any(RedisStreamCommands.XAddOptions.class)))
                    .thenReturn(Mono.just(RecordId.of("1-0")));
            StepVerifier.create(service.logSecurityEvent("CHECK", "user", "tenant", "ip", "HIGH", null, Map.of()))
                    .expectNextCount(1)
                    .verifyComplete();
            assertThat(service.getDroppedCount()).isEqualTo(1);
        } finally {
            subscriptions.forEach(Disposable::dispose);
        }
    }

    @Test
    void timesOutHangingAuditWritesAndRestoresAdmission() {
        ReactiveRedisTemplate<String, String> redis = mock(ReactiveRedisTemplate.class);
        ReactiveStreamOperations<String, Object, Object> streams = mock(ReactiveStreamOperations.class);
        when(redis.opsForStream()).thenReturn(streams);
        when(streams.add(anyString(), anyMap(), any(RedisStreamCommands.XAddOptions.class)))
                .thenReturn(Mono.never());
        UnifiedAuditService service = new UnifiedAuditService(redis, new ObjectMapper().findAndRegisterModules());
        StepVerifier.withVirtualTime(() -> service.logSystemEvent("CHECK", "system", true, Map.of()))
                .expectSubscription()
                .thenAwait(java.time.Duration.ofSeconds(4))
                .verifyComplete();
        assertThat(service.getFailedCount()).isEqualTo(1);
        when(streams.add(anyString(), anyMap(), any(RedisStreamCommands.XAddOptions.class)))
                .thenReturn(Mono.just(RecordId.of("1-0")));
        StepVerifier.create(service.logSystemEvent("CHECK", "system", true, Map.of()))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void timesOutSecurityAlertsWithoutLosingAuditResult() {
        ReactiveRedisTemplate<String, String> redis = mock(ReactiveRedisTemplate.class);
        ReactiveStreamOperations<String, Object, Object> streams = mock(ReactiveStreamOperations.class);
        when(redis.opsForStream()).thenReturn(streams);
        when(streams.add(anyString(), anyMap(), any(RedisStreamCommands.XAddOptions.class)))
                .thenAnswer(invocation -> invocation.<String>getArgument(0).contains("alert")
                        ? Mono.never()
                        : Mono.just(RecordId.of("1-0")));
        UnifiedAuditService service = new UnifiedAuditService(redis, new ObjectMapper().findAndRegisterModules());
        StepVerifier.withVirtualTime(
                        () -> service.logSecurityEvent("CHECK", "user", "tenant", "ip", "HIGH", null, Map.of()))
                .expectSubscription()
                .thenAwait(java.time.Duration.ofSeconds(4))
                .expectNextCount(1)
                .verifyComplete();
        assertThat(service.getFailedCount()).isEqualTo(1);
    }
}
