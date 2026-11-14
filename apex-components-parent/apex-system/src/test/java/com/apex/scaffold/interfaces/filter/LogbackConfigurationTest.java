package com.apex.scaffold.interfaces.filter;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.status.Status;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class LogbackConfigurationTest {
    @TempDir
    Path directory;

    @Test
    void loadsBothApplicationsAndProfileCombinations() throws Exception {
        for (String[] profiles : new String[][] {
            {}, {"dev"}, {"it"}, {"test"}, {"prod"}, {"performance"}, {"dev", "performance"}, {"prod", "performance"}
        }) {
            verify(Path.of("src/main/resources/logback-spring.xml"), profiles);
        }
        verify(Path.of("../apex-auth-service/src/main/resources/logback-spring.xml"), new String[] {});
    }

    @Test
    void jsonFilesPreserveServiceIdentityAndRequestFields() throws Exception {
        for (String module : new String[] {
            "apex-system",
            "apex-gateway",
            "apex-auth-service",
            "apex-workflow-service",
            "apex-office-service",
            "apex-file-service"
        }) {
            Path configuration = Path.of("../", module, "src/main/resources/logback-spring.xml");
            for (String profile : new String[] {"dev", "test", "it", "prod"}) {
                LoggerContext context = new LoggerContext();
                context.setMDCAdapter(org.slf4j.MDC.getMDCAdapter());
                Path output = directory.resolve(module + "-" + profile);
                StandardEnvironment environment = new StandardEnvironment();
                environment.setActiveProfiles(profile);
                environment
                        .getPropertySources()
                        .addFirst(new MapPropertySource(
                                "json-test",
                                java.util.Map.of(
                                        "spring.application.name",
                                        module,
                                        "apex.log.dir",
                                        directory.resolve("wrong-path").toString())));
                context.putProperty("LOG_PATH", output.toString());
                try {
                    Class<?> type =
                            Class.forName("org.springframework.boot.logging.logback.SpringBootJoranConfigurator");
                    var constructor = type.getDeclaredConstructor(LoggingInitializationContext.class);
                    constructor.setAccessible(true);
                    JoranConfigurator configurator =
                            (JoranConfigurator) constructor.newInstance(new LoggingInitializationContext(environment));
                    configurator.setContext(context);
                    configurator.doConfigure(configuration.toFile());
                    org.slf4j.MDC.setContextMap(java.util.Map.of(
                            "traceId",
                            "business-id",
                            "tenantId",
                            "tenant-7",
                            "routeId",
                            "route-auth",
                            "status",
                            "201"));
                    context.getLogger("com.apex.scaffold.interfaces.filter.LoggingFilter")
                            .info("request-summary");
                    context.getLogger("com.apex.scaffold.application.BusinessProbe")
                            .info("business-summary");
                } finally {
                    org.slf4j.MDC.clear();
                    context.stop();
                }
                Path jsonFile = output.resolve(module + "-json.log");
                assertThat(jsonFile).exists();
                assertThat(java.nio.file.Files.readString(jsonFile)).contains("business-summary");
                String line = java.nio.file.Files.readAllLines(jsonFile).stream()
                        .filter(value -> value.contains("request-summary"))
                        .findFirst()
                        .orElseThrow();
                var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(line);
                assertThat(json.path("service").asText()).isEqualTo(module);
                assertThat(json.path("serviceName").asText()).isEqualTo(module);
                assertThat(json.path("traceId").asText()).isEqualTo("business-id");
                assertThat(json.path("tenantId").asText()).isEqualTo("tenant-7");
                assertThat(json.path("routeId").asText()).isEqualTo("route-auth");
                assertThat(json.path("status").asText()).isEqualTo("201");
                assertThat(json.path("tid").asText()).isNotEqualTo("business-id");
            }
        }
    }

    @Test
    void saturationDoesNotBlockAndCopiesMdcBeforeDispatch() throws Exception {
        LoggerContext context = new LoggerContext();
        context.setMDCAdapter(org.slf4j.MDC.getMDCAdapter());
        java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<String> trace = new java.util.concurrent.atomic.AtomicReference<>();
        ch.qos.logback.core.AppenderBase<ch.qos.logback.classic.spi.ILoggingEvent> target =
                new ch.qos.logback.core.AppenderBase<>() {
                    @Override
                    protected void append(ch.qos.logback.classic.spi.ILoggingEvent event) {
                        entered.countDown();
                        try {
                            release.await(5, java.util.concurrent.TimeUnit.SECONDS);
                        } catch (InterruptedException error) {
                            Thread.currentThread().interrupt();
                        } finally {
                            trace.compareAndSet(null, event.getMDCPropertyMap().get("traceId"));
                        }
                    }
                };
        target.setContext(context);
        target.setName("blocked-output");
        target.start();
        AsyncAppender async = new AsyncAppender();
        async.setContext(context);
        async.setName("bounded-output");
        async.setQueueSize(2);
        async.setDiscardingThreshold(0);
        async.setNeverBlock(true);
        async.addAppender(target);
        async.start();
        org.slf4j.MDC.put("traceId", "captured-trace");
        try {
            async.doAppend(event(context));
            assertThat(entered.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            org.slf4j.MDC.put("traceId", "changed-trace");
            org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(1), () -> {
                for (int i = 0; i < 10000; i++) {
                    async.doAppend(event(context));
                }
            });
            assertThat(async.getNumberOfElementsInQueue()).isEqualTo(2);
        } finally {
            org.slf4j.MDC.remove("traceId");
            release.countDown();
            async.stop();
            target.stop();
            context.stop();
        }
        assertThat(trace.get()).isEqualTo("captured-trace");
    }

    private ch.qos.logback.classic.spi.LoggingEvent event(LoggerContext context) {
        return new ch.qos.logback.classic.spi.LoggingEvent(
                getClass().getName(),
                context.getLogger("test"),
                ch.qos.logback.classic.Level.ERROR,
                "test event",
                null,
                null);
    }

    private void verify(Path configuration, String[] profiles) throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles(profiles);
        environment
                .getPropertySources()
                .addFirst(new MapPropertySource(
                        "test",
                        java.util.Map.of(
                                "apex.log.dir", directory.toString(), "logging.file.path", directory.toString())));
        LoggerContext context = new LoggerContext();
        context.setName("configuration-test");
        try {
            Class<?> type = Class.forName("org.springframework.boot.logging.logback.SpringBootJoranConfigurator");
            var constructor = type.getDeclaredConstructor(LoggingInitializationContext.class);
            constructor.setAccessible(true);
            JoranConfigurator configurator =
                    (JoranConfigurator) constructor.newInstance(new LoggingInitializationContext(environment));
            configurator.setContext(context);
            configurator.doConfigure(configuration.toFile());
            assertThat(context.getStatusManager().getCopyOfStatusList())
                    .filteredOn(status -> status.getLevel() == Status.ERROR)
                    .isEmpty();
            for (var logger : context.getLoggerList()) {
                var appenders = logger.iteratorForAppenders();
                while (appenders.hasNext()) {
                    var appender = appenders.next();
                    assertThat(appender.isStarted())
                            .as(logger.getName() + ":" + appender.getName())
                            .isTrue();
                    if (appender instanceof AsyncAppender async) {
                        assertThat(async.getQueueSize()).isEqualTo(1024);
                        assertThat(async.isNeverBlock()).isTrue();
                        assertThat(async.isIncludeCallerData()).isFalse();
                        assertThat(async.iteratorForAppenders().hasNext()).isTrue();
                    } else {
                        assertThat(appender.getClass().getName()).contains("LogstashTcpSocketAppender");
                    }
                }
            }
            if (java.util.Arrays.asList(profiles).contains("performance")) {
                assertThat(context.getLogger("com.apex.scaffold.interfaces.filter.LoggingFilter")
                                .getEffectiveLevel())
                        .isEqualTo(ch.qos.logback.classic.Level.OFF);
            }
        } finally {
            context.stop();
        }
    }
}
