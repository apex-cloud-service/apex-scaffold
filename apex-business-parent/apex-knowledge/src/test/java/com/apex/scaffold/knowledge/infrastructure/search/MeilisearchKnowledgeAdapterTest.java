package com.apex.scaffold.knowledge.infrastructure.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.knowledge.config.KnowledgeProperties;
import com.apex.scaffold.knowledge.domain.model.IndexedChunk;
import com.apex.scaffold.knowledge.domain.model.VectorSearchQuery;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meilisearch.sdk.Client;
import java.time.Duration;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

class MeilisearchKnowledgeAdapterTest {

    private MockWebServer server;
    private KnowledgeProperties properties;
    private MeilisearchKnowledgeAdapter adapter;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        properties = new KnowledgeProperties();
        properties.getMeilisearch().setEnabled(true);
        properties.getMeilisearch().setUrl(server.url("/").toString());
        properties.getMeilisearch().setTaskPollInterval(Duration.ofMillis(1));
        properties.getMeilisearch().setTaskMaxPolls(3);
        properties.getMeilisearch().setTaskTimeout(Duration.ofSeconds(2));
        adapter = new MeilisearchKnowledgeAdapter(
                org.mockito.Mockito.mock(Client.class), properties, WebClient.builder(), json);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private void task(long uid, String status) {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"taskUid\":" + uid + ",\"status\":\"enqueued\"}"));
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"uid\":" + uid + ",\"status\":\"" + status + "\"}"));
    }

    @Test
    void deleteUsesPostBodyWithTenantAndDocumentNeverDeleteAll() throws Exception {
        task(1, "succeeded");
        task(2, "succeeded");
        StepVerifier.create(adapter.deleteDocumentChunks("t1", 9L)).verifyComplete();
        StepVerifier.create(adapter.deleteDocumentChunks("t2", 9L)).verifyComplete();
        var first = server.takeRequest();
        assertThat(first.getMethod()).isEqualTo("POST");
        assertThat(first.getPath()).isEqualTo("/indexes/apex_kb_chunks/documents/delete");
        assertThat(json.readTree(first.getBody().readUtf8()).get("filter").asText())
                .isEqualTo("tenantId = \"t1\" AND documentId = 9");
        assertThat(server.takeRequest().getPath()).isEqualTo("/tasks/1");
        var second = server.takeRequest();
        assertThat(json.readTree(second.getBody().readUtf8()).get("filter").asText())
                .isEqualTo("tenantId = \"t2\" AND documentId = 9");
    }

    @Test
    void deleteEscapesQuotedTenantAndRejectsMissingScope() throws Exception {
        task(3, "succeeded");
        StepVerifier.create(adapter.deleteDocumentChunks("a\" OR tenantId = \"b\\c", 9L))
                .verifyComplete();
        assertThat(json.readTree(server.takeRequest().getBody().readUtf8())
                        .get("filter")
                        .asText())
                .isEqualTo("tenantId = \"a\\\" OR tenantId = \\\"b\\\\c\" AND documentId = 9");
        StepVerifier.create(adapter.deleteDocumentChunks(" ", 9L))
                .expectError(IllegalArgumentException.class)
                .verify();
        StepVerifier.create(adapter.deleteDocumentChunks("t1", null))
                .expectError(IllegalArgumentException.class)
                .verify();
        StepVerifier.create(adapter.deleteDocumentChunks("t1", 0L))
                .expectError(IllegalArgumentException.class)
                .verify();
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    void taskFailureCancellationAndUnknownStatusRejectCompletion() {
        for (String status : List.of("failed", "canceled", "unexpected")) {
            task(4, status);
            StepVerifier.create(adapter.deleteDocumentChunks("t1", 9L))
                    .expectError(IllegalStateException.class)
                    .verify();
        }
    }

    @Test
    void pollingIsBoundedAndMissingTaskCannotBeSuccess() {
        server.enqueue(
                new MockResponse().setHeader("Content-Type", "application/json").setBody("{\"taskUid\":5}"));
        for (int i = 0; i < 3; i++) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"uid\":5,\"status\":\"processing\"}"));
        }
        StepVerifier.create(adapter.deleteDocumentChunks("t1", 9L))
                .expectError(IllegalStateException.class)
                .verify();
        server.enqueue(
                new MockResponse().setHeader("Content-Type", "application/json").setBody("{}"));
        StepVerifier.create(adapter.deleteDocumentChunks("t1", 9L))
                .expectError(IllegalStateException.class)
                .verify();
        assertThat(server.getRequestCount()).isEqualTo(5);
    }

    @Test
    void emptyTaskStatusResponseCannotConfirmSuccess() {
        server.enqueue(
                new MockResponse().setHeader("Content-Type", "application/json").setBody("{\"taskUid\":6}"));
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json"));
        StepVerifier.create(adapter.deleteDocumentChunks("t1", 9L))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void onlyExplicitIndexNotFoundMakesDeletionIdempotent() {
        server.enqueue(new MockResponse()
                .setResponseCode(404)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"code\":\"index_not_found\"}"));
        StepVerifier.create(adapter.deleteDocumentChunks("t1", 9L)).verifyComplete();
        server.enqueue(new MockResponse()
                .setResponseCode(404)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"code\":\"route_not_found\"}"));
        StepVerifier.create(adapter.deleteDocumentChunks("t1", 9L))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void createAndSettingsBothWaitForTaskSuccessAndOnlyThenCacheReady() throws Exception {
        task(10, "succeeded");
        task(11, "succeeded");
        StepVerifier.create(adapter.ensureIndex(true, 4)).verifyComplete();
        assertThat(server.takeRequest().getPath()).isEqualTo("/indexes");
        server.takeRequest();
        var settings = server.takeRequest();
        assertThat(settings.getMethod()).isEqualTo("PATCH");
        assertThat(settings.getBody().readUtf8()).contains("\"dimensions\":4", "tenantId");
        server.takeRequest();
        StepVerifier.create(adapter.ensureIndex(true, 4)).verifyComplete();
        assertThat(server.getRequestCount()).isEqualTo(4);
    }

    @Test
    void onlyExplicitAlreadyExistsIsIgnoredAndFailedSettingsRemainRetryable() {
        server.enqueue(new MockResponse()
                .setResponseCode(409)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"code\":\"index_already_exists\"}"));
        task(11, "failed");
        StepVerifier.create(adapter.ensureIndex(false, 4))
                .expectError(IllegalStateException.class)
                .verify();
        server.enqueue(new MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"code\":\"invalid_api_key\"}"));
        StepVerifier.create(adapter.ensureIndex(false, 4))
                .expectError(IllegalStateException.class)
                .verify();
        assertThat(server.getRequestCount()).isEqualTo(4);
    }

    @Test
    void upsertWaitsForSuccessfulTaskAndContainsTenantAndVector() throws Exception {
        IndexedChunk chunk = IndexedChunk.builder()
                .chunkId(101L)
                .documentId(9L)
                .spaceId(2L)
                .tenantId("t1")
                .seq(0)
                .content("正文")
                .embedding(new float[] {0.1f, 0.2f})
                .build();
        task(12, "succeeded");
        StepVerifier.create(adapter.upsertChunks(List.of(chunk))).verifyComplete();
        var request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/indexes/apex_kb_chunks/documents?primaryKey=id");
        var doc = json.readTree(request.getBody().readUtf8()).get(0);
        assertThat(doc.get("tenantId").asText()).isEqualTo("t1");
        assertThat(doc.has("_vectors")).isTrue();
        task(13, "failed");
        StepVerifier.create(adapter.upsertChunks(List.of(chunk)))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void hybridFailureFallsBackToLexicalSearch() {
        server.enqueue(new MockResponse().setResponseCode(400).setBody("{}"));
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"hits\":[],\"estimatedTotalHits\":0}"));
        StepVerifier.create(adapter.search(VectorSearchQuery.builder()
                        .tenantId("t1")
                        .keyword("正文")
                        .queryEmbedding(new float[] {1})
                        .limit(5)
                        .build()))
                .assertNext(result -> assertThat(result.isHybridUsed()).isFalse())
                .verifyComplete();
    }
}
