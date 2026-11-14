package com.apex.scaffold.knowledge.infrastructure.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.knowledge.config.KnowledgeProperties;
import com.apex.scaffold.knowledge.domain.model.ChatMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

/**
 * OpenAI 兼容流式对话适配器测试（MockWebServer 模拟 SSE 供应商端点）。
 *
 * @author Apex Team
 * @since 1.3.2
 */
class OpenAiCompatibleChatAdapterTest {

    private MockWebServer server;

    private OpenAiCompatibleChatAdapter adapter;

    private KnowledgeProperties properties;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        properties = new KnowledgeProperties();
        properties.getChat().setEnabled(true);
        properties.getChat().setBaseUrl(server.url("/v1").toString());
        properties.getChat().setApiKey("test-key");
        properties.getChat().setModel("chat-model-test");
        adapter = new OpenAiCompatibleChatAdapter(properties, WebClient.builder(), new ObjectMapper());
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void streamsDeltasAndStopsAtDone() throws Exception {
        server.enqueue(
                new MockResponse()
                        .setHeader("Content-Type", "text/event-stream")
                        .setBody(
                                """
                        data: {"choices":[{"delta":{"content":"质量方针"}}]}

                        data: {"choices":[{"delta":{"content":"是以客户为中心"}}]}

                        data: [DONE]

                        """));

        StepVerifier.create(adapter.streamChat("系统提示", List.of(), "什么是质量方针"))
                .expectNext("质量方针")
                .expectNext("是以客户为中心")
                .verifyComplete();

        String recorded = server.takeRequest().getBody().readUtf8();
        assertThat(recorded)
                .contains("chat-model-test")
                .contains("stream\":true")
                .contains("系统提示");
    }

    @Test
    void nonDeltaFramesAreIgnored() {
        server.enqueue(
                new MockResponse()
                        .setHeader("Content-Type", "text/event-stream")
                        .setBody(
                                """
                        data: {"choices":[{"delta":{"role":"assistant"}}]}

                        data: {"usage":{"total_tokens":10}}

                        data: {"choices":[{"delta":{"content":"正文"}}]}

                        data: [DONE]

                        """));

        StepVerifier.create(adapter.streamChat("系统提示", List.of(), "提问"))
                .expectNext("正文")
                .verifyComplete();
    }

    @Test
    void historyMessagesAreIncludedInRequest() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("""
                        data: [DONE]

                        """));

        adapter.streamChat(
                        "系统提示",
                        List.of(
                                new ChatMessage(ChatMessage.ROLE_USER, "上一问"),
                                new ChatMessage(ChatMessage.ROLE_ASSISTANT, "上一答")),
                        "本轮提问")
                .blockLast();

        String recorded = server.takeRequest().getBody().readUtf8();
        assertThat(recorded).contains("上一问").contains("上一答").contains("本轮提问");
    }

    @Test
    void sseParseFailureFramesAreSkipped() {
        server.enqueue(
                new MockResponse()
                        .setHeader("Content-Type", "text/event-stream")
                        .setBody(
                                """
                        data: not-json

                        data: {"choices":[{"delta":{"content":"可用片段"}}]}

                        data: [DONE]

                        """));

        StepVerifier.create(adapter.streamChat("系统提示", List.of(), "提问"))
                .expectNext("可用片段")
                .verifyComplete();
    }
}
