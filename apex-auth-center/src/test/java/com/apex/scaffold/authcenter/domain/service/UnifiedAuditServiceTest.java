package com.apex.scaffold.authcenter.domain.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStreamOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UnifiedAuditService 统一审计服务测试")
class UnifiedAuditServiceTest {

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Mock
    private ReactiveStreamOperations<String, Object, Object> streamOperations;

    private ObjectMapper objectMapper;

    private UnifiedAuditService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        service = new UnifiedAuditService(redisTemplate, objectMapper);
        lenient().when(redisTemplate.opsForStream()).thenReturn(streamOperations);
    }

    @Test
    @DisplayName("记录认证事件 - 登录成功")
    void testLogAuthenticationEvent_LoginSuccess() {
        when(streamOperations.add(anyString(), anyMap())).thenAnswer(invocation -> Mono.just(RecordId.of("1-0")));

        Map<String, Object> details = new HashMap<>();
        details.put("loginMethod", "password");

        StepVerifier.create(service.logAuthenticationEvent(
                        "LOGIN", "user-123", "tenant-1", "192.168.1.1", true, null, details))
                .assertNext(auditLog -> {
                    assertNotNull(auditLog.getEventId());
                    assertEquals("AUTHENTICATION", auditLog.getEventType());
                    assertEquals("LOGIN", auditLog.getAction());
                    assertEquals("user-123", auditLog.getUserId());
                    assertEquals("tenant-1", auditLog.getTenantId());
                    assertEquals("192.168.1.1", auditLog.getClientIp());
                    assertTrue(auditLog.isSuccess());
                    assertNull(auditLog.getErrorMessage());
                    assertNotNull(auditLog.getTimestamp());
                    assertNotNull(auditLog.getTraceId());
                })
                .verifyComplete();

        verify(streamOperations).add(eq("audit:stream"), anyMap());
    }

    @Test
    @DisplayName("记录认证事件 - 登录失败")
    void testLogAuthenticationEvent_LoginFailed() {
        when(streamOperations.add(anyString(), anyMap())).thenAnswer(invocation -> Mono.just(RecordId.of("1-0")));

        StepVerifier.create(service.logAuthenticationEvent(
                        "LOGIN", "user-123", "tenant-1", "192.168.1.1", false, "Invalid password", null))
                .assertNext(auditLog -> {
                    assertEquals("AUTHENTICATION", auditLog.getEventType());
                    assertEquals("LOGIN", auditLog.getAction());
                    assertFalse(auditLog.isSuccess());
                    assertEquals("Invalid password", auditLog.getErrorMessage());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("记录认证事件 - 密码修改")
    void testLogAuthenticationEvent_PasswordChange() {
        when(streamOperations.add(anyString(), anyMap())).thenAnswer(invocation -> Mono.just(RecordId.of("1-0")));

        Map<String, Object> details = new HashMap<>();
        details.put("passwordStrength", "strong");

        StepVerifier.create(service.logAuthenticationEvent(
                        "PASSWORD_CHANGE", "user-456", "tenant-1", "10.0.0.1", true, null, details))
                .assertNext(auditLog -> {
                    assertEquals("PASSWORD_CHANGE", auditLog.getAction());
                    assertTrue(auditLog.isSuccess());
                    assertEquals("strong", auditLog.getDetails().get("passwordStrength"));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("记录授权事件 - 权限检查成功")
    void testLogAuthorizationEvent_PermissionCheckSuccess() {
        when(streamOperations.add(anyString(), anyMap())).thenAnswer(invocation -> Mono.just(RecordId.of("1-0")));

        Map<String, Object> details = new HashMap<>();
        details.put("permission", "user:read");

        StepVerifier.create(service.logAuthorizationEvent(
                        "PERMISSION_CHECK", "user-123", "tenant-1", "resource-abc", true, details))
                .assertNext(auditLog -> {
                    assertEquals("AUTHORIZATION", auditLog.getEventType());
                    assertEquals("PERMISSION_CHECK", auditLog.getAction());
                    assertTrue(auditLog.isSuccess());
                    assertEquals("resource-abc", auditLog.getDetails().get("resourceId"));
                    assertEquals("user:read", auditLog.getDetails().get("permission"));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("记录授权事件 - 权限检查失败")
    void testLogAuthorizationEvent_PermissionCheckFailed() {
        when(streamOperations.add(anyString(), anyMap())).thenAnswer(invocation -> Mono.just(RecordId.of("1-0")));

        StepVerifier.create(service.logAuthorizationEvent(
                        "PERMISSION_CHECK", "user-123", "tenant-1", "resource-xyz", false, null))
                .assertNext(auditLog -> {
                    assertEquals("AUTHORIZATION", auditLog.getEventType());
                    assertFalse(auditLog.isSuccess());
                    assertEquals("resource-xyz", auditLog.getDetails().get("resourceId"));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("记录OAuth2事件 - Token颁发")
    void testLogOAuth2Event_TokenIssue() {
        when(streamOperations.add(anyString(), anyMap())).thenAnswer(invocation -> Mono.just(RecordId.of("1-0")));

        Map<String, Object> details = new HashMap<>();
        details.put("grantType", "authorization_code");

        StepVerifier.create(service.logOAuth2Event(
                        "TOKEN_ISSUE", "user-123", "tenant-1", "client-abc", "192.168.1.1", true, details))
                .assertNext(auditLog -> {
                    assertEquals("OAUTH2", auditLog.getEventType());
                    assertEquals("TOKEN_ISSUE", auditLog.getAction());
                    assertEquals("client-abc", auditLog.getDetails().get("clientId"));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("记录SSO事件 - SAML认证成功")
    void testLogSsoEvent_SamlAuth() {
        when(streamOperations.add(anyString(), anyMap())).thenAnswer(invocation -> Mono.just(RecordId.of("1-0")));

        Map<String, Object> details = new HashMap<>();
        details.put("assertionId", "saml-123");

        StepVerifier.create(service.logSsoEvent(
                        "SAML_AUTH", "user-123", "tenant-1", "SAML", "Okta", "192.168.1.1", true, null, details))
                .assertNext(auditLog -> {
                    assertEquals("SSO", auditLog.getEventType());
                    assertEquals("SAML_AUTH", auditLog.getAction());
                    assertEquals("SAML", auditLog.getDetails().get("protocol"));
                    assertEquals("Okta", auditLog.getDetails().get("idpName"));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("记录安全事件 - 登录失败（中等严重级别）")
    void testLogSecurityEvent_LoginFailedMedium() {
        when(streamOperations.add(anyString(), anyMap())).thenAnswer(invocation -> Mono.just(RecordId.of("1-0")));

        Map<String, Object> details = new HashMap<>();
        details.put("attemptCount", 3);

        StepVerifier.create(service.logSecurityEvent(
                        "LOGIN_FAILED",
                        "user-123",
                        "tenant-1",
                        "192.168.1.1",
                        "MEDIUM",
                        "Too many failed attempts",
                        details))
                .assertNext(auditLog -> {
                    assertEquals("SECURITY", auditLog.getEventType());
                    assertEquals("LOGIN_FAILED", auditLog.getAction());
                    assertEquals("MEDIUM", auditLog.getDetails().get("severity"));
                    assertFalse(auditLog.isSuccess());
                })
                .verifyComplete();

        verify(streamOperations).add(eq("audit:stream"), anyMap());
        verify(streamOperations, never()).add(eq("audit:security:alert"), anyMap());
    }

    @Test
    @DisplayName("记录安全事件 - 暴力破解（高严重级别）")
    void testLogSecurityEvent_BruteForceHigh() {
        when(streamOperations.add(anyString(), anyMap())).thenAnswer(invocation -> Mono.just(RecordId.of("1-0")));

        Map<String, Object> details = new HashMap<>();
        details.put("attemptCount", 50);

        StepVerifier.create(service.logSecurityEvent(
                        "BRUTE_FORCE",
                        "user-456",
                        "tenant-1",
                        "10.0.0.1",
                        "HIGH",
                        "Brute force attack detected",
                        details))
                .assertNext(auditLog -> {
                    assertEquals("BRUTE_FORCE", auditLog.getAction());
                    assertEquals("HIGH", auditLog.getDetails().get("severity"));
                })
                .verifyComplete();

        verify(streamOperations).add(eq("audit:stream"), anyMap());
        verify(streamOperations).add(eq("audit:security:alert"), anyMap());
    }

    @Test
    @DisplayName("记录系统事件 - 密钥轮换成功")
    void testLogSystemEvent_KeyRotationSuccess() {
        when(streamOperations.add(anyString(), anyMap())).thenAnswer(invocation -> Mono.just(RecordId.of("1-0")));

        Map<String, Object> details = new HashMap<>();
        details.put("keyType", "JWT_SIGNING_KEY");

        StepVerifier.create(service.logSystemEvent("KEY_ROTATION", "system-admin", true, details))
                .assertNext(auditLog -> {
                    assertEquals("SYSTEM", auditLog.getEventType());
                    assertEquals("KEY_ROTATION", auditLog.getAction());
                    assertEquals("system-admin", auditLog.getUserId());
                    assertTrue(auditLog.isSuccess());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("审计日志记录失败 - Redis异常不影响业务")
    void testLogEvent_RedisFailure() {
        when(streamOperations.add(anyString(), anyMap()))
                .thenAnswer(invocation -> Mono.error(new RuntimeException("Redis connection failed")));

        StepVerifier.create(service.logAuthenticationEvent(
                        "LOGIN", "user-123", "tenant-1", "192.168.1.1", true, null, null))
                .verifyComplete();

        verify(streamOperations).add(eq("audit:stream"), anyMap());
    }

    @Test
    @DisplayName("安全告警发送失败 - 不影响主流程")
    void testLogSecurityEvent_AlertFailure() {
        when(streamOperations.add(eq("audit:stream"), anyMap()))
                .thenAnswer(invocation -> Mono.just(RecordId.of("1-0")));
        when(streamOperations.add(eq("audit:security:alert"), anyMap()))
                .thenAnswer(invocation -> Mono.error(new RuntimeException("Alert stream failed")));

        StepVerifier.create(service.logSecurityEvent(
                        "BRUTE_FORCE", "user-123", "tenant-1", "192.168.1.1", "HIGH", "Attack detected", null))
                .assertNext(auditLog -> {
                    assertEquals("BRUTE_FORCE", auditLog.getAction());
                })
                .verifyComplete();

        verify(streamOperations).add(eq("audit:stream"), anyMap());
        verify(streamOperations).add(eq("audit:security:alert"), anyMap());
    }

    @Test
    @DisplayName("审计日志包含完整字段")
    void testAuditLog_CompleteFields() {
        when(streamOperations.add(anyString(), anyMap())).thenAnswer(invocation -> Mono.just(RecordId.of("1-0")));

        Map<String, Object> details = new HashMap<>();
        details.put("key1", "value1");
        details.put("key2", 123);

        StepVerifier.create(service.logAuthenticationEvent(
                        "LOGIN", "user-999", "tenant-999", "203.0.113.1", true, null, details))
                .assertNext(auditLog -> {
                    assertNotNull(auditLog.getEventId());
                    assertNotNull(auditLog.getEventType());
                    assertNotNull(auditLog.getAction());
                    assertNotNull(auditLog.getUserId());
                    assertNotNull(auditLog.getTenantId());
                    assertNotNull(auditLog.getClientIp());
                    assertNotNull(auditLog.getTimestamp());
                    assertNotNull(auditLog.getTraceId());
                    assertNotNull(auditLog.getDetails());
                    assertEquals(2, auditLog.getDetails().size());
                })
                .verifyComplete();
    }
}
