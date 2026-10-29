package com.apex.scaffold.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.apex.scaffold.common.autoconfigure.OperationAuditAutoConfiguration;
import com.apex.scaffold.gateway.fallback.FallbackHandler;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

class OperationAuditWiringTest {
    @Test
    void publicFallbackRetainsServiceUnavailableAndAuditsOnce() {
        Logger logger = (Logger) LoggerFactory.getLogger("com.apex.scaffold.common.audit.ReactiveOperationLogAspect");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(AopAutoConfiguration.class, OperationAuditAutoConfiguration.class))
                    .withBean(FallbackHandler.class, FallbackHandler::new)
                    .run(context -> {
                        var exchange = MockServerWebExchange.from(
                                MockServerHttpRequest.get("/private-path?token=private-token"));
                        StepVerifier.create(
                                        context.getBean(FallbackHandler.class).fallback(exchange))
                                .assertNext(response -> assertThat(
                                                response.statusCode().value())
                                        .isEqualTo(503))
                                .verifyComplete();
                        assertThat(appender.list).hasSize(1);
                        assertThat(appender.list.get(0).getFormattedMessage())
                                .contains("FallbackHandler.fallback", "HTTP_RESPONSE_READY")
                                .doesNotContain("private-", "SUCCESS");
                    });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
