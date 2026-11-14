package com.apex.scaffold.knowledge.infrastructure.embedding;

import com.apex.scaffold.knowledge.config.KnowledgeProperties;
import com.apex.scaffold.knowledge.domain.gateway.EmbeddingPort;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * OpenAI 兼容协议向量化适配器。
 *
 * <p>对接 {@code POST {base-url}/embeddings}；云 API（Dashscope/DeepSeek/OpenAI）与
 * 本地推理服务（Ollama/vLLM）只要暴露同形接口即可经配置切换。输出维度与配置
 * {@code dimensions} 不符时显式失败，防止污染索引 embedder。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "apex.knowledge.embedding", name = "enabled", havingValue = "true")
public class OpenAiCompatibleEmbeddingAdapter implements EmbeddingPort {

    private final KnowledgeProperties.Embedding config;

    private final WebClient client;

    /**
     * 构建适配器：基于 Boot 管理的 WebClient 构建器注入基址与鉴权头。
     *
     * @param properties 知识库配置
     * @param builder Boot 管理的 WebClient 构建器
     */
    public OpenAiCompatibleEmbeddingAdapter(KnowledgeProperties properties, WebClient.Builder builder) {
        this.config = properties.getEmbedding();
        this.client = builder.baseUrl(stripTrailingSlash(config.getBaseUrl()))
                .defaultHeader("Authorization", "Bearer " + config.getApiKey())
                .build();
    }

    @Override
    public Mono<List<float[]>> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Mono.just(List.of());
        }
        List<Mono<List<float[]>>> batches = new ArrayList<>();
        for (int start = 0; start < texts.size(); start += config.getBatchSize()) {
            List<String> batch = texts.subList(start, Math.min(start + config.getBatchSize(), texts.size()));
            batches.add(embedBatch(batch));
        }
        return Mono.zip(batches, results -> {
                    List<float[]> merged = new ArrayList<>();
                    for (Object result : results) {
                        merged.addAll((List<float[]>) result);
                    }
                    return merged;
                })
                .doOnNext(vectors -> {
                    if (config.getDimensions() > 0
                            && !vectors.isEmpty()
                            && vectors.get(0).length != config.getDimensions()) {
                        throw new IllegalStateException(
                                "向量维度与配置不符: 实际=" + vectors.get(0).length + ", 配置=" + config.getDimensions());
                    }
                });
    }

    /**
     * 调用单批 embeddings 接口。
     *
     * @param texts 批内文本
     * @return 与输入顺序一致的向量列表
     */
    @SuppressWarnings("unchecked")
    private Mono<List<float[]>> embedBatch(List<String> texts) {
        return client.post()
                .uri("/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(java.util.Map.of("model", config.getModel(), "input", texts))
                .retrieve()
                .bodyToMono(java.util.Map.class)
                .timeout(Duration.ofMillis(config.getTimeout().toMillis()))
                .map(body -> {
                    List<float[]> vectors = new ArrayList<>(texts.size());
                    List<Object> data = (List<Object>) body.get("data");
                    if (data == null || data.size() != texts.size()) {
                        throw new IllegalStateException("embeddings 响应条数与输入不符");
                    }
                    for (Object item : data) {
                        java.util.Map<String, Object> entry = (java.util.Map<String, Object>) item;
                        List<Object> raw = (List<Object>) entry.get("embedding");
                        float[] vector = new float[raw.size()];
                        for (int i = 0; i < raw.size(); i++) {
                            vector[i] = ((Number) raw.get(i)).floatValue();
                        }
                        vectors.add(vector);
                    }
                    return vectors;
                })
                .doOnError(e -> log.error("embeddings 调用失败: model={}, batch={}", config.getModel(), texts.size(), e));
    }

    private static String stripTrailingSlash(String baseUrl) {
        return baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    }
}
