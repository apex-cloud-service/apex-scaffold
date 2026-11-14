package com.apex.scaffold.infrastructure.log;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.log.RuntimeLogEntry;
import com.apex.scaffold.domain.log.RuntimeLogRepository;
import com.apex.scaffold.infrastructure.config.LokiConfig.LokiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Loki日志查询适配器
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        prefix = "loki",
        name = "enabled",
        havingValue = "true")
public class LokiAdapter implements RuntimeLogRepository {

    private final WebClient.Builder webClientBuilder;
    private final LokiProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * 查询 Loki 运行日志。
     *
     * <p>现有 WebClient 同步桥接包含阻塞等待，订阅时在 {@link BlockingIoSchedulers#remote()}
     * 执行请求及解析；请求失败沿用空列表降级。
     *
     * @return 匹配的日志条目
     */
    @Override
    public Mono<List<RuntimeLogEntry>> queryLogs(
            LocalDateTime startTime, LocalDateTime endTime, String level, String logger, String message, int limit) {

        return Mono.fromCallable(() -> {
                    String query = buildLogQLQuery(level, logger, message);
                    long startNano = toNanoTimestamp(startTime);
                    long endNano = toNanoTimestamp(endTime);

                    WebClient webClient =
                            webClientBuilder.baseUrl(properties.getUrl()).build();

                    String response = webClient
                            .get()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/loki/api/v1/query_range")
                                    .queryParam("query", query)
                                    .queryParam("start", startNano)
                                    .queryParam("end", endNano)
                                    .queryParam("limit", limit)
                                    .queryParam("direction", "backward")
                                    .build())
                            .retrieve()
                            .bodyToMono(String.class)
                            .block();

                    return parseLogResponse(response);
                })
                .subscribeOn(BlockingIoSchedulers.remote())
                .doOnError(e -> log.error("Query logs failed", e))
                .onErrorReturn(new ArrayList<>());
    }

    /**
     * 统计 Loki 运行日志数量。
     *
     * <p>订阅时在 {@link BlockingIoSchedulers#remote()} 执行同步远程等待及响应解析，
     * 避免阻塞请求线程；请求失败沿用返回零的降级语义。
     *
     * @return 日志数量
     */
    @Override
    public Mono<Long> countLogs(LocalDateTime startTime, LocalDateTime endTime, String level) {

        return Mono.fromCallable(() -> {
                    String query = buildLogQLQuery(level, null, null);
                    long startNano = toNanoTimestamp(startTime);
                    long endNano = toNanoTimestamp(endTime);

                    WebClient webClient =
                            webClientBuilder.baseUrl(properties.getUrl()).build();

                    String response = webClient
                            .get()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/loki/api/v1/query_range")
                                    .queryParam("query", "count_over_time(" + query + "[1h])")
                                    .queryParam("start", startNano)
                                    .queryParam("end", endNano)
                                    .build())
                            .retrieve()
                            .bodyToMono(String.class)
                            .block();

                    return parseCountResponse(response);
                })
                .subscribeOn(BlockingIoSchedulers.remote())
                .doOnError(e -> log.error("Count logs failed", e))
                .onErrorReturn(0L);
    }

    @SuppressWarnings("PMD.LowerCamelCaseVariableNamingRule")
    private String buildLogQLQuery(String level, String logger, String message) {
        StringBuilder query = new StringBuilder("{job=\"apex-scaffold\"}");

        if (level != null && !level.isEmpty()) {
            query.append(" |= \"").append(level).append("\"");
        }

        if (logger != null && !logger.isEmpty()) {
            query.append(" |= \"").append(logger).append("\"");
        }

        if (message != null && !message.isEmpty()) {
            query.append(" |= \"").append(message).append("\"");
        }

        return query.toString();
    }

    private List<RuntimeLogEntry> parseLogResponse(String response) {
        List<RuntimeLogEntry> entries = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.path("data").path("result");

            if (data.isArray()) {
                for (JsonNode stream : data) {
                    JsonNode values = stream.path("values");
                    if (values.isArray()) {
                        for (JsonNode value : values) {
                            if (value.isArray() && value.size() >= 2) {
                                long timestamp = Long.parseLong(value.get(0).asText());
                                String logLine = value.get(1).asText();
                                entries.add(parseLogLine(timestamp, logLine));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Parse log response failed", e);
        }

        return entries;
    }

    private RuntimeLogEntry parseLogLine(long timestampNano, String logLine) {
        try {
            JsonNode log = objectMapper.readTree(logLine);

            return RuntimeLogEntry.builder()
                    .timestamp(LocalDateTime.ofInstant(
                            java.time.Instant.ofEpochMilli(timestampNano / 1_000_000), ZoneId.systemDefault()))
                    .level(log.path("level").asText())
                    .logger(log.path("logger").asText())
                    .message(log.path("message").asText())
                    .thread(log.path("thread").asText())
                    .exception(log.path("exception").asText(null))
                    .tenantId(log.path("tenantId").asText(null))
                    .userId(log.path("userId").asText(null))
                    .traceId(log.path("traceId").asText(null))
                    .build();
        } catch (Exception e) {
            return RuntimeLogEntry.builder()
                    .timestamp(LocalDateTime.ofInstant(
                            java.time.Instant.ofEpochMilli(timestampNano / 1_000_000), ZoneId.systemDefault()))
                    .message(logLine)
                    .build();
        }
    }

    private Long parseCountResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.path("data").path("result");

            if (data.isArray() && data.size() > 0) {
                JsonNode values = data.get(0).path("values");
                if (values.isArray() && values.size() > 0) {
                    return values.get(0).get(1).asLong();
                }
            }
        } catch (Exception e) {
            log.error("Parse count response failed", e);
        }

        return 0L;
    }

    private long toNanoTimestamp(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() * 1_000_000;
    }
}
