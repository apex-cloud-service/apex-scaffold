package com.apex.scaffold.interfaces.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * 接口文档端点端到端验证。
 *
 * <p>启动完整应用上下文（it profile，MySQL + mock Redis），验证：
 * <ul>
 *   <li>{@code GET /doc.html} — Knife4j 增强文档入口可访问（返回 200，静态资源由 webjar 提供）</li>
 *   <li>{@code GET /v3/api-docs} — OpenAPI JSON 端点可访问，且包含系统管理分组声明的路径</li>
 *   <li>{@code GET /swagger-ui.html} — Swagger UI 入口可访问（重定向到 springdoc 默认 UI）</li>
 * </ul>
 *
 * @author Apex Team
 */
@SpringBootTest(classes = IntegrationTestApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Import(ItProfileMockRedisConfiguration.class)
class OpenApiEndpointsIntegrationTest {

    @LocalServerPort
    private int port;

    private WebTestClient client() {
        return WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Test
    void knife4jDocHtmlIsServed() {
        client().get()
                .uri("/doc.html")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.OK)
                .expectBody(String.class)
                .consumeWith(response -> assertThat(response.getResponseBody())
                        .as("doc.html 应返回 knife4j 前端应用")
                        .contains("<div id=app>"));
    }

    @Test
    void openApiJsonEndpointIsServedWithAdminSystemPaths() {
        client().get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.OK)
                .expectBody(String.class)
                .consumeWith(response -> {
                    String body = response.getResponseBody();
                    assertThat(body).as("OpenAPI JSON 应包含菜单管理路径").contains("/admin-api/v1/menus");
                    assertThat(body).as("OpenAPI JSON 应包含定时任务路径").contains("/admin-api/v1/jobs");
                    assertThat(body).as("OpenAPI JSON 应包含验证码路径").contains("/api/v1/auth/captcha");
                    assertThat(body).as("OpenAPI JSON 应包含统一响应模型").contains("ApiSuccessResponse");
                });
    }

    @Test
    void swaggerUiIsServed() {
        // springdoc 默认将 /swagger-ui.html 重定向到 /swagger-ui/index.html（WebFlux 下为 302）
        client().get().uri("/swagger-ui.html").exchange().expectStatus().value(status -> assertThat(status)
                .isIn(302, 200));
    }
}
