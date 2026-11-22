package com.apex.scaffold.knowledge.infrastructure.embedding;

import com.apex.scaffold.knowledge.config.KnowledgeProperties;
import com.apex.scaffold.knowledge.domain.gateway.EmbeddingPort;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * 本地推理服务（Ollama/vLLM）只要暴露同形接口即可经配置切换。</p>
 *
 * <p>响应校验：按 {@code data[].index} 归位（缺失时回退响应顺序），索引越界/重复、
 * 条目数与输入不符、向量元素非数值或维度与配置 {@code dimensions} 不符时显式失败，
 * 防止污染索引 embedder。批量请求按 {@code batch-size} 切分后按批序拼接结果。</p>
 *
 * @author Apex Team
 * @version 1.3.2
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

    /**
     * 向量化文本列表，结果顺序与输入一致。
     *
     * @param texts 待向量化文本
     * @return 与输入顺序一致的向量列表；空输入直接返回空列表且不发请求
     */
    @Override
    public Mono<List<float[]>> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Mono.just(List.of());
        }
        int batchSize = Math.max(1, config.getBatchSize());
        List<Mono<List<float[]>>> batches = new ArrayList<>();
        for (int start = 0; start < texts.size(); start += batchSize) {
            List<String> batch = texts.subList(start, Math.min(start + batchSize, texts.size()));
            batches.add(embedBatch(batch));
        }
        return Mono.zip(batches, results -> {
                    List<float[]> merged = new ArrayList<>();
                    for (Object result : results) {
                        merged.addAll((List<float[]>) result);
                    }
                    return merged;
                })
                .doOnNext(this::validateDimensions);
    }

    /**
     * 校验每个向量维度与配置一致。
     *
     * @param vectors 全部向量
     * @throws IllegalStateException 任一向量维度与 {@code dimensions} 不符
     */
    private void validateDimensions(List<float[]> vectors) {
        int expected = config.getDimensions();
        if (expected <= 0) {
            return;
        }
        for (int i = 0; i < vectors.size(); i++) {
            int actual = vectors.get(i).length;
            if (actual != expected) {
                throw new IllegalStateException("向量维度与配置不符: 序号=" + i + ", 实际=" + actual + ", 配置=" + expected);
            }
        }
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
                .bodyValue(Map.of("model", config.getModel(), "input", texts))
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofMillis(config.getTimeout().toMillis()))
                .map(body -> parseVectors(texts.size(), (List<Object>) body.get("data")))
                .doOnError(e -> log.error("embeddings 调用失败: model={}, batch={}", config.getModel(), texts.size(), e));
    }

    /**
     * 解析并校验 embeddings 响应条目。
     *
     * <p>条目带 {@code index} 时按其归位；缺失时回退为响应顺序。索引越界、重复、
     * 条目数与输入不符、条目为空或向量元素非数值时抛出异常。</p>
     *
     * @param expectedCount 期望条目数（等于本批输入条数）
     * @param data          响应 {@code data} 数组
     * @return 与输入顺序一致的向量列表
     * @throws IllegalStateException 响应结构或内容不合法
     */
    @SuppressWarnings("unchecked")
    private static List<float[]> parseVectors(int expectedCount, List<Object> data) {
        if (data == null || data.size() != expectedCount) {
            throw new IllegalStateException("embeddings 响应条数与输入不符");
        }
        float[][] ordered = new float[expectedCount][];
        for (int position = 0; position < data.size(); position++) {
            Map<String, Object> entry = (Map<String, Object>) data.get(position);
            if (entry == null) {
                throw new IllegalStateException("embeddings 响应条目为空");
            }
            List<Object> raw = (List<Object>) entry.get("embedding");
            if (raw == null || raw.isEmpty()) {
                throw new IllegalStateException("embeddings 响应条目缺少向量");
            }
            float[] vector = new float[raw.size()];
            for (int i = 0; i < raw.size(); i++) {
                Object value = raw.get(i);
                if (!(value instanceof Number number)) {
                    throw new IllegalStateException("embeddings 向量元素不是数值: 条目=" + position + ", 位置=" + i);
                }
                vector[i] = number.floatValue();
            }
            int slot = position;
            Object indexValue = entry.get("index");
            if (entry.containsKey("index")) {
                if (!(indexValue instanceof Number number)) {
                    throw new IllegalStateException("embeddings 响应 index 不是数值");
                }
                double index = number.doubleValue();
                if (index != Math.rint(index) || index < 0 || index >= expectedCount) {
                    throw new IllegalStateException("embeddings 响应 index 不是有效的批内整数: " + indexValue);
                }
                slot = (int) index;
            }
            if (ordered[slot] != null) {
                throw new IllegalStateException("embeddings 响应 index 重复或与回退顺序冲突: " + slot);
            }
            ordered[slot] = vector;
        }
        List<float[]> vectors = new ArrayList<>(expectedCount);
        for (int i = 0; i < expectedCount; i++) {
            if (ordered[i] == null) {
                throw new IllegalStateException("embeddings 响应缺失 index=" + i + " 的向量");
            }
            vectors.add(ordered[i]);
        }
        return vectors;
    }

    private static String stripTrailingSlash(String baseUrl) {
        return baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    }
}
