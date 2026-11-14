package com.apex.scaffold.knowledge.infrastructure.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apex.scaffold.knowledge.config.KnowledgeProperties;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

/**
 * OpenAI 兼容向量化适配器测试（MockWebServer 模拟供应商端点）。
 *
 * @author Apex Team
 * @since 1.3.2
 */
class OpenAiCompatibleEmbeddingAdapterTest {

    private MockWebServer server;

    private OpenAiCompatibleEmbeddingAdapter adapter;

    private KnowledgeProperties properties;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        properties = new KnowledgeProperties();
        properties.getEmbedding().setEnabled(true);
        properties.getEmbedding().setBaseUrl(server.url("/v1").toString());
        properties.getEmbedding().setApiKey("test-key");
        properties.getEmbedding().setModel("text-embedding-test");
        properties.getEmbedding().setDimensions(4);
        adapter = new OpenAiCompatibleEmbeddingAdapter(properties, WebClient.builder());
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void embedsBatchAndPreservesOrder() throws Exception {
        server.enqueue(
                new MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                                """
                        {"data":[{"index":0,"embedding":[0.1,0.2,0.3,0.4]},{"index":1,"embedding":[0.5,0.6,0.7,0.8]}]}"""));

        StepVerifier.create(adapter.embed(List.of("第一段", "第二段")))
                .assertNext(vectors -> {
                    assertThat(vectors).hasSize(2);
                    assertThat(vectors.get(0)[0]).isEqualTo(0.1f);
                    assertThat(vectors.get(1)[3]).isEqualTo(0.8f);
                })
                .verifyComplete();

        String recorded = server.takeRequest().getBody().readUtf8();
        assertThat(recorded).contains("text-embedding-test").contains("第一段").contains("第二段");
    }

    @Test
    void dimensionMismatchFailsFast() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"data":[{"index":0,"embedding":[0.1,0.2]}]}"""));

        StepVerifier.create(adapter.embed(List.of("文本")))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void emptyInputSkipsRemoteCall() {
        assertThat(adapter.embed(List.of()).block()).isEmpty();
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void oversizedBatchIsPartitioned() throws Exception {
        properties.getEmbedding().setBatchSize(1);
        adapter = new OpenAiCompatibleEmbeddingAdapter(properties, WebClient.builder());
        // 三批各 1 条；响应体完全一致，规避并发请求的响应顺序不确定性
        MockResponse singleEntry = new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":[{\"index\":0,\"embedding\":[1,0,0,0]}]}");
        for (int i = 0; i < 3; i++) {
            server.enqueue(singleEntry.clone());
        }

        StepVerifier.create(adapter.embed(List.of("a", "b", "c")))
                .assertNext(vectors -> {
                    assertThat(vectors).hasSize(3);
                    assertThat(vectors.get(2)[3]).isZero();
                })
                .verifyComplete();
        assertThat(server.getRequestCount()).isEqualTo(3);
    }

    @Test
    void non2xxResponseBubblesError() {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("unauthorized"));
        assertThatThrownBy(() -> adapter.embed(List.of("文本")).block()).isInstanceOf(Exception.class);
    }
}
