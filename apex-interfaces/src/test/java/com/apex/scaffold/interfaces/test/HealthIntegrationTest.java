package com.apex.scaffold.interfaces.test;

import com.apex.scaffold.common.constant.SystemConstants;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * 健康检查接口集成测试
 *
 * <p>测试 /api/v1/health 和 /api/v1/info 端点的可用性和响应内容。
 *
 * @author Apex Team
 */
@DisplayName("健康检查接口集成测试")
@EnabledIfSystemProperty(named = "apex.remote-it.enabled", matches = "true")
class HealthRemoteIT extends BaseLocalIntegrationTest {

    /**
     * 创建 {@code HealthRemoteIT} 实例。
     * @param dataSource 参数 {@code dataSource}
     */
    public HealthRemoteIT(DataSource dataSource) {
        super(dataSource);
    }

    /**
     * 执行 {@code health_shouldReturnUpStatus} 操作。
     */
    @Test
    @DisplayName("GET /api/v1/health - 应返回200及UP状态")
    void health_shouldReturnUpStatus() {
        webTestClient
                .get()
                .uri("/api/v1/health")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.status")
                .isEqualTo("UP")
                .jsonPath("$.data.application")
                .isEqualTo("apex-scaffold");
    }

    /**
     * 执行 {@code info_shouldReturnSystemInfo} 操作。
     */
    @Test
    @DisplayName("GET /api/v1/info - 应返回200及系统信息")
    void info_shouldReturnSystemInfo() {
        String accessToken = generateAccessToken(TEST_OPEN_ID, 60);
        webTestClient
                .get()
                .uri("/api/v1/info")
                .header(SystemConstants.TOKEN_HEADER, SystemConstants.TOKEN_PREFIX + accessToken)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.application")
                .isEqualTo("Apex Scaffold Service")
                .jsonPath("$.data.version")
                .isEqualTo("1.0.0");
    }

    /**
     * 执行 {@code health_shouldContainTimestamp} 操作。
     */
    @Test
    @DisplayName("GET /api/v1/health - 响应中包含时间戳")
    void health_shouldContainTimestamp() {
        webTestClient
                .get()
                .uri("/api/v1/health")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.timestamp")
                .isNotEmpty();
    }

    /**
     * 执行 {@code info_shouldContainEnvironmentInfo} 操作。
     */
    @Test
    @DisplayName("GET /api/v1/info - 响应中包含Java版本和操作系统信息")
    void info_shouldContainEnvironmentInfo() {
        String accessToken = generateAccessToken(TEST_OPEN_ID, 60);
        webTestClient
                .get()
                .uri("/api/v1/info")
                .header(SystemConstants.TOKEN_HEADER, SystemConstants.TOKEN_PREFIX + accessToken)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.java")
                .isNotEmpty()
                .jsonPath("$.data.os")
                .isNotEmpty()
                .jsonPath("$.data.timestamp")
                .isNotEmpty();
    }
}
