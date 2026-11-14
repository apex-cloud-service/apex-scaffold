package com.apex.scaffold.knowledge.infrastructure.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.apex.scaffold.knowledge.config.KnowledgeProperties;
import com.apex.scaffold.knowledge.domain.model.IndexedChunk;
import com.apex.scaffold.knowledge.domain.model.VectorSearchQuery;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.exceptions.MeilisearchException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Meilisearch 知识索引适配器测试：SDK 写入、REST 检索/设置/删除与降级链。
 *
 * <p>SDK Client 以 Mockito 模拟，REST 调用经 MockWebServer 扮演
 * Meilisearch HTTP 端点（与生产共用同一 base-url 配置）。
 *
 * @author Apex Team
 * @since 1.3.2
 */
class MeilisearchKnowledgeAdapterTest {

    private MockWebServer server;

    private Client client;

    private Index index;

    private KnowledgeProperties properties;

    private MeilisearchKnowledgeAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        client = mock(Client.class);
        index = mock(Index.class);
        properties = new KnowledgeProperties();
        properties.getMeilisearch().setEnabled(true);
        properties.getMeilisearch().setUrl(server.url("/").toString());
        properties.getMeilisearch().setApiKey("master-key");
        properties.getEmbedding().setDimensions(4);
        adapter = new MeilisearchKnowledgeAdapter(client, properties, WebClient.builder(), new ObjectMapper());
        lenient().when(client.index(anyString())).thenReturn(index);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    /**
     * 构建带向量的索引分块。
     *
     * @param withVector 是否带向量
     * @return 分块
     */
    private IndexedChunk chunk(boolean withVector) {
        return IndexedChunk.builder()
                .chunkId(101L)
                .documentId(9L)
                .spaceId(2L)
                .tenantId("t1")
                .seq(0)
                .content("分块正文")
                .contentHash("hash-1")
                .documentTitle("文档标题")
                .spaceName("空间")
                .contentType("application/pdf")
                .embedding(withVector ? new float[] {0.1f, 0.2f, 0.3f, 0.4f} : null)
                .updateTime(LocalDateTime.now())
                .build();
    }

    @Test
    void ensureIndexCreatesIndexAndPatchesSettings() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"taskUid\":1,\"status\":\"enqueued\"}"));

        StepVerifier.create(adapter.ensureIndex(true, 4)).verifyComplete();

        verify(client).createIndex("apex_kb_chunks", "id");
        String recorded = server.takeRequest().getBody().readUtf8();
        assertThat(recorded)
                .contains("\"filterableAttributes\"")
                .contains("userProvided")
                .contains("\"dimensions\":4");
        // 二次调用走进程内幂等短路，不再发 PATCH
        StepVerifier.create(adapter.ensureIndex(true, 4)).verifyComplete();
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void ensureIndexTreatsExistingIndexAsIdempotent() throws Exception {
        doThrow(new MeilisearchException("index_already_exists")).when(client).createIndex("apex_kb_chunks", "id");
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"taskUid\":1,\"status\":\"enqueued\"}"));

        StepVerifier.create(adapter.ensureIndex(false, 4)).verifyComplete();
        assertThat(server.takeRequest().getBody().readUtf8()).contains("\"embedders\":{}");
    }

    @Test
    void upsertWritesDocumentsWithVectorsAndPrimaryKey() throws Exception {
        adapter.upsertChunks(List.of(chunk(true))).block();

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(index).addDocuments(json.capture(), org.mockito.ArgumentMatchers.eq("id"));
        JsonNode doc = new ObjectMapper().readTree(json.getValue()).get(0);
        assertThat(doc.get("id").asText()).isEqualTo("101");
        assertThat(doc.get("tenantId").asText()).isEqualTo("t1");
        assertThat(doc.get("_vectors")
                        .get("default")
                        .get("embeddings")
                        .get(0)
                        .get(0)
                        .asDouble())
                .isEqualTo(0.1);
    }

    @Test
    void upsertWithoutEmbeddingOmitsVectorField() throws Exception {
        adapter.upsertChunks(List.of(chunk(false))).block();

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(index).addDocuments(json.capture(), org.mockito.ArgumentMatchers.eq("id"));
        JsonNode doc = new ObjectMapper().readTree(json.getValue()).get(0);
        assertThat(doc.has("_vectors")).isFalse();
    }

    @Test
    void hybridSearchParsesHitsAndUsesVector() throws Exception {
        server.enqueue(
                new MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                                """
                        {"hits":[{"id":"101","chunkId":101,"documentId":9,"spaceId":2,"tenantId":"t1",
                                  "documentTitle":"文档标题","spaceName":"空间","content":"正文",
                                  "_rankingScore":0.87,"_formatted":{"content":"<em>正文</em>"}}],
                         "estimatedTotalHits":1}"""));

        VectorSearchQuery query = VectorSearchQuery.builder()
                .keyword("正文")
                .queryEmbedding(new float[] {0.1f, 0.2f, 0.3f, 0.4f})
                .tenantId("t1")
                .spaceIds(List.of(2L))
                .offset(0)
                .limit(5)
                .semanticRatio(0.6)
                .build();

        StepVerifier.create(adapter.search(query))
                .assertNext(result -> {
                    assertThat(result.isHybridUsed()).isTrue();
                    assertThat(result.getTotal()).isEqualTo(1L);
                    assertThat(result.getHits().getFirst().getHighlight()).isEqualTo("<em>正文</em>");
                    assertThat(result.getHits().getFirst().getScore()).isEqualTo(0.87d);
                })
                .verifyComplete();

        String recorded = server.takeRequest().getBody().readUtf8();
        assertThat(recorded)
                .contains("\"hybrid\"")
                .contains("\"semanticRatio\":0.6")
                .contains("spaceId = 2");
    }

    @Test
    void hybridFailureFallsBackToLexicalSearch() throws Exception {
        // 第一次（hybrid）服务端报错，第二次（词法）成功
        server.enqueue(new MockResponse().setResponseCode(400).setBody("{\"message\":\"invalid embedder\"}"));
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"hits\":[],\"estimatedTotalHits\":0}"));

        VectorSearchQuery query = VectorSearchQuery.builder()
                .keyword("正文")
                .queryEmbedding(new float[] {0.1f, 0.2f, 0.3f, 0.4f})
                .tenantId("t1")
                .offset(0)
                .limit(5)
                .semanticRatio(0.6)
                .build();

        StepVerifier.create(adapter.search(query))
                .assertNext(result -> {
                    assertThat(result.isHybridUsed()).isFalse();
                    assertThat(result.getHits()).isEmpty();
                })
                .verifyComplete();
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    void deleteByFilterScopesTenantAndDocument() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"taskUid\":2,\"status\":\"enqueued\"}"));

        StepVerifier.create(adapter.deleteDocumentChunks("t1", 9L)).verifyComplete();

        String path =
                java.net.URLDecoder.decode(server.takeRequest().getPath(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(path).contains("filter=tenantId = 't1' AND documentId = 9");
    }

    @Test
    void rebuildRewritesChunksFromLoader() throws Exception {
        Function<List<Long>, Mono<List<IndexedChunk>>> loader = ids -> Mono.just(List.of(chunk(false)));

        StepVerifier.create(adapter.rebuild(List.of(101L), loader))
                .expectNext(1L)
                .verifyComplete();
        verify(index).addDocuments(anyString(), org.mockito.ArgumentMatchers.eq("id"));
    }

    @Test
    void rebuildWithEmptyIdsIsNoop() throws Exception {
        Function<List<Long>, Mono<List<IndexedChunk>>> loader = ids -> Mono.just(List.of());
        StepVerifier.create(adapter.rebuild(List.of(), loader)).expectNext(0L).verifyComplete();
    }
}
