package com.apex.scaffold.workflowservice.interfaces.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.workflow.application.query.WorkflowQueryService;
import com.apex.scaffold.workflow.application.query.WorkflowStatisticsService;
import com.apex.scaffold.workflow.application.service.WorkflowService;
import com.apex.scaffold.workflowservice.infrastructure.event.WorkflowEventPublisher;
import com.apex.scaffold.workflowservice.interfaces.dto.workflow.CompleteTaskRequest;
import com.apex.scaffold.workflowservice.interfaces.handler.WorkflowHandler;
import com.apex.scaffold.workflowservice.interfaces.util.RequestHandlerUtil;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class WorkflowOperationLogAspectTest {
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(WorkflowAuditLogServiceImpl.class);
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AopAutoConfiguration.class))
            .withUserConfiguration(AuditComponents.class)
            .withBean(Probe.class, Probe::new);

    @BeforeEach
    void attachLogCapture() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachLogCapture() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void actualHandlerIsProxiedAndUsesTrustedContextWithoutRecordingSecrets() {
        WorkflowService service = mock(WorkflowService.class);
        WorkflowQueryService queries = mock(WorkflowQueryService.class);
        RequestHandlerUtil util = mock(RequestHandlerUtil.class);
        when(service.completeTask(7L, 12L, "private-comment")).thenReturn(Mono.empty());
        when(queries.getTaskById(7L)).thenReturn(Mono.empty());
        when(util.validateAndThrow(any(CompleteTaskRequest.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));
        contextRunner
                .withBean(
                        WorkflowHandler.class,
                        () -> new WorkflowHandler(
                                service,
                                queries,
                                mock(WorkflowStatisticsService.class),
                                mock(WorkflowEventPublisher.class),
                                util))
                .run(context -> {
                    WorkflowHandler handler = context.getBean(WorkflowHandler.class);
                    assertThat(AopUtils.isAopProxy(handler)).isTrue();
                    CompleteTaskRequest body = new CompleteTaskRequest();
                    body.setComment("private-comment");
                    ServerRequest request = MockServerRequest.builder()
                            .method(org.springframework.http.HttpMethod.POST)
                            .uri(java.net.URI.create("/private-path?secret=private-query"))
                            .pathVariable("taskId", "7")
                            .header("Authorization", "Bearer private-token")
                            .header("X-User-Id", "999")
                            .header("X-Tenant-Id", "forged-tenant")
                            .body(Mono.just(body));
                    Mono<ServerResponse> response = handler.completeTask(request);
                    assertThat(appender.list).isEmpty();
                    StepVerifier.create(trusted(response))
                            .assertNext(value ->
                                    assertThat(value.statusCode().value()).isEqualTo(200))
                            .verifyComplete();
                    assertThat(messages())
                            .singleElement()
                            .asString()
                            .contains(
                                    "WORKFLOW_AUDIT",
                                    "userId=12",
                                    "tenantId=trusted-tenant",
                                    "resource=WorkflowHandler.completeTask",
                                    "result=HTTP_RESPONSE_READY",
                                    "httpMethod=POST",
                                    "httpStatus=200")
                            .doesNotContain("SUCCESS", "private-", "forged-tenant", "999");
                    assertThat(appender.list.get(0).getThrowableProxy()).isNull();
                });
    }

    @Test
    void responseWithBusinessErrorIsOnlyResponseReady() {
        contextRunner.run(context -> {
            StepVerifier.create(trusted(context.getBean(Probe.class).businessError()))
                    .expectNextCount(1)
                    .verifyComplete();
            assertThat(messages())
                    .singleElement()
                    .asString()
                    .contains("result=HTTP_RESPONSE_READY")
                    .doesNotContain("SUCCESS", "private-response");
        });
    }

    @Test
    void asynchronousAndSynchronousErrorsKeepOriginalErrorWithoutMessageOrStack() {
        contextRunner.run(context -> {
            Probe probe = context.getBean(Probe.class);
            StepVerifier.create(trusted(probe.asyncError()))
                    .expectErrorMatches(error -> error == Probe.FAILURE)
                    .verify();
            StepVerifier.create(trusted(probe.syncError()))
                    .expectErrorMatches(error -> error == Probe.FAILURE)
                    .verify();
            assertThat(messages()).hasSize(2).allSatisfy(message -> assertThat(message)
                    .contains("result=ERROR", "errorType=IllegalStateException")
                    .doesNotContain("private-error", "HTTP_RESPONSE_READY"));
            assertThat(appender.list)
                    .allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
        });
    }

    @Test
    void absenceOfTrustedIdentityDoesNotChangeExistingInvocationSemantics() {
        contextRunner.run(context -> {
            StepVerifier.create(context.getBean(Probe.class).businessError())
                    .expectNextCount(1)
                    .verifyComplete();
            assertThat(messages()).isEmpty();
        });
    }

    @Test
    void unannotatedAndEmptyOrCancelledCallsDoNotEmitResponseReady() {
        contextRunner.run(context -> {
            Probe probe = context.getBean(Probe.class);
            StepVerifier.create(trusted(probe.unannotated())).expectNextCount(1).verifyComplete();
            StepVerifier.create(trusted(probe.empty())).verifyComplete();
            StepVerifier.create(trusted(probe.pending())).thenCancel().verify();
            assertThat(messages()).isEmpty();
        });
    }

    @Test
    void saveFlagsSuppressRequestAndResponseMetadata() {
        contextRunner.run(context -> {
            StepVerifier.create(trusted(context.getBean(Probe.class)
                            .metadataDisabled(MockServerRequest.builder().build())))
                    .expectNextCount(1)
                    .verifyComplete();
            assertThat(messages())
                    .singleElement()
                    .asString()
                    .contains("HTTP_RESPONSE_READY")
                    .doesNotContain("httpMethod", "httpStatus");
        });
    }

    private <T> Mono<T> trusted(Mono<T> publisher) {
        return publisher.contextWrite(
                ReactiveSecurityContextHolder.withAuthentication(new UsernamePasswordAuthenticationToken(
                        new AuthenticatedUser(12L, "private-openid", "trusted-tenant"),
                        "private-credentials",
                        List.of())));
    }

    private List<String> messages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(
            basePackageClasses = WorkflowOperationLogAspect.class,
            useDefaultFilters = false,
            includeFilters =
                    @ComponentScan.Filter(
                            type = FilterType.ASSIGNABLE_TYPE,
                            classes = {WorkflowOperationLogAspect.class, WorkflowAuditLogServiceImpl.class}))
    static class AuditComponents {}

    static class Probe {
        static final IllegalStateException FAILURE = new IllegalStateException("private-error");

        @OperationLog(module = "测试")
        public Mono<ServerResponse> businessError() {
            return ServerResponse.ok().bodyValue(Result.error(1, "private-response"));
        }

        @OperationLog(module = "测试")
        public Mono<ServerResponse> asyncError() {
            return Mono.error(FAILURE);
        }

        @OperationLog(module = "测试")
        public Mono<ServerResponse> syncError() {
            throw FAILURE;
        }

        public Mono<ServerResponse> unannotated() {
            return ServerResponse.ok().build();
        }

        @OperationLog(module = "测试")
        public Mono<ServerResponse> empty() {
            return Mono.empty();
        }

        @OperationLog(module = "测试")
        public Mono<ServerResponse> pending() {
            return Mono.never();
        }

        @OperationLog(module = "测试", saveRequest = false, saveResponse = false)
        public Mono<ServerResponse> metadataDisabled(ServerRequest request) {
            return ServerResponse.ok().build();
        }
    }
}
