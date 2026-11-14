package com.apex.scaffold.fileservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.apex.scaffold.common.autoconfigure.OperationAuditAutoConfiguration;
import com.apex.scaffold.file.application.dto.data.ChartRequest;
import com.apex.scaffold.file.application.service.ChartService;
import com.apex.scaffold.fileservice.interfaces.handler.ChartHandler;
import com.apex.scaffold.fileservice.interfaces.util.RequestHandlerUtil;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class OperationAuditWiringTest {
    @Test
    void chartHandlerPreservesOriginalFailureAndAuditsOnce() {
        Logger logger = (Logger) LoggerFactory.getLogger("com.apex.scaffold.common.audit.ReactiveOperationLogAspect");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            var request = MockServerRequest.builder().build();
            var failure = new IllegalArgumentException("private-error");
            RequestHandlerUtil util = mock(RequestHandlerUtil.class);
            when(util.handleRequestBody(request, ChartRequest.class)).thenReturn(Mono.error(failure));
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(AopAutoConfiguration.class, OperationAuditAutoConfiguration.class))
                    .withBean(ChartHandler.class, () -> new ChartHandler(mock(ChartService.class), util))
                    .run(context -> {
                        StepVerifier.create(context.getBean(ChartHandler.class).create(request))
                                .expectErrorMatches(error -> error == failure)
                                .verify();
                        assertThat(appender.list).hasSize(1);
                        assertThat(appender.list.get(0).getFormattedMessage())
                                .contains("ChartHandler.create", "result=ERROR", "IllegalArgumentException")
                                .doesNotContain("private-error", "SUCCESS");
                    });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
