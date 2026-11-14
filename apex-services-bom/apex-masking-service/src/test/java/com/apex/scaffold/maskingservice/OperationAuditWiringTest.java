package com.apex.scaffold.maskingservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.apex.scaffold.common.autoconfigure.OperationAuditAutoConfiguration;
import com.apex.scaffold.maskingservice.config.MaskingProperties;
import com.apex.scaffold.maskingservice.interfaces.MaskingHandler;
import jakarta.validation.Validator;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import reactor.test.StepVerifier;

class OperationAuditWiringTest {
    @Test
    void rulesRetainForbiddenWithoutAuthenticationAndAuditOnce() {
        Logger logger = (Logger) LoggerFactory.getLogger("com.apex.scaffold.common.audit.ReactiveOperationLogAspect");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(AopAutoConfiguration.class, OperationAuditAutoConfiguration.class))
                    .withBean(
                            MaskingHandler.class,
                            () -> new MaskingHandler(
                                    new MaskingProperties("issuer", "jwks", "audience", Map.of()),
                                    mock(Validator.class)))
                    .run(context -> {
                        StepVerifier.create(context.getBean(MaskingHandler.class)
                                        .rules(MockServerRequest.builder().build()))
                                .assertNext(response -> assertThat(
                                                response.statusCode().value())
                                        .isEqualTo(403))
                                .verifyComplete();
                        assertThat(appender.list).hasSize(1);
                        assertThat(appender.list.get(0).getFormattedMessage())
                                .contains("MaskingHandler.rules", "HTTP_RESPONSE_READY")
                                .doesNotContain("SUCCESS");
                    });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
