package com.apex.scaffold.authservice;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.apex.scaffold.common.annotation.OperationLog;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ReactiveOperationAuditTest {
    @Test
    void autoConfiguredConsumerPreservesSignalsAndOnlyLogsWhitelistedMetadata() {
        Class<?>[] configurations = ImportCandidates.load(
                        org.springframework.boot.autoconfigure.AutoConfiguration.class,
                        getClass().getClassLoader())
                .getCandidates()
                .stream()
                .filter(name -> name.startsWith("com.apex.scaffold.common."))
                .map(this::load)
                .toArray(Class<?>[]::new);
        Logger logger = (Logger) LoggerFactory.getLogger("com.apex.scaffold.common.audit.ReactiveOperationLogAspect");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(AopAutoConfiguration.class))
                    .withConfiguration(AutoConfigurations.of(configurations))
                    .withBean(Probe.class, Probe::new)
                    .run(context -> {
                        Probe probe = context.getBean(Probe.class);
                        ServerRequest request = MockServerRequest.builder()
                                .method(HttpMethod.POST)
                                .uri(URI.create("/private-path?secret=private-query"))
                                .header("Authorization", "Bearer private-token")
                                .header("X-User-Id", "private-user")
                                .body("private-body");
                        Mono<ServerResponse> result = probe.response(request);
                        assertThat(appender.list).isEmpty();
                        StepVerifier.create(result)
                                .assertNext(response -> assertThat(
                                                response.statusCode().value())
                                        .isEqualTo(503))
                                .verifyComplete();
                        assertThat(probe.subscriptionCount()).isEqualTo(1);
                        assertThat(appender.list).hasSize(1);
                        assertThat(appender.list.get(0).getFormattedMessage())
                                .contains(
                                        "OPERATION_AUDIT",
                                        "HTTP_RESPONSE_READY",
                                        "httpMethod=POST",
                                        "httpStatus=503",
                                        "module=测试",
                                        "description=元数据",
                                        "Probe.response")
                                .doesNotContain("private-", "SUCCESS");
                        StepVerifier.create(probe.failure(false))
                                .expectErrorMatches(error -> error == Probe.FAILURE)
                                .verify();
                        StepVerifier.create(probe.failure(true))
                                .expectErrorMatches(error -> error == Probe.FAILURE)
                                .verify();
                        assertThat(appender.list).hasSize(3);
                        assertThat(appender.list.subList(1, 3)).allSatisfy(event -> {
                            assertThat(event.getFormattedMessage())
                                    .contains("result=ERROR", "IllegalStateException")
                                    .doesNotContain("private-", "HTTP_RESPONSE_READY");
                            assertThat(event.getThrowableProxy()).isNull();
                        });
                        StepVerifier.create(probe.empty()).verifyComplete();
                        StepVerifier.create(probe.pending()).thenCancel().verify();
                        StepVerifier.create(probe.unannotated())
                                .expectNextCount(1)
                                .verifyComplete();
                        assertThat(appender.list).hasSize(3);
                        StepVerifier.create(probe.disabled(request))
                                .expectNextCount(1)
                                .verifyComplete();
                        assertThat(appender.list.get(3).getFormattedMessage())
                                .doesNotContain("httpMethod", "httpStatus");
                        assertThat(context.getBeansOfType(
                                        com.apex.scaffold.common.audit.ReactiveOperationLogAspect.class))
                                .hasSize(1);
                        ServerRequest unavailableMetadata = org.mockito.Mockito.mock(ServerRequest.class);
                        org.mockito.Mockito.when(unavailableMetadata.method())
                                .thenThrow(new IllegalStateException("private-metadata"));
                        StepVerifier.create(probe.response(unavailableMetadata))
                                .expectNextCount(1)
                                .verifyComplete();
                        StepVerifier.create(probe.failureWithRequest(unavailableMetadata))
                                .expectErrorMatches(error -> error == Probe.FAILURE)
                                .verify();
                        assertThat(appender.list).hasSize(4);
                    });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private Class<?> load(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException error) {
            throw new IllegalStateException(error);
        }
    }

    static class Probe {
        static final IllegalStateException FAILURE = new IllegalStateException("private-error");
        final AtomicInteger subscriptions = new AtomicInteger();

        public int subscriptionCount() {
            return subscriptions.get();
        }

        @OperationLog(module = "测试", description = "元数据")
        public Mono<ServerResponse> response(ServerRequest request) {
            return Mono.defer(() -> {
                subscriptions.incrementAndGet();
                return ServerResponse.status(503).bodyValue("private-response");
            });
        }

        @OperationLog
        public Mono<ServerResponse> failure(boolean synchronous) {
            if (synchronous) {
                throw FAILURE;
            }
            return Mono.error(FAILURE);
        }

        @OperationLog
        public Mono<ServerResponse> failureWithRequest(ServerRequest request) {
            return Mono.error(FAILURE);
        }

        @OperationLog
        public Mono<ServerResponse> empty() {
            return Mono.empty();
        }

        @OperationLog
        public Mono<ServerResponse> pending() {
            return Mono.never();
        }

        public Mono<ServerResponse> unannotated() {
            return ServerResponse.ok().build();
        }

        @OperationLog(saveRequest = false, saveResponse = false)
        public Mono<ServerResponse> disabled(ServerRequest request) {
            return ServerResponse.ok().build();
        }
    }
}
