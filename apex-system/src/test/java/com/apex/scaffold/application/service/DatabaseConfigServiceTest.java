package com.apex.scaffold.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.application.dto.admin.DatabaseConfigRequest;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class DatabaseConfigServiceTest {
    private final DatabaseConfigService service = new DatabaseConfigService(null);

    @Test
    void shouldGenerateTemplatesWithoutPassword() {
        DatabaseConfigRequest request = request();
        StepVerifier.create(service.generateDeploymentConfig(request))
                .assertNext(result -> {
                    assertThat(result.getYaml())
                            .doesNotContain(request.getPassword())
                            .contains("${DB_PASSWORD}");
                    assertThat(result.getEnv())
                            .doesNotContain(request.getPassword())
                            .contains("DB_PASSWORD=\n");
                    assertThat(result.getDockerCompose())
                            .doesNotContain(request.getPassword())
                            .contains("${DB_PASSWORD:");
                    assertThat(request.toString()).doesNotContain(request.getPassword());
                })
                .verifyComplete();
    }

    @Test
    void shouldRejectCredentialsInConnectionParameters() {
        DatabaseConfigRequest request = request();
        for (String params : new String[] {"password=secret", "%70assword=secret", "useSSL=false&token=secret"}) {
            request.setConnectionParams(params);
            StepVerifier.create(service.generateDeploymentConfig(request))
                    .expectError(IllegalArgumentException.class)
                    .verify();
        }
    }

    @Test
    void shouldNotExposeFactoryExceptionInput() {
        DatabaseConfigRequest request = request();
        request.setDbType("secret-input");
        StepVerifier.create(service.testConnection(request))
                .assertNext(result -> {
                    assertThat(result.getSuccess()).isFalse();
                    assertThat(result.getMessage()).doesNotContain("secret-input", request.getPassword());
                })
                .verifyComplete();
    }

    @Test
    void shouldNotLogCredentialsFromMetadataFailure() throws java.sql.SQLException {
        javax.sql.DataSource dataSource = org.mockito.Mockito.mock(javax.sql.DataSource.class);
        org.mockito.Mockito.when(dataSource.getConnection())
                .thenThrow(new java.sql.SQLException("password=private-db-password"));
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(DatabaseConfigService.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            StepVerifier.create(new DatabaseConfigService(dataSource).getCurrentDatabaseInfo())
                    .assertNext(result -> assertThat(result.getType()).isEqualTo("Unknown"))
                    .verifyComplete();
            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.get(0).getFormattedMessage()).doesNotContain("private-db-password");
            assertThat(appender.list.get(0).getThrowableProxy()).isNull();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private DatabaseConfigRequest request() {
        return DatabaseConfigRequest.builder()
                .dbType("mysql")
                .host("localhost")
                .port(3306)
                .database("apex")
                .username("apex")
                .password("private-db-password")
                .build();
    }
}
