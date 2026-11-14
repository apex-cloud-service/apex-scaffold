package com.apex.scaffold.authservice.interfaces.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.auth.application.dto.PasswordRecoveryConfirmRequest;
import com.apex.scaffold.auth.application.dto.PasswordRecoveryConfirmResponse;
import com.apex.scaffold.auth.application.dto.PasswordRecoveryInitiateRequest;
import com.apex.scaffold.auth.application.dto.PasswordRecoveryInitiateResponse;
import com.apex.scaffold.auth.application.dto.PasswordRecoveryVerifyRequest;
import com.apex.scaffold.auth.application.dto.PasswordRecoveryVerifyResponse;
import com.apex.scaffold.auth.application.service.PasswordRecoveryApplicationService;
import com.apex.scaffold.authservice.AuthCenterSliceTestConfiguration;
import com.apex.scaffold.authservice.interfaces.router.PasswordRecoveryRouter;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

/**
 * 自助密码找回 Handler 集成测试。
 *
 * <p>补齐 42.20 记录的缺口：恢复链路此前只有应用服务单测与 OpenAPI 契约测试，
 * 没有 handler 级集成测试。本测试以 {@code @WebFluxTest} 切片装配
 * {@link PasswordRecoveryRouter} + {@link PasswordRecoveryHandler}，验证三段式
 * 匿名端点的 HTTP 契约与错误映射：
 *
 * <ul>
 *   <li>申请、校验、确认三条端点的成功响应形态（{@code code=0} 与 data 字段）；</li>
 *   <li>限流 429、短信服务不可用 503、业务校验失败 400 的状态码映射；</li>
 *   <li>确认阶段撤销失败时 {@code sessionRevocation=PENDING} 不伪称全端退出；</li>
 *   <li>Handler 不向应用服务泄露客户端 IP 以外的环境信息，请求体按原样传递。</li>
 * </ul>
 *
 * @author Apex Security Team
 * @since 1.1.0
 * @see PasswordRecoveryHandler
 * @see PasswordRecoveryRouter
 */
@WebFluxTest(
        controllers = PasswordRecoveryHandler.class,
        excludeAutoConfiguration = {
            org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration.class,
            org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration.class
        })
@AutoConfigureWebTestClient
@Import({PasswordRecoveryRouter.class, AuthCenterSliceTestConfiguration.class})
@DisplayName("自助密码找回 Handler 集成测试")
class PasswordRecoveryHandlerIntegrationTest {

    /**
     * {@code WebTestClient} 在 {@code @WebFluxTest} 中无法通过构造器解析，
     * 故采用 JSR-250 {@code @Resource} 字段注入。
     */
    @Resource
    private WebTestClient webTestClient;

    @MockBean
    private PasswordRecoveryApplicationService recoveryService;

    @Test
    @DisplayName("OpenAPI 契约注册三个恢复端点")
    void shouldExposeAllOpenApiOperations(ApplicationContext context) {
        org.springdoc.core.annotations.RouterOperations operations = context.findAnnotationOnBean(
                "passwordRecoveryRoutes", org.springdoc.core.annotations.RouterOperations.class);
        assertThat(operations).isNotNull();
        assertThat(operations.value()).hasSize(3);
    }

    @Test
    @DisplayName("申请恢复单 - 成功返回恢复单ID与有效期")
    void testInitiateSuccess() {
        PasswordRecoveryInitiateResponse response = new PasswordRecoveryInitiateResponse();
        response.setRecoveryId("rec-1");
        response.setExpiresIn(600L);
        when(recoveryService.initiate(any(PasswordRecoveryInitiateRequest.class), anyString()))
                .thenReturn(Mono.just(response));

        webTestClient
                .post()
                .uri("/auth/v2/password-recovery/requests")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(initiateRequest("EMAIL", "user@example.com"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.recoveryId")
                .isEqualTo("rec-1")
                .jsonPath("$.data.expiresIn")
                .isEqualTo(600);
    }

    @Test
    @DisplayName("申请恢复单 - 联系方式透传应用服务且客户端 IP 来自连接")
    void testInitiatePassesContactAndClientIp() {
        PasswordRecoveryInitiateResponse response = new PasswordRecoveryInitiateResponse();
        response.setRecoveryId("rec-2");
        response.setExpiresIn(600L);
        when(recoveryService.initiate(any(PasswordRecoveryInitiateRequest.class), anyString()))
                .thenReturn(Mono.just(response));

        webTestClient
                .post()
                .uri("/auth/v2/password-recovery/requests")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(initiateRequest("SMS", "13800138000"))
                .exchange()
                .expectStatus()
                .isOk();

        ArgumentCaptor<String> clientIp = ArgumentCaptor.forClass(String.class);
        verify(recoveryService).initiate(eq(initiateRequest("SMS", "13800138000")), clientIp.capture());
        assertThat(clientIp.getValue()).isNotBlank();
    }

    @Test
    @DisplayName("申请恢复单 - 触发限流映射为 429")
    void testInitiateRateLimited() {
        when(recoveryService.initiate(any(PasswordRecoveryInitiateRequest.class), anyString()))
                .thenReturn(Mono.error(new BusinessException(ResultCode.RATE_LIMIT, "请求过于频繁，请稍后再试")));

        webTestClient
                .post()
                .uri("/auth/v2/password-recovery/requests")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(initiateRequest("EMAIL", "user@example.com"))
                .exchange()
                .expectStatus()
                .isEqualTo(429)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.RATE_LIMIT.getCode());
    }

    @Test
    @DisplayName("申请恢复单 - 短信服务不可用映射为 503 且不泄露内部错误")
    void testInitiateSmsServiceUnavailable() {
        when(recoveryService.initiate(any(PasswordRecoveryInitiateRequest.class), anyString()))
                .thenReturn(Mono.error(new BusinessException(ResultCode.SMS_SERVICE_ERROR, "验证码投递失败，请稍后重试")));

        webTestClient
                .post()
                .uri("/auth/v2/password-recovery/requests")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(initiateRequest("SMS", "13800138000"))
                .exchange()
                .expectStatus()
                .isEqualTo(503)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.SMS_SERVICE_ERROR.getCode())
                .jsonPath("$.msg")
                .isEqualTo("验证码投递失败，请稍后重试");
    }

    @Test
    @DisplayName("校验验证码 - 成功返回一次性确认令牌")
    void testVerifySuccess() {
        PasswordRecoveryVerifyResponse response = new PasswordRecoveryVerifyResponse();
        response.setConfirmToken("ct-1");
        response.setExpiresIn(600L);
        when(recoveryService.verify(any(PasswordRecoveryVerifyRequest.class))).thenReturn(Mono.just(response));

        webTestClient
                .post()
                .uri("/auth/v2/password-recovery/verifications")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(verifyRequest("rec-1", "482913"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.confirmToken")
                .isEqualTo("ct-1");
    }

    @Test
    @DisplayName("校验验证码 - 无效凭证返回 400 且不区分具体原因")
    void testVerifyRejectsInvalidCredential() {
        when(recoveryService.verify(any(PasswordRecoveryVerifyRequest.class)))
                .thenReturn(Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "验证码无效或已过期")));

        webTestClient
                .post()
                .uri("/auth/v2/password-recovery/verifications")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(verifyRequest("rec-1", "000000"))
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.PARAM_ERROR.getCode());
    }

    @Test
    @DisplayName("确认重设 - 成功且撤销状态为完成")
    void testConfirmSuccess() {
        PasswordRecoveryConfirmResponse response = new PasswordRecoveryConfirmResponse();
        response.setStatus("SUCCESS");
        response.setSessionRevocation("COMPLETED");
        when(recoveryService.confirm(any(PasswordRecoveryConfirmRequest.class))).thenReturn(Mono.just(response));

        webTestClient
                .post()
                .uri("/auth/v2/password-recovery/confirmations")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(confirmRequest("rec-1", "ct-1", "N3wPassw0rd!"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.status")
                .isEqualTo("SUCCESS")
                .jsonPath("$.data.sessionRevocation")
                .isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("确认重设 - 撤销失败如实返回 PENDING 而非伪称成功")
    void testConfirmReportsPendingRevocation() {
        PasswordRecoveryConfirmResponse response = new PasswordRecoveryConfirmResponse();
        response.setStatus("SUCCESS");
        response.setSessionRevocation("PENDING");
        when(recoveryService.confirm(any(PasswordRecoveryConfirmRequest.class))).thenReturn(Mono.just(response));

        webTestClient
                .post()
                .uri("/auth/v2/password-recovery/confirmations")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(confirmRequest("rec-1", "ct-1", "N3wPassw0rd!"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.sessionRevocation")
                .isEqualTo("PENDING");
    }

    /**
     * 构建恢复申请请求。
     *
     * @param channel 恢复通道
     * @param contact 联系方式
     * @return 请求 DTO
     */
    private static PasswordRecoveryInitiateRequest initiateRequest(String channel, String contact) {
        PasswordRecoveryInitiateRequest request = new PasswordRecoveryInitiateRequest();
        request.setChannel(channel);
        request.setContact(contact);
        return request;
    }

    /**
     * 构建验证码校验请求。
     *
     * @param recoveryId 恢复单 ID
     * @param code       一次性验证码
     * @return 请求 DTO
     */
    private static PasswordRecoveryVerifyRequest verifyRequest(String recoveryId, String code) {
        PasswordRecoveryVerifyRequest request = new PasswordRecoveryVerifyRequest();
        request.setRecoveryId(recoveryId);
        request.setCode(code);
        return request;
    }

    /**
     * 构建确认重设请求。
     *
     * @param recoveryId  恢复单 ID
     * @param confirmToken 一次性确认令牌
     * @param newPassword  新密码
     * @return 请求 DTO
     */
    private static PasswordRecoveryConfirmRequest confirmRequest(
            String recoveryId, String confirmToken, String newPassword) {
        PasswordRecoveryConfirmRequest request = new PasswordRecoveryConfirmRequest();
        request.setRecoveryId(recoveryId);
        request.setConfirmToken(confirmToken);
        request.setNewPassword(newPassword);
        return request;
    }
}
