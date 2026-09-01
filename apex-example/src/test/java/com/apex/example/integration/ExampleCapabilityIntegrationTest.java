package com.apex.example.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * 脚手架核心能力演示集成测试
 *
 * <p>覆盖 {@code /api/example/capabilities/*} 各端点，验证
 * 缓存读穿、幂等、SSE、脱敏、Excel 导出等能力的端到端可用性。
 *
 * <p>说明：Redis 依赖在 {@link ExampleTestMockConfiguration} 中以 Mock 提供，
 * 缓存/幂等端点验证的是「接入方式与响应结构」而非真实缓存行为。
 *
 * @author Apex Team
 * @version 1.1.4
 * @since 1.1.4
 */
class ExampleCapabilityIntegrationTest extends BaseIntegrationTest {

    /**
     * 缓存读穿端点：应返回统一成功响应且包含底层加载次数。
     */
    @Test
    @DisplayName("缓存读穿演示端点可访问并返回缓存值")
    void cacheReadShouldReturnCachedValue() {
        webTestClient
                .get()
                .uri("/api/example/capabilities/cache/demo")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.storageHits")
                .exists();
    }

    /**
     * 缓存失效端点：应返回统一成功响应且包含删除数量。
     */
    @Test
    @DisplayName("缓存失效端点可访问")
    void cacheEvictShouldReturnDeletedCount() {
        webTestClient
                .delete()
                .uri("/api/example/capabilities/cache/demo")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.deleted")
                .exists();
    }

    /**
     * 幂等端点：携带幂等键请求应返回统一成功响应并回显幂等键。
     */
    @Test
    @DisplayName("写接口幂等演示端点可访问（携带 Idempotency-Key）")
    void idempotentDemoShouldEchoIdempotencyKey() {
        webTestClient
                .post()
                .uri("/api/example/capabilities/idempotent")
                .header("Idempotency-Key", "test-uuid-001")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.idempotencyKey")
                .isEqualTo("test-uuid-001");
    }

    /**
     * 审计端点：应返回统一成功响应并提示审计已记录。
     */
    @Test
    @DisplayName("操作审计演示端点可访问")
    void auditDemoShouldReturnAuditedFlag() {
        webTestClient
                .post()
                .uri("/api/example/capabilities/audit")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.audited")
                .isEqualTo(true);
    }

    /**
     * SSE 端点：应返回 text/event-stream 且首条事件为心跳。
     *
     * <p>SSE 为持续推送流（10 条 / 每秒 1 条），测试仅消费首个事件
     * （{@code blockFirst}）即断开，避免阻塞等待完整流。</p>
     */
    @Test
    @DisplayName("SSE 演示端点返回事件流")
    void streamDemoShouldReturnSseEvents() {
        // 以响应流方式消费：只取第一条心跳事件后立即取消订阅
        String firstEvent = webTestClient
                .get()
                .uri("/api/example/capabilities/stream")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .returnResult(String.class)
                .getResponseBody()
                .take(1)
                .blockFirst();

        assertThat(firstEvent).contains("heartbeat");
    }

    /**
     * 脱敏端点：应返回统一成功响应且手机号被掩码。
     */
    @Test
    @DisplayName("数据脱敏演示端点返回掩码结果")
    void maskDemoShouldMaskSensitiveFields() {
        webTestClient
                .get()
                .uri("/api/example/capabilities/mask?phone=13800138000")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.phone")
                .isEqualTo("138****8000");
    }

    /**
     * Excel 导出端点：应返回 xlsx 附件流（文件头 PK 魔数）。
     *
     * <p>EasyExcel/POI 首次加载类较多（冷启动约 6~7s），单独放宽该请求
     * 的响应读取超时（默认 5s）。</p>
     */
    @Test
    @DisplayName("Excel 导出端点返回 xlsx 文件流")
    void exportUsersShouldReturnXlsxStream() {
        byte[] body = webTestClient
                .mutate()
                // 放宽响应读取超时：EasyExcel/POI 冷启动较慢
                .responseTimeout(java.time.Duration.ofSeconds(30))
                .build()
                .get()
                .uri("/api/example/capabilities/export/users")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentTypeCompatibleWith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .expectHeader()
                .value("Content-Disposition", value -> assertThat(value).contains("attachment"))
                .expectBody()
                .returnResult()
                .getResponseBody();

        // xlsx 为 ZIP 容器，PK 魔数校验
        assertThat(body).isNotNull();
        assertThat(body.length).isGreaterThan(0);
        assertThat(body[0]).isEqualTo((byte) 'P');
        assertThat(body[1]).isEqualTo((byte) 'K');
    }
}
