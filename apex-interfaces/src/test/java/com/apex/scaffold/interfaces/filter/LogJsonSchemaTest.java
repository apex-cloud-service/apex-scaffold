package com.apex.scaffold.interfaces.filter;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.encoder.Encoder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.MDC;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * ELK 日志 JSON 契约测试.
 *
 * <p>断言应用经 {@code JSON_FILE_OUTPUT}（LogstashEncoder）输出的结构化日志，
 * 必须包含供 Filebeat/Elasticsearch/Kibana 检索与关联的必需字段：
 * {@code @timestamp、level、logger_name、message、traceId、serviceName}。</p>
 *
 * <p>该契约是任务九「新增 ELK 用于日志/查询」的字段基线：Filebeat 采集
 * {@code *-json.log} 后按这些字段建立索引（{@code traceId/serviceName} 为 keyword 可聚合）。</p>
 *
 * @author Apex Team
 * @since 1.3.1
 */
class LogJsonSchemaTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path directory;

    @Test
    void jsonLogContainsRequiredElkFields() throws Exception {
        LoggerContext context = loadConfiguration("dev");
        try {
            Encoder<ILoggingEvent> encoder = resolveJsonEncoder(context);
            assertThat(encoder).as("JSON_FILE_OUTPUT encoder 必须存在").isNotNull();

            MDC.put("traceId", "test-trace-0001");
            MDC.put("tenantId", "acme-corp");
            try {
                LoggingEvent event = new LoggingEvent(
                        "com.apex.scaffold.interfaces.ElkProbe",
                        context.getLogger("com.apex.scaffold.interfaces.ElkProbe"),
                        ch.qos.logback.classic.Level.INFO,
                        "ELK 契约探测日志",
                        null,
                        null);
                byte[] bytes = encoder.encode(event);
                String json = new String(bytes, StandardCharsets.UTF_8).trim();

                JsonNode node = MAPPER.readTree(json);

                // 必需字段：LogstashEncoder 标准字段
                assertThat(node.has("@timestamp")).as("@timestamp 必须存在").isTrue();
                assertThat(node.path("level").asText()).isEqualTo("INFO");
                assertThat(node.path("logger_name").asText())
                        .isEqualTo("com.apex.scaffold.interfaces.ElkProbe");
                assertThat(node.path("message").asText()).isEqualTo("ELK 契约探测日志");

                // 必需字段：MDC 透传（traceId 供链路关联）
                assertThat(node.path("traceId").asText()).isEqualTo("test-trace-0001");

                // 必需字段：服务标识（供 Kibana 按服务聚合）
                assertThat(node.has("serviceName"))
                        .as("JSON 日志必须包含 serviceName 字段（供 ELK 按服务检索）")
                        .isTrue();
                assertThat(node.path("serviceName").asText()).isEqualTo("apex-scaffold");
            } finally {
                MDC.remove("traceId");
                MDC.remove("tenantId");
            }
        } finally {
            context.stop();
        }
    }

    private LoggerContext loadConfiguration(String profile) throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles(profile);
        environment.getPropertySources().addFirst(new MapPropertySource(
                "test",
                Map.of(
                        "apex.log.dir", directory.toString(),
                        "logging.file.path", directory.toString())));
        LoggerContext context = new LoggerContext();
        context.setName("log-json-schema-test");
        context.setMDCAdapter(MDC.getMDCAdapter());
        Class<?> type = Class.forName("org.springframework.boot.logging.logback.SpringBootJoranConfigurator");
        var constructor = type.getDeclaredConstructor(LoggingInitializationContext.class);
        constructor.setAccessible(true);
        JoranConfigurator configurator =
                (JoranConfigurator) constructor.newInstance(new LoggingInitializationContext(environment));
        configurator.setContext(context);
        configurator.doConfigure(Path.of("src/main/resources/logback-spring.xml").toFile());
        return context;
    }

    @SuppressWarnings("unchecked")
    private Encoder<ILoggingEvent> resolveJsonEncoder(LoggerContext context) {
        for (Logger logger : context.getLoggerList()) {
            Iterator<Appender<ILoggingEvent>> it = logger.iteratorForAppenders();
            while (it.hasNext()) {
                Encoder<ILoggingEvent> found = scan(it.next());
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Encoder<ILoggingEvent> scan(Appender<ILoggingEvent> appender) {
        if ("JSON_FILE_OUTPUT".equals(appender.getName())
                && appender instanceof ch.qos.logback.core.rolling.RollingFileAppender<?> rolling) {
            return (Encoder<ILoggingEvent>) rolling.getEncoder();
        }
        if (appender instanceof ch.qos.logback.classic.AsyncAppender async) {
            Iterator<Appender<ILoggingEvent>> it = async.iteratorForAppenders();
            while (it.hasNext()) {
                Encoder<ILoggingEvent> found = scan(it.next());
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
