package com.apex.scaffold.authservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.apex.scaffold.auth.application.service.InternalAuthService;
import com.apex.scaffold.authservice.interfaces.handler.InternalAuthHandler;
import com.apex.scaffold.authservice.interfaces.util.RequestHandlerUtil;
import com.apex.scaffold.common.autoconfigure.OperationAuditAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import reactor.test.StepVerifier;

class OperationAuditWiringTest {
    @Test
    void internalHandlerRetainsBadRequestWithoutAuthenticationAndAuditsOnce() {
        Logger logger = (Logger) LoggerFactory.getLogger("com.apex.scaffold.common.audit.ReactiveOperationLogAspect");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(AopAutoConfiguration.class, OperationAuditAutoConfiguration.class))
                    .withBean(
                            InternalAuthHandler.class,
                            () -> new InternalAuthHandler(
                                    mock(InternalAuthService.class), mock(RequestHandlerUtil.class)))
                    .run(context -> {
                        var request = MockServerRequest.builder()
                                .pathVariable("accountId", "0")
                                .build();
                        StepVerifier.create(context.getBean(InternalAuthHandler.class)
                                        .getUserPermissions(request))
                                .assertNext(response -> assertThat(
                                                response.statusCode().value())
                                        .isEqualTo(400))
                                .verifyComplete();
                        assertThat(appender.list).hasSize(1);
                        assertThat(appender.list.get(0).getFormattedMessage())
                                .contains("InternalAuthHandler.getUserPermissions", "HTTP_RESPONSE_READY")
                                .doesNotContain("SUCCESS");
                    });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
