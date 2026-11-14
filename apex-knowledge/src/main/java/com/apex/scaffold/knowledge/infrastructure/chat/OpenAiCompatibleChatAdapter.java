package com.apex.scaffold.knowledge.infrastructure.chat;

import com.apex.scaffold.knowledge.config.KnowledgeProperties;
import com.apex.scaffold.knowledge.domain.gateway.ChatPort;
import com.apex.scaffold.knowledge.domain.model.ChatMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * OpenAI 兼容协议流式对话适配器。
 *
 * <p>对接 {@code POST {base-url}/chat/completions}（{@code stream=true}），
 * 以 SSE 分帧解析增量 {@code choices[0].delta.content}；{@code [DONE]} 结束流。
 * 云 API 与本地推理服务（Ollama/vLLM）经配置切换。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "apex.knowledge.chat", name = "enabled", havingValue = "true")
public class OpenAiCompatibleChatAdapter implements ChatPort {

    private static final String DONE_MARK = "[DONE]";

    private final KnowledgeProperties.Chat config;

    private final WebClient client;

    private final ObjectMapper objectMapper;

    /**
     * 构建适配器。
     *
     * @param properties 知识库配置
     * @param builder Boot 管理的 WebClient 构建器
     * @param objectMapper JSON 解析器
     */
    public OpenAiCompatibleChatAdapter(
            KnowledgeProperties properties, WebClient.Builder builder, ObjectMapper objectMapper) {
        this.config = properties.getChat();
        this.objectMapper = objectMapper;
        this.client = builder.baseUrl(stripTrailingSlash(config.getBaseUrl()))
                .defaultHeader("Authorization", "Bearer " + config.getApiKey())
                .build();
    }

    @Override
    public Flux<String> streamChat(String systemPrompt, List<ChatMessage> history, String question) {
        List<Map<String, String>> messages = new java.util.ArrayList<>();
        messages.add(Map.of("role", ChatMessage.ROLE_SYSTEM, "content", systemPrompt));
        for (ChatMessage message : history) {
            messages.add(Map.of("role", message.role(), "content", message.content()));
        }
        messages.add(Map.of("role", ChatMessage.ROLE_USER, "content", question));
        Map<String, Object> body = Map.of("model", config.getModel(), "messages", messages, "stream", true);
        return client.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .timeout(Duration.ofMillis(config.getTimeout().toMillis()))
                .takeUntil(event -> DONE_MARK.equals(event.data()))
                // mapNotNull：非增量帧（role/usage/[DONE]）返回 null 由该操作符过滤
                .mapNotNull(this::extractDelta)
                .filter(delta -> !delta.isEmpty())
                .doOnError(e -> log.error("chat/completions 流式调用失败: model={}", config.getModel(), e));
    }

    /**
     * 从 SSE 帧提取增量文本；非增量帧（如 usage）返回 null 由 filter 过滤。
     *
     * @param event SSE 帧
     * @return 增量文本；无增量时为 null
     */
    private String extractDelta(ServerSentEvent<String> event) {
        String data = event.data();
        if (data == null || DONE_MARK.equals(data)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(data);
            JsonNode delta = root.path("choices").path(0).path("delta").path("content");
            return delta.isMissingNode() || delta.isNull() ? null : delta.asText();
        } catch (Exception e) {
            log.debug("忽略无法解析的 SSE 帧");
            return null;
        }
    }

    /**
     * 兜底空流保护：错误在订阅时传播，确保调用方收到明确失败信号。
     *
     * @return 错误保护后的流
     */
    Mono<Void> validateConfig() {
        return config.isEnabled() && !config.getBaseUrl().isBlank()
                ? Mono.empty()
                : Mono.error(new IllegalStateException("chat 供应商配置无效"));
    }

    private static String stripTrailingSlash(String baseUrl) {
        return baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    }
}
