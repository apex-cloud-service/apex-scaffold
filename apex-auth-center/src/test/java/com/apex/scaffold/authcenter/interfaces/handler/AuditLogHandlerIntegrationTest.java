package com.apex.scaffold.authcenter.interfaces.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import com.apex.scaffold.authcenter.application.assembler.AuditLogAssembler;
import com.apex.scaffold.authcenter.application.dto.AuditLogDTO;
import com.apex.scaffold.authcenter.application.dto.AuditLogQueryRequest;
import com.apex.scaffold.authcenter.application.service.AuditLogService;
import com.apex.scaffold.authcenter.domain.model.entity.AuditLogEntity;
import com.apex.scaffold.authcenter.interfaces.router.AuditLogRouter;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * AuditLogHandler集成测试
 *
 * <p>测试审计日志Handler的所有端点，包括查询、统计和清理功能。
 *
 * @author Apex Scaffold Team
 */
@WebFluxTest(
        controllers = AuditLogHandler.class,
        excludeAutoConfiguration = {
            org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration.class,
            org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration.class
        })
@Import({AuditLogRouter.class})
@DisplayName("审计日志Handler集成测试")
class AuditLogHandlerIntegrationTest {

    /**
     * {@code WebTestClient} 在 {@code @WebFluxTest} 中无法通过构造器解析，
     * 故采用 JSR-250 {@code @Resource} 字段注入。
     */
    @Resource
    private WebTestClient webTestClient;

    @MockBean
    private AuditLogService auditLogService;

    @MockBean
    private AuditLogAssembler auditLogAssembler;

    private AuditLogEntity testLog;
    private AuditLogDTO testDto;
    private AuditLogQueryRequest queryRequest;

    @Test
    void shouldExposeAllOpenApiOperations(org.springframework.context.ApplicationContext context) {
        org.springdoc.core.annotations.RouterOperations operations =
                context.findAnnotationOnBean("auditLogRoutes", org.springdoc.core.annotations.RouterOperations.class);
        assertThat(operations).isNotNull();
        assertThat(operations.value()).hasSize(10);
    }

    @BeforeEach
    void setUp() {
        testLog = AuditLogEntity.builder()
                .id(1L)
                .tenantId("test-tenant")
                .userId(100L)
                .username("testuser")
                .eventType("LOGIN")
                .eventStatus("SUCCESS")
                .action("USER_LOGIN")
                .resourceType("USER")
                .resourceId("100")
                .riskLevel("LOW")
                .ipAddress("192.168.1.100")
                .userAgent("Mozilla/5.0")
                .details("{\"method\": \"password\"}")
                .eventTime(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        testDto = AuditLogDTO.builder()
                .id(1L)
                .tenantId("test-tenant")
                .userId(100L)
                .username("testuser")
                .eventType("LOGIN")
                .eventStatus("SUCCESS")
                .action("USER_LOGIN")
                .resourceType("USER")
                .resourceId("100")
                .riskLevel("LOW")
                .ipAddress("192.168.1.100")
                .userAgent("Mozilla/5.0")
                .details("{\"method\": \"password\"}")
                .eventTime(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        queryRequest = AuditLogQueryRequest.builder()
                .tenantId("test-tenant")
                .userId(100L)
                .eventType("LOGIN")
                .startTime(LocalDateTime.now().minusDays(7))
                .endTime(LocalDateTime.now())
                .build();

        // Mock AuditLogAssembler 同步版本 - 用于 map 操作
        when(auditLogAssembler.toDto(any(AuditLogEntity.class))).thenAnswer(invocation -> {
            AuditLogEntity entity = invocation.getArgument(0);
            if (entity == null) {
                return null;
            }
            return AuditLogDTO.builder()
                    .id(entity.getId())
                    .tenantId(entity.getTenantId())
                    .userId(entity.getUserId())
                    .username(entity.getUsername())
                    .eventType(entity.getEventType())
                    .eventStatus(entity.getEventStatus())
                    .action(entity.getAction())
                    .resourceType(entity.getResourceType())
                    .resourceId(entity.getResourceId())
                    .riskLevel(entity.getRiskLevel())
                    .ipAddress(entity.getIpAddress())
                    .userAgent(entity.getUserAgent())
                    .details(entity.getDetails())
                    .eventTime(entity.getEventTime())
                    .createdAt(entity.getCreatedAt())
                    .build();
        });

        // Mock AuditLogAssembler 响应式版本 - 用于 getLog 方法
        when(auditLogAssembler.toDto(any(Mono.class))).thenAnswer(invocation -> {
            Mono<AuditLogEntity> entityMono = invocation.getArgument(0);
            return entityMono.map(entity -> AuditLogDTO.builder()
                    .id(entity.getId())
                    .tenantId(entity.getTenantId())
                    .userId(entity.getUserId())
                    .username(entity.getUsername())
                    .eventType(entity.getEventType())
                    .eventStatus(entity.getEventStatus())
                    .action(entity.getAction())
                    .resourceType(entity.getResourceType())
                    .resourceId(entity.getResourceId())
                    .riskLevel(entity.getRiskLevel())
                    .ipAddress(entity.getIpAddress())
                    .userAgent(entity.getUserAgent())
                    .details(entity.getDetails())
                    .eventTime(entity.getEventTime())
                    .createdAt(entity.getCreatedAt())
                    .build());
        });
    }

    @Test
    @DisplayName("获取审计日志详情")
    void testGetLog() {
        when(auditLogService.getLogById(1L)).thenReturn(Mono.just(testLog));

        webTestClient
                .get()
                .uri("/api/v1/auth/audit-logs/1")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Result.class)
                .value(result -> {
                    assertThat(result.getCode()).isEqualTo(0);
                    assertThat(result.getData()).isNotNull();
                });
    }

    @Test
    @DisplayName("查询用户审计日志")
    void testListUserLogs() {
        when(auditLogService.listUserLogs(anyLong(), anyString())).thenReturn(Flux.just(testLog));

        webTestClient
                .get()
                .uri("/api/v1/auth/audit-logs/user/100?tenantId=test-tenant")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Result.class)
                .value(result -> {
                    assertThat(result.getCode()).isEqualTo(0);
                    assertThat(result.getData()).isNotNull();
                });
    }

    @Test
    @DisplayName("按事件类型查询审计日志")
    void testListLogsByEventType() {
        when(auditLogService.listLogsByEventType(anyString(), anyString())).thenReturn(Flux.just(testLog));

        webTestClient
                .get()
                .uri("/api/v1/auth/audit-logs/event-type/LOGIN?tenantId=test-tenant")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Result.class)
                .value(result -> {
                    assertThat(result.getCode()).isEqualTo(0);
                    assertThat(result.getData()).isNotNull();
                });
    }

    @Test
    @DisplayName("按时间范围查询审计日志")
    void testListLogsByTimeRange() {
        when(auditLogService.listLogsByTimeRange(any(LocalDateTime.class), any(LocalDateTime.class), anyString()))
                .thenReturn(Flux.just(testLog));

        webTestClient
                .post()
                .uri("/api/v1/auth/audit-logs/time-range")
                .bodyValue(queryRequest)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Result.class)
                .value(result -> {
                    assertThat(result.getCode()).isEqualTo(0);
                    assertThat(result.getData()).isNotNull();
                });
    }

    @Test
    @DisplayName("查询失败日志")
    void testListFailedLogs() {
        when(auditLogService.listFailedLogs(anyString())).thenReturn(Flux.just(testLog));

        webTestClient
                .get()
                .uri("/api/v1/auth/audit-logs/failed?tenantId=test-tenant")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Result.class)
                .value(result -> {
                    assertThat(result.getCode()).isEqualTo(0);
                    assertThat(result.getData()).isNotNull();
                });
    }

    @Test
    @DisplayName("查询高风险日志")
    void testListHighRiskLogs() {
        when(auditLogService.listHighRiskLogs(anyString())).thenReturn(Flux.just(testLog));

        webTestClient
                .get()
                .uri("/api/v1/auth/audit-logs/high-risk?tenantId=test-tenant")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Result.class)
                .value(result -> {
                    assertThat(result.getCode()).isEqualTo(0);
                    assertThat(result.getData()).isNotNull();
                });
    }

    @Test
    @DisplayName("按IP地址查询日志")
    void testListLogsByIpAddress() {
        when(auditLogService.listLogsByIpAddress(anyString(), anyString())).thenReturn(Flux.just(testLog));

        webTestClient
                .get()
                .uri("/api/v1/auth/audit-logs/ip/192.168.1.100?tenantId=test-tenant")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Result.class)
                .value(result -> {
                    assertThat(result.getCode()).isEqualTo(0);
                    assertThat(result.getData()).isNotNull();
                });
    }

    @Test
    @DisplayName("统计日志数量")
    void testCountLogsByTimeRange() {
        when(auditLogService.countLogsByTimeRange(any(LocalDateTime.class), any(LocalDateTime.class), anyString()))
                .thenReturn(Mono.just(10L));

        webTestClient
                .post()
                .uri("/api/v1/auth/audit-logs/count")
                .bodyValue(queryRequest)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Result.class)
                .value(result -> {
                    assertThat(result.getCode()).isEqualTo(0);
                    assertThat(result.getData()).isNotNull();
                });
    }

    @Test
    @DisplayName("统计登录失败次数")
    void testCountLoginFailures() {
        when(auditLogService.countLoginFailures(anyLong(), any(LocalDateTime.class), anyString()))
                .thenReturn(Mono.just(3L));

        webTestClient
                .get()
                .uri(
                        "/api/v1/auth/audit-logs/user/100/login-failures?tenantId=test-tenant&startTime=2024-01-01T00:00:00")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Result.class)
                .value(result -> {
                    assertThat(result.getCode()).isEqualTo(0);
                    assertThat(result.getData()).isNotNull();
                });
    }

    @Test
    @DisplayName("清理过期日志")
    void testCleanupExpiredLogs() {
        when(auditLogService.cleanupExpiredLogs(any(LocalDateTime.class), anyString()))
                .thenReturn(Mono.just(5L));

        webTestClient
                .delete()
                .uri("/api/v1/auth/audit-logs/cleanup?tenantId=test-tenant&beforeTime=2024-01-01T00:00:00")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Result.class)
                .value(result -> {
                    assertThat(result.getCode()).isEqualTo(0);
                    assertThat(result.getData()).isNotNull();
                });
    }

    @Test
    void testGetLogServiceError() {
        when(auditLogService.getLogById(1L))
                .thenReturn(reactor.core.publisher.Mono.error(new IllegalStateException("service unavailable")));

        webTestClient
                .get()
                .uri("/api/v1/auth/audit-logs/1")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.ERROR.getCode())
                .jsonPath("$.msg")
                .isNotEmpty()
                .jsonPath("$.data")
                .doesNotExist();
    }

    @Test
    void testListUserLogsServiceError() {
        when(auditLogService.listUserLogs(anyLong(), anyString()))
                .thenReturn(Flux.error(new IllegalStateException("service unavailable")));

        webTestClient
                .get()
                .uri("/api/v1/auth/audit-logs/user/100?tenantId=test-tenant")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.ERROR.getCode())
                .jsonPath("$.msg")
                .isNotEmpty()
                .jsonPath("$.data")
                .doesNotExist();
    }

    @Test
    void testListLogsByEventTypeServiceError() {
        when(auditLogService.listLogsByEventType(anyString(), anyString()))
                .thenReturn(Flux.error(new IllegalStateException("service unavailable")));

        webTestClient
                .get()
                .uri("/api/v1/auth/audit-logs/event-type/LOGIN?tenantId=test-tenant")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.ERROR.getCode())
                .jsonPath("$.msg")
                .isNotEmpty()
                .jsonPath("$.data")
                .doesNotExist();
    }

    @Test
    void testListLogsByTimeRangeServiceError() {
        when(auditLogService.listLogsByTimeRange(any(LocalDateTime.class), any(LocalDateTime.class), anyString()))
                .thenReturn(Flux.error(new IllegalStateException("service unavailable")));

        webTestClient
                .post()
                .uri("/api/v1/auth/audit-logs/time-range")
                .bodyValue(queryRequest)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.ERROR.getCode())
                .jsonPath("$.msg")
                .isNotEmpty()
                .jsonPath("$.data")
                .doesNotExist();
    }

    @Test
    void testListFailedLogsServiceError() {
        when(auditLogService.listFailedLogs(anyString()))
                .thenReturn(Flux.error(new IllegalStateException("service unavailable")));

        webTestClient
                .get()
                .uri("/api/v1/auth/audit-logs/failed?tenantId=test-tenant")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.ERROR.getCode())
                .jsonPath("$.msg")
                .isNotEmpty()
                .jsonPath("$.data")
                .doesNotExist();
    }

    @Test
    void testListHighRiskLogsServiceError() {
        when(auditLogService.listHighRiskLogs(anyString()))
                .thenReturn(Flux.error(new IllegalStateException("service unavailable")));

        webTestClient
                .get()
                .uri("/api/v1/auth/audit-logs/high-risk?tenantId=test-tenant")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.ERROR.getCode())
                .jsonPath("$.msg")
                .isNotEmpty()
                .jsonPath("$.data")
                .doesNotExist();
    }

    @Test
    void testListLogsByIpAddressServiceError() {
        when(auditLogService.listLogsByIpAddress(anyString(), anyString()))
                .thenReturn(Flux.error(new IllegalStateException("service unavailable")));

        webTestClient
                .get()
                .uri("/api/v1/auth/audit-logs/ip/192.168.1.100?tenantId=test-tenant")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.ERROR.getCode())
                .jsonPath("$.msg")
                .isNotEmpty()
                .jsonPath("$.data")
                .doesNotExist();
    }

    @Test
    void testCountLogsByTimeRangeServiceError() {
        when(auditLogService.countLogsByTimeRange(any(LocalDateTime.class), any(LocalDateTime.class), anyString()))
                .thenReturn(reactor.core.publisher.Mono.error(new IllegalStateException("service unavailable")));

        webTestClient
                .post()
                .uri("/api/v1/auth/audit-logs/count")
                .bodyValue(queryRequest)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.ERROR.getCode())
                .jsonPath("$.msg")
                .isNotEmpty()
                .jsonPath("$.data")
                .doesNotExist();
    }

    @Test
    void testCountLoginFailuresServiceError() {
        when(auditLogService.countLoginFailures(anyLong(), any(LocalDateTime.class), anyString()))
                .thenReturn(reactor.core.publisher.Mono.error(new IllegalStateException("service unavailable")));

        webTestClient
                .get()
                .uri(
                        "/api/v1/auth/audit-logs/user/100/login-failures?tenantId=test-tenant&startTime=2024-01-01T00:00:00")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.ERROR.getCode())
                .jsonPath("$.msg")
                .isNotEmpty()
                .jsonPath("$.data")
                .doesNotExist();
    }

    @Test
    void testCleanupExpiredLogsServiceError() {
        when(auditLogService.cleanupExpiredLogs(any(LocalDateTime.class), anyString()))
                .thenReturn(reactor.core.publisher.Mono.error(new IllegalStateException("service unavailable")));

        webTestClient
                .delete()
                .uri("/api/v1/auth/audit-logs/cleanup?tenantId=test-tenant&beforeTime=2024-01-01T00:00:00")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.ERROR.getCode())
                .jsonPath("$.msg")
                .isNotEmpty()
                .jsonPath("$.data")
                .doesNotExist();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(
            strings = {"/user/100", "/event-type/LOGIN", "/failed", "/high-risk", "/ip/192.0.2.10"})
    void testLogListsEmpty(String path) {
        when(auditLogService.listUserLogs(100L, "tenant-b")).thenReturn(Flux.empty());
        when(auditLogService.listLogsByEventType("LOGIN", "tenant-b")).thenReturn(Flux.empty());
        when(auditLogService.listFailedLogs("tenant-b")).thenReturn(Flux.empty());
        when(auditLogService.listHighRiskLogs("tenant-b")).thenReturn(Flux.empty());
        when(auditLogService.listLogsByIpAddress("192.0.2.10", "tenant-b")).thenReturn(Flux.empty());
        webTestClient
                .get()
                .uri("/api/v1/auth/audit-logs" + path + "?tenantId=tenant-b")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data")
                .isArray()
                .jsonPath("$.data.length()")
                .isEqualTo(0);
    }

    @Test
    void testTimeRangePassesExactQueryFields() {
        when(auditLogService.listLogsByTimeRange(queryRequest.getStartTime(), queryRequest.getEndTime(), "test-tenant"))
                .thenReturn(Flux.empty());
        webTestClient
                .post()
                .uri("/api/v1/auth/audit-logs/time-range")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue(queryRequest)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data")
                .isEmpty();
        org.mockito.Mockito.verify(auditLogService)
                .listLogsByTimeRange(queryRequest.getStartTime(), queryRequest.getEndTime(), "test-tenant");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(
            strings = {
                "/user/100",
                "/event-type/LOGIN",
                "/failed",
                "/high-risk",
                "/ip/192.0.2.10",
                "/user/100/login-failures",
                "/user/100/login-failures?tenantId=tenant-b"
            })
    void testMissingQueryParameterCurrentlyReturnsServerError(String path) {
        webTestClient
                .get()
                .uri("/api/v1/auth/audit-logs" + path)
                .exchange()
                .expectStatus()
                .isEqualTo(500);
        org.mockito.Mockito.verifyNoInteractions(auditLogService);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"", "?tenantId=tenant-b"})
    void testCleanupMissingQueryParameterCurrentlyReturnsServerError(String query) {
        webTestClient
                .delete()
                .uri("/api/v1/auth/audit-logs/cleanup" + query)
                .exchange()
                .expectStatus()
                .isEqualTo(500);
        org.mockito.Mockito.verifyNoInteractions(auditLogService);
    }
}
