package com.apex.scaffold.authservice.interfaces.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import com.apex.scaffold.auth.application.dto.MfaSetupRequest;
import com.apex.scaffold.auth.application.dto.MfaVerifyRequest;
import com.apex.scaffold.auth.application.service.TotpService;
import com.apex.scaffold.authservice.AuthCenterSliceTestConfiguration;
import com.apex.scaffold.authservice.interfaces.router.MfaRouter;
import com.apex.scaffold.authservice.interfaces.util.RequestHandlerUtil;
import com.apex.scaffold.common.result.ResultCode;
import jakarta.annotation.Resource;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * MFA Handler 集成测试
 *
 * <p>测试 MFA 相关的 HTTP 接口，包括设置、验证、禁用和状态查询。
 *
 * @author Apex Scaffold Team
 * @since 2026-09-21
 */
@WebFluxTest(
        controllers = MfaHandler.class,
        excludeAutoConfiguration = {
            org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration.class,
            org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration.class
        })
@AutoConfigureWebTestClient
@Import({MfaRouter.class, RequestHandlerUtil.class, AuthCenterSliceTestConfiguration.class})
@DisplayName("MFA Handler 集成测试")
class MfaHandlerIntegrationTest {

    /**
     * {@code WebTestClient} 在 {@code @WebFluxTest} 中无法通过构造器解析，
     * 故采用 JSR-250 {@code @Resource} 字段注入。
     */
    @Resource
    private WebTestClient webTestClient;

    @MockBean
    private TotpService totpService;

    private MfaSetupRequest setupRequest;
    private MfaVerifyRequest verifyRequest;

    @Test
    void shouldExposeAllOpenApiOperations(org.springframework.context.ApplicationContext context) {
        org.springdoc.core.annotations.RouterOperations operations =
                context.findAnnotationOnBean("mfaRoutes", org.springdoc.core.annotations.RouterOperations.class);
        assertThat(operations).isNotNull();
        assertThat(operations.value()).hasSize(6);
    }

    @BeforeEach
    void setUp() {
        setupRequest = MfaSetupRequest.builder()
                .userId(123L)
                .tenantId("test-tenant")
                .issuer("Apex")
                .accountName("user@example.com")
                .build();

        verifyRequest = MfaVerifyRequest.builder()
                .userId(123L)
                .challengeToken("challenge-token-for-test")
                .code("123456")
                .tenantId("test-tenant")
                .build();
    }

    @Test
    @DisplayName("设置 MFA - 成功")
    void testSetupMfa() {
        TotpService.TotpSetupResult result = new TotpService.TotpSetupResult(
                "JBSWY3DPEHPK3PXP",
                "otpauth://totp/Apex:user@example.com?secret=JBSWY3DPEHPK3PXP",
                List.of("12345678", "23456789"));

        when(totpService.generateSecret(eq(123L), eq("test-tenant"), eq("Apex"), eq("user@example.com")))
                .thenReturn(reactor.core.publisher.Mono.just(result));

        webTestClient
                .post()
                .uri("/api/v1/mfa/setup")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(setupRequest)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.secret")
                .isNotEmpty()
                .jsonPath("$.data.otpAuthUrl")
                .isNotEmpty()
                .jsonPath("$.data.backupCodes")
                .isNotEmpty();
    }

    @Test
    @DisplayName("验证 TOTP 并启用 MFA - 成功")
    void testVerifyAndEnableMfa() {
        when(totpService.verifyAndEnable(eq(123L), eq("123456"), eq("test-tenant")))
                .thenReturn(reactor.core.publisher.Mono.just(true));

        webTestClient
                .post()
                .uri("/api/v1/mfa/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(verifyRequest)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.enabled")
                .isEqualTo(true);
    }

    @Test
    @DisplayName("验证 TOTP 并启用 MFA - 验证码错误")
    void testVerifyAndEnableMfaWrongCode() {
        when(totpService.verifyAndEnable(eq(123L), eq("123456"), eq("test-tenant")))
                .thenReturn(reactor.core.publisher.Mono.just(false));

        webTestClient
                .post()
                .uri("/api/v1/mfa/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(verifyRequest)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.ERROR.getCode())
                .jsonPath("$.msg")
                .isEqualTo("验证码错误")
                .jsonPath("$.data")
                .doesNotExist();
    }

    @Test
    @DisplayName("验证 TOTP 验证码 - 登录时")
    void testVerifyMfaCode() {
        when(totpService.verifyCode(eq(123L), eq("123456"), eq("test-tenant")))
                .thenReturn(reactor.core.publisher.Mono.just(true));

        webTestClient
                .post()
                .uri("/api/v1/mfa/verify-code")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(verifyRequest)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.verified")
                .isEqualTo(true);
    }

    @Test
    @DisplayName("验证备份码 - 成功")
    void testVerifyBackupCode() {
        MfaVerifyRequest backupRequest = MfaVerifyRequest.builder()
                .userId(123L)
                .challengeToken("challenge-token-for-test")
                .backupCode("12345678")
                .tenantId("test-tenant")
                .build();

        when(totpService.verifyBackupCode(eq(123L), eq("12345678"), eq("test-tenant")))
                .thenReturn(reactor.core.publisher.Mono.just(true));

        webTestClient
                .post()
                .uri("/api/v1/mfa/verify-backup")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(backupRequest)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.verified")
                .isEqualTo(true);
    }

    @Test
    @DisplayName("禁用 MFA - 成功")
    void testDisableMfa() {
        when(totpService.disable(eq(123L), eq("test-tenant"))).thenReturn(reactor.core.publisher.Mono.just(true));

        webTestClient
                .post()
                .uri("/api/v1/mfa/disable")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(verifyRequest)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.disabled")
                .isEqualTo(true);
    }

    @Test
    @DisplayName("查询 MFA 状态 - 已启用")
    void testGetMfaStatusEnabled() {
        when(totpService.isEnabled(eq(123L), eq("test-tenant"))).thenReturn(reactor.core.publisher.Mono.just(true));

        webTestClient
                .get()
                .uri("/api/v1/mfa/status?userId=123&tenantId=test-tenant")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.enabled")
                .isEqualTo(true)
                .jsonPath("$.data.mfaType")
                .isEqualTo("TOTP");
    }

    @Test
    @DisplayName("查询 MFA 状态 - 未启用")
    void testGetMfaStatusDisabled() {
        when(totpService.isEnabled(eq(123L), eq("test-tenant"))).thenReturn(reactor.core.publisher.Mono.just(false));

        webTestClient
                .get()
                .uri("/api/v1/mfa/status?userId=123&tenantId=test-tenant")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.enabled")
                .isEqualTo(false)
                .jsonPath("$.data.mfaType")
                .isEqualTo("NONE");
    }

    @Test
    @DisplayName("设置 MFA - 缺少必填字段")
    void testSetupMfaMissingFields() {
        MfaSetupRequest invalidRequest =
                MfaSetupRequest.builder().userId(123L).tenantId("test-tenant").build();

        webTestClient
                .post()
                .uri("/api/v1/mfa/setup")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidRequest)
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    @Test
    void testSetupMfaServiceError() {
        TotpService.TotpSetupResult result = new TotpService.TotpSetupResult(
                "JBSWY3DPEHPK3PXP",
                "otpauth://totp/Apex:user@example.com?secret=JBSWY3DPEHPK3PXP",
                List.of("12345678", "23456789"));

        when(totpService.generateSecret(eq(123L), eq("test-tenant"), eq("Apex"), eq("user@example.com")))
                .thenReturn(reactor.core.publisher.Mono.error(new IllegalStateException("service unavailable")));

        webTestClient
                .post()
                .uri("/api/v1/mfa/setup")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(setupRequest)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.ERROR.getCode())
                .jsonPath("$.msg")
                .isNotEmpty()
                .jsonPath("$.data")
                .doesNotExist();
    }

    @Test
    void testVerifyAndEnableMfaServiceError() {
        when(totpService.verifyAndEnable(eq(123L), eq("123456"), eq("test-tenant")))
                .thenReturn(reactor.core.publisher.Mono.error(new IllegalStateException("service unavailable")));

        webTestClient
                .post()
                .uri("/api/v1/mfa/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(verifyRequest)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.ERROR.getCode())
                .jsonPath("$.msg")
                .isNotEmpty()
                .jsonPath("$.data")
                .doesNotExist();
    }

    @Test
    void testVerifyMfaCodeServiceError() {
        when(totpService.verifyCode(eq(123L), eq("123456"), eq("test-tenant")))
                .thenReturn(reactor.core.publisher.Mono.error(new IllegalStateException("service unavailable")));

        webTestClient
                .post()
                .uri("/api/v1/mfa/verify-code")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(verifyRequest)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.ERROR.getCode())
                .jsonPath("$.msg")
                .isNotEmpty()
                .jsonPath("$.data")
                .doesNotExist();
    }

    @Test
    void testVerifyBackupCodeServiceError() {
        MfaVerifyRequest backupRequest = MfaVerifyRequest.builder()
                .userId(123L)
                .challengeToken("challenge-token-for-test")
                .backupCode("12345678")
                .tenantId("test-tenant")
                .build();

        when(totpService.verifyBackupCode(eq(123L), eq("12345678"), eq("test-tenant")))
                .thenReturn(reactor.core.publisher.Mono.error(new IllegalStateException("service unavailable")));

        webTestClient
                .post()
                .uri("/api/v1/mfa/verify-backup")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(backupRequest)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.ERROR.getCode())
                .jsonPath("$.msg")
                .isNotEmpty()
                .jsonPath("$.data")
                .doesNotExist();
    }

    @Test
    void testDisableMfaServiceError() {
        when(totpService.disable(eq(123L), eq("test-tenant")))
                .thenReturn(reactor.core.publisher.Mono.error(new IllegalStateException("service unavailable")));

        webTestClient
                .post()
                .uri("/api/v1/mfa/disable")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(verifyRequest)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.ERROR.getCode())
                .jsonPath("$.msg")
                .isNotEmpty()
                .jsonPath("$.data")
                .doesNotExist();
    }

    @Test
    void testGetMfaStatusEnabledServiceError() {
        when(totpService.isEnabled(eq(123L), eq("test-tenant")))
                .thenReturn(reactor.core.publisher.Mono.error(new IllegalStateException("service unavailable")));

        webTestClient
                .get()
                .uri("/api/v1/mfa/status?userId=123&tenantId=test-tenant")
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
    void testCodeRejected() {
        verifyRequest.setBackupCode("12345678");
        when(totpService.verifyCode(123L, "123456", "test-tenant")).thenReturn(reactor.core.publisher.Mono.just(false));
        webTestClient
                .post()
                .uri("/api/v1/mfa/verify-code")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(verifyRequest)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.ERROR.getCode())
                .jsonPath("$.msg")
                .isEqualTo("验证码错误")
                .jsonPath("$.data")
                .doesNotExist();
    }

    @Test
    void testBackupRejected() {
        verifyRequest.setBackupCode("12345678");
        when(totpService.verifyBackupCode(123L, "12345678", "test-tenant"))
                .thenReturn(reactor.core.publisher.Mono.just(false));
        webTestClient
                .post()
                .uri("/api/v1/mfa/verify-backup")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(verifyRequest)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.ERROR.getCode())
                .jsonPath("$.msg")
                .isEqualTo("备份码无效或已使用")
                .jsonPath("$.data")
                .doesNotExist();
    }

    @Test
    void testDisableRejected() {
        verifyRequest.setBackupCode("12345678");
        when(totpService.disable(123L, "test-tenant")).thenReturn(reactor.core.publisher.Mono.just(false));
        webTestClient
                .post()
                .uri("/api/v1/mfa/disable")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(verifyRequest)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.ERROR.getCode())
                .jsonPath("$.msg")
                .isEqualTo("禁用失败，MFA 未配置")
                .jsonPath("$.data")
                .doesNotExist();
    }

    @Test
    void testInvalidStatusUserId() {
        webTestClient
                .get()
                .uri("/api/v1/mfa/status?userId=invalid&tenantId=test-tenant")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.ERROR.getCode())
                .jsonPath("$.msg")
                .isEqualTo("userId 格式错误");
        org.mockito.Mockito.verifyNoInteractions(totpService);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"verify", "verify-code", "verify-backup", "disable"})
    void testVerificationRejectsInvalidFieldsBeforeCallingService(String endpoint) {
        verifyRequest.setTenantId(" ");
        webTestClient
                .post()
                .uri("/api/v1/mfa/" + endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(verifyRequest)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.PARAM_ERROR.getCode())
                .jsonPath("$.msg")
                .isNotEmpty()
                .jsonPath("$.data")
                .doesNotExist();
        org.mockito.Mockito.verifyNoInteractions(totpService);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"?tenantId=test-tenant", "?userId=123"})
    void testStatusMissingRequiredParameterCurrentlyReturnsServerError(String query) {
        webTestClient
                .get()
                .uri("/api/v1/mfa/status" + query)
                .exchange()
                .expectStatus()
                .isEqualTo(500);
        org.mockito.Mockito.verifyNoInteractions(totpService);
    }

    @Test
    void testBackupCodeInvalidLengthDoesNotCallService() {
        verifyRequest.setBackupCode("123456");
        webTestClient
                .post()
                .uri("/api/v1/mfa/verify-backup")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(verifyRequest)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.PARAM_ERROR.getCode());
        org.mockito.Mockito.verifyNoInteractions(totpService);
    }
}
