package com.apex.scaffold.interfaces.handler.data;

import com.apex.scaffold.application.dto.data.DocumentRequest;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@DisplayName("文档管理集成测试")
class DocumentIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    private DocumentRequest createRequest;

    @BeforeEach
    void setUp() {
        createRequest = DocumentRequest.builder()
                .title("集成测试文档")
                .docType("report")
                .category("tech")
                .content("这是一个集成测试文档的内容")
                .tags(Arrays.asList("测试", "集成"))
                .build();
    }

    @Test
    @DisplayName("创建文档")
    void testCreateDocument() {
        webTestClient
                .post()
                .uri("/admin-api/v1/data/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createRequest)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.title")
                .isEqualTo("集成测试文档");
    }

    @Test
    @DisplayName("分页查询文档")
    void testQueryDocuments() {
        webTestClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/admin-api/v1/data/documents")
                        .queryParam("page", 1)
                        .queryParam("size", 10)
                        .build())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.list")
                .isArray();
    }
}
