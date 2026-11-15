package com.apex.scaffold.knowledge.infrastructure.search;

import com.apex.scaffold.knowledge.config.KnowledgeProperties;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeVectorIndexPort;
import com.apex.scaffold.knowledge.domain.model.IndexedChunk;
import com.apex.scaffold.knowledge.domain.model.VectorSearchHit;
import com.apex.scaffold.knowledge.domain.model.VectorSearchQuery;
import com.apex.scaffold.knowledge.domain.model.VectorSearchResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meilisearch.sdk.Client;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Meilisearch 知识分块索引适配器。
 *
 * <p>创建、设置、写入、删除与hybrid检索均走REST，写操作取得taskUid后有界轮询成功确认。
 * 删除严格使用POST documents/delete JSON filter，租户值验证与转义，不调用delete-all。
 * SDK客户端保留原装配构造边界；REST参数避免SDK 0.17.1未完整暴露的hybrid参数。
 * 检索带查询向量失败时最多降级一次词法检索，不把写入失败转换为成功。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "apex.knowledge.meilisearch", name = "enabled", havingValue = "true")
public class MeilisearchKnowledgeAdapter implements KnowledgeVectorIndexPort {

    private static final String INDEX_SUFFIX = "kb_chunks";

    private static final String EMBEDDER_NAME = "default";

    private final KnowledgeProperties.Meilisearch config;

    private final ObjectMapper objectMapper;

    private final WebClient restClient;

    /** 进程内索引就绪标记；失败时复位以便重试。 */
    private final AtomicBoolean indexEnsured = new AtomicBoolean(false);

    /**
     * 构建适配器：SDK 客户端来自 {@code KnowledgeMeilisearchConfiguration}，
     * REST 直调客户端复用同一地址与密钥。
     *
     * @param client Meilisearch SDK 客户端
     * @param properties 知识库配置
     * @param builder Boot 管理的 WebClient 构建器
     * @param objectMapper JSON 序列化器
     */
    public MeilisearchKnowledgeAdapter(
            Client client, KnowledgeProperties properties, WebClient.Builder builder, ObjectMapper objectMapper) {
        this.config = properties.getMeilisearch();
        this.objectMapper = objectMapper;
        this.restClient = builder.baseUrl(stripTrailingSlash(config.getUrl()))
                .defaultHeader("Authorization", "Bearer " + config.getApiKey())
                .build();
    }

    @Override
    public Mono<Void> ensureIndex(boolean embeddingEnabled, int dimensions) {
        return Mono.defer(() -> {
            if (indexEnsured.get()) {
                return Mono.empty();
            }
            return submit(
                            restClient
                                    .post()
                                    .uri("/indexes")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .bodyValue(Map.of("uid", indexName(), "primaryKey", "id")),
                            "index_already_exists")
                    .then(patchSettings(embeddingEnabled, dimensions))
                    .doOnSuccess(v -> indexEnsured.set(true))
                    .doOnError(e -> indexEnsured.set(false));
        });
    }

    @Override
    public Mono<Void> upsertChunks(List<IndexedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return Mono.empty();
        }
        return Flux.fromIterable(partition(chunks, config.getBatchSize()))
                .concatMap(this::upsertBatch)
                .then();
    }

    @Override
    public Mono<Void> deleteDocumentChunks(String tenantId, Long documentId) {
        return deleteDocumentChunks(config.getUrl(), indexName(), tenantId, documentId);
    }

    @Override
    public Mono<Void> deleteDocumentChunks(String endpoint, String index, String tenantId, Long documentId) {
        return Mono.defer(() -> {
            requireTarget(endpoint, index);
            requireDocumentId(documentId);
            String filter = "tenantId = " + quotedTenant(tenantId) + " AND documentId = " + documentId;
            return submit(
                    restClient
                            .post()
                            .uri("/indexes/{index}/documents/delete", index)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Map.of("filter", filter)),
                    "index_not_found");
        });
    }

    @Override
    public Mono<VectorSearchResult> search(VectorSearchQuery query) {
        return searchOnce(query, true).onErrorResume(error -> query.getQueryEmbedding() != null, error -> {
            log.info("hybrid 检索失败，降级词法检索: {}", error.getMessage());
            return searchOnce(withoutEmbedding(query), false);
        });
    }

    @Override
    public Mono<Long> rebuild(List<Long> ids, Function<List<Long>, Mono<List<IndexedChunk>>> chunkLoader) {
        if (ids == null || ids.isEmpty()) {
            return Mono.just(0L);
        }
        return chunkLoader.apply(ids).flatMap(chunks -> {
            if (chunks.isEmpty()) {
                return Mono.just(0L);
            }
            return Flux.fromIterable(partition(chunks, config.getBatchSize()))
                    .concatMap(this::upsertBatch)
                    .then(Mono.just((long) chunks.size()));
        });
    }

    /**
     * 执行一次检索请求；{@code allowHybrid} 为 false 时剥离向量与 hybrid 参数。
     *
     * @param query 检索条件
     * @param allowHybrid 是否允许携带向量
     * @return 检索结果
     */
    private Mono<VectorSearchResult> searchOnce(VectorSearchQuery query, boolean allowHybrid) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("q", query.getKeyword() == null ? "" : query.getKeyword());
        body.put("offset", query.getOffset());
        body.put("limit", query.getLimit());
        body.put("filter", buildFilter(query));
        body.put("attributesToHighlight", List.of("content"));
        body.put("highlightPreTag", "<em>");
        body.put("highlightPostTag", "</em>");
        boolean hybrid = allowHybrid && query.getQueryEmbedding() != null;
        if (hybrid) {
            body.put("vector", query.getQueryEmbedding());
            body.put("hybrid", Map.of("embedder", EMBEDDER_NAME, "semanticRatio", query.getSemanticRatio()));
        }
        return restClient
                .post()
                .uri("/indexes/{index}/search", indexName())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseSearchResponse)
                .map(result -> {
                    result.setHybridUsed(hybrid);
                    return result;
                });
    }

    /**
     * 组装租户强制 + 空间可选过滤表达式。
     *
     * @param query 检索条件
     * @return Meilisearch filter 表达式
     */
    private String buildFilter(VectorSearchQuery query) {
        StringBuilder filter = new StringBuilder("tenantId = " + quotedTenant(query.getTenantId()));
        if (query.getSpaceIds() != null && !query.getSpaceIds().isEmpty()) {
            List<String> spaceFilters =
                    query.getSpaceIds().stream().map(id -> "spaceId = " + id).toList();
            filter.append(" AND (").append(String.join(" OR ", spaceFilters)).append(")");
        }
        return filter.toString();
    }

    /**
     * 解析检索响应为领域结果。
     *
     * @param body 响应 JSON
     * @return 领域检索结果
     */
    private VectorSearchResult parseSearchResponse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode hits = root.path("hits");
            List<VectorSearchHit> items = new ArrayList<>();
            for (JsonNode hit : hits) {
                items.add(VectorSearchHit.builder()
                        .chunkId(hit.path("chunkId").asLong())
                        .documentId(hit.path("documentId").asLong())
                        .spaceId(hit.path("spaceId").asLong())
                        .documentTitle(hit.path("documentTitle").asText(""))
                        .spaceName(hit.path("spaceName").asText(""))
                        .content(hit.path("content").asText(""))
                        .highlight(hit.path("_formatted").path("content").asText(""))
                        .score(hit.path("_rankingScore").asDouble(0.0d))
                        .build());
            }
            return new VectorSearchResult(items, root.path("estimatedTotalHits").asLong(items.size()), false);
        } catch (Exception e) {
            throw new IllegalStateException("知识检索响应解析失败", e);
        }
    }

    /**
     * PATCH 索引设置：过滤字段、可搜索字段与 embedder（关闭时清空）。
     *
     * @param embeddingEnabled 是否配置向量 embedder
     * @param dimensions 向量维度
     * @return 完成信号
     */
    private Mono<Void> patchSettings(boolean embeddingEnabled, int dimensions) {
        Map<String, Object> settings = new HashMap<>();
        settings.put("filterableAttributes", List.of("tenantId", "spaceId", "documentId"));
        settings.put("searchableAttributes", List.of("content", "documentTitle", "spaceName"));
        if (embeddingEnabled) {
            settings.put(
                    "embedders", Map.of(EMBEDDER_NAME, Map.of("source", "userProvided", "dimensions", dimensions)));
        } else {
            settings.put("embedders", Map.of());
        }
        return submit(
                restClient
                        .patch()
                        .uri("/indexes/{index}/settings", indexName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(settings),
                null);
    }

    /**
     * 单批REST写入并取得taskUid，等待成功确认；网络超时不转换为成功。
     *
     * @param batch 分块批次
     * @return 完成信号
     */
    private Mono<Void> upsertBatch(List<IndexedChunk> batch) {
        return Mono.defer(() -> {
            for (IndexedChunk chunk : batch) {
                quotedTenant(chunk.getTenantId());
                requireDocumentId(chunk.getDocumentId());
                requireDocumentId(chunk.getChunkId());
            }
            return submit(
                    restClient
                            .post()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/indexes/{index}/documents")
                                    .queryParam("primaryKey", "id")
                                    .build(indexName()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(batch.stream().map(this::toDocument).toList()),
                    null);
        });
    }

    /**
     * 分块转索引文档；带向量时写入 userProvided 向量字段。
     *
     * @param chunk 分块
     * @return 索引文档结构
     */
    private Map<String, Object> toDocument(IndexedChunk chunk) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", String.valueOf(chunk.getChunkId()));
        doc.put("chunkId", chunk.getChunkId());
        doc.put("documentId", chunk.getDocumentId());
        doc.put("spaceId", chunk.getSpaceId());
        doc.put("tenantId", chunk.getTenantId());
        doc.put("seq", chunk.getSeq());
        doc.put("content", chunk.getContent());
        doc.put("contentHash", chunk.getContentHash());
        doc.put("documentTitle", chunk.getDocumentTitle());
        doc.put("spaceName", chunk.getSpaceName());
        doc.put("contentType", chunk.getContentType());
        if (chunk.getEmbedding() != null && chunk.getEmbedding().length > 0) {
            doc.put("_vectors", Map.of(EMBEDDER_NAME, Map.of("embeddings", List.of(chunk.getEmbedding()))));
        }
        return doc;
    }

    /**
     * 取得合法taskUid后有界确认；仅明确允许的幂等错误码忽略，通用错误不得吞掉。
     *
     * @param request 待提交REST写请求
     * @param allowedCode 操作专属幂等码，创建允许index_already_exists，删除允许index_not_found
     * @return 任务成功确认或明确幂等缺失/已存在时完成
     */
    private Mono<Void> submit(WebClient.RequestHeadersSpec<?> request, String allowedCode) {
        return request.exchangeToMono(response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> {
                            JsonNode node = readJson(body);
                            if (!response.statusCode().is2xxSuccessful()) {
                                String code = node.path("code").asText();
                                if (allowedCode != null && allowedCode.equals(code)) {
                                    return Mono.<Long>empty();
                                }
                                return Mono.error(new IllegalStateException("Meilisearch 请求失败: status="
                                        + response.statusCode().value() + ", code=" + code));
                            }
                            JsonNode uid = node.get("taskUid");
                            if (uid == null || !uid.isIntegralNumber() || !uid.canConvertToLong() || uid.asLong() < 0) {
                                return Mono.error(new IllegalStateException("Meilisearch 缺少有效taskUid"));
                            }
                            return Mono.just(uid.asLong());
                        }))
                .timeout(config.getRequestTimeout())
                .flatMap(uid -> pollTask(uid, 0, allowedCode).timeout(config.getTaskTimeout()))
                .then();
    }

    private Mono<Void> pollTask(long uid, int poll, String allowedCode) {
        return restClient
                .get()
                .uri("/tasks/{uid}", uid)
                .exchangeToMono(response -> {
                    if (!response.statusCode().is2xxSuccessful()) {
                        return response.releaseBody()
                                .then(Mono.<JsonNode>error(
                                        new IllegalStateException("Meilisearch task状态无法确认: taskUid=" + uid)));
                    }
                    return response.bodyToMono(String.class)
                            .switchIfEmpty(Mono.error(new IllegalStateException("Meilisearch task状态响应为空")))
                            .map(this::readJson);
                })
                .timeout(config.getRequestTimeout())
                .flatMap(task -> {
                    if (!task.path("uid").isIntegralNumber() || task.path("uid").asLong(-1) != uid) {
                        return Mono.error(new IllegalStateException("Meilisearch taskUid不匹配"));
                    }
                    String status = task.path("status").asText();
                    if ("succeeded".equals(status)) {
                        return Mono.empty();
                    }
                    if ("failed".equals(status)
                            && allowedCode != null
                            && allowedCode.equals(
                                    task.path("error").path("code").asText())) {
                        return Mono.empty();
                    }
                    if ("enqueued".equals(status) || "processing".equals(status)) {
                        if (poll + 1 >= config.getTaskMaxPolls()) {
                            return Mono.error(new IllegalStateException("Meilisearch task轮询上限: taskUid=" + uid));
                        }
                        return Mono.delay(config.getTaskPollInterval()).then(pollTask(uid, poll + 1, allowedCode));
                    }
                    return Mono.error(
                            new IllegalStateException("Meilisearch task未成功: taskUid=" + uid + ", status=" + status));
                });
    }

    private JsonNode readJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception error) {
            throw new IllegalStateException("Meilisearch 响应格式无效", error);
        }
    }

    private String quotedTenant(String tenantId) {
        if (tenantId == null
                || tenantId.isBlank()
                || tenantId.length() > 64
                || tenantId.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("可信租户ID无效");
        }
        return "\"" + tenantId.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private void requireDocumentId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("文档或分块ID无效");
        }
    }

    private void requireTarget(String endpoint, String index) {
        if (!stripTrailingSlash(config.getUrl()).equals(stripTrailingSlash(endpoint))
                || index == null
                || !index.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalStateException("历史索引目标与当前配置不兼容，需恢复原目标配置");
        }
    }

    /**
     * 构造仅词法检索的查询副本。
     *
     * @param query 原查询
     * @return 去除向量的副本
     */
    private VectorSearchQuery withoutEmbedding(VectorSearchQuery query) {
        return VectorSearchQuery.builder()
                .keyword(query.getKeyword())
                .queryEmbedding(null)
                .tenantId(query.getTenantId())
                .spaceIds(query.getSpaceIds())
                .offset(query.getOffset())
                .limit(query.getLimit())
                .semanticRatio(query.getSemanticRatio())
                .build();
    }

    /**
     * 按批大小切分列表。
     *
     * @param items 全量列表
     * @param size 批大小
     * @param <T> 元素类型
     * @return 批次列表
     */
    private <T> List<List<T>> partition(List<T> items, int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("索引批大小必须为正整数");
        }
        List<List<T>> batches = new ArrayList<>();
        for (int start = 0; start < items.size(); start += size) {
            batches.add(items.subList(start, Math.min(start + size, items.size())));
        }
        return batches;
    }

    private String indexName() {
        return config.getIndexPrefix() + INDEX_SUFFIX;
    }

    private static String stripTrailingSlash(String url) {
        return url == null ? "" : url.replaceAll("/+$", "");
    }
}
