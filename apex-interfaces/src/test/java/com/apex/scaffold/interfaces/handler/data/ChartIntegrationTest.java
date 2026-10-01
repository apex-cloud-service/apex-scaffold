package com.apex.scaffold.interfaces.handler.data;

import com.apex.scaffold.application.dto.data.ChartRequest;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@DisplayName("图表管理集成测试")
@RequiredArgsConstructor
class ChartIntegrationTest {

    private final WebTestClient webTestClient;

    private ChartRequest createRequest;

    @BeforeEach
    void setUp() {
        Map<String, Object> config = new HashMap<>();
        config.put("color", "blue");

        createRequest = ChartRequest.builder()
                .chartName("集成测试图表")
                .chartType("line")
                .category("business")
                .description("集成测试图表描述")
                .dataSource("test_db")
                .config(config)
                .build();
    }

    @Test
    @DisplayName("创建图表")
    void testCreateChart() {
        webTestClient
                .post()
                .uri("/admin-api/v1/data/charts")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createRequest)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.chartName")
                .isEqualTo("集成测试图表");
    }

    @Test
    @DisplayName("分页查询图表")
    void testQueryCharts() {
        webTestClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/admin-api/v1/data/charts")
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
