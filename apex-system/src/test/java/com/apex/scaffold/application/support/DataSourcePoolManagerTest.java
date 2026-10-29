package com.apex.scaffold.application.support;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.apex.scaffold.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import reactor.test.StepVerifier;

class DataSourcePoolManagerTest {
    @Test
    void connectionFailureDoesNotExposeCredentialsInResponseOrLog() {
        Logger logger = (Logger) LoggerFactory.getLogger(DataSourcePoolManager.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            StepVerifier.create(new DataSourcePoolManager(2)
                            .testConnection("jdbc:mysql://127.0.0.1:1/demo", "user", "private-secret", 1))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(BusinessException.class);
                        assertThat(error.getMessage()).doesNotContain("private-secret", "jdbc:", "user");
                        assertThat(error.getCause()).isNull();
                    })
                    .verify();
            assertThat(appender.list).isNotEmpty();
            appender.list.forEach(event -> {
                assertThat(event.getFormattedMessage()).doesNotContain("private-secret", "jdbc:");
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void unsupportedProtocolIsRejectedBeforeConnecting() {
        StepVerifier.create(new DataSourcePoolManager(2)
                        .testConnection("jdbc:unknown:database?password=private-secret", "user", "private-secret", 1))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BusinessException.class);
                    assertThat(error.getMessage()).doesNotContain("private-secret", "jdbc:");
                })
                .verify();
    }
}
