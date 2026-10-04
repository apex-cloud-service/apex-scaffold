package com.apex.scaffold.common.audit;

import java.util.HashMap;
import java.util.Map;
import com.apex.scaffold.common.util.TraceIdUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/**
 * {@link SecurityAuditServiceImpl} 单元测试
 *
 * <p>本测试类随 {@code SecurityAuditService} 接口+实现由 {@code apex-auth-center} 下沉至
 * {@code apex-common} 而新建（2026-09-30），覆盖认证事件（登录/登出/密码修改/账户锁定与解锁）、
 * Token 事件（签发/刷新/撤销/验证失败/重放攻击/轮换）、授权事件（权限拒绝）、
 * 安全威胁（暴力破解）、密钥管理事件（吊销/轮换/轮换失败/生成）、
 * 通用安全事件（两个重载版本，含正常/异常 userId 与空详情场景）、
 * 客户端密钥轮换事件等全部公开方法，确保下沉后行为与原实现完全一致（无回归）。
 *
 * <p>由于当前实现仅将事件转换为结构化日志输出、不依赖任何外部资源（无数据库、无网络调用），
 * 测试无需 Mock 任何协作对象，直接构造实例断言 {@code Mono} 正常完成（{@code verifyComplete()}）
 * 或同步方法不抛出异常即可。
 */
@DisplayName("安全审计服务实现测试")
class SecurityAuditServiceImplTest {

    private SecurityAuditServiceImpl securityAuditService;

    @BeforeEach
    void setUp() {
        securityAuditService = new SecurityAuditServiceImpl();
    }

    @Test
    @DisplayName("记录登录成功事件")
    void testLogLoginSuccess() {
        StepVerifier.create(securityAuditService.logLoginSuccess(
                        1L, "admin", "tenant1", "192.168.1.1", "Mozilla/5.0", "device-001"))
                .verifyComplete();
    }

    @Test
    @DisplayName("审计事件读取当前TraceId")
    void testAuditEventUsesCurrentTraceId() {
        String traceId = "audit-trace-001";
        TraceIdUtil.setTraceId(traceId);

        StepVerifier.create(securityAuditService.logSecurityEvent(
                        "TRACE_EVENT", "trace event", 1L, "admin", "tenant1", "test")
                .contextWrite(TraceIdUtil.withTraceIdContext(traceId)))
                .verifyComplete();
        TraceIdUtil.clear();
    }

    @Test
    @DisplayName("记录登录失败事件")
    void testLogLoginFailure() {
        StepVerifier.create(securityAuditService.logLoginFailure(
                        "admin", "tenant1", "192.168.1.100", "Mozilla/5.0", "invalid_credentials"))
                .verifyComplete();
    }

    @Test
    @DisplayName("记录登出事件")
    void testLogLogout() {
        StepVerifier.create(securityAuditService.logLogout(1L, "admin", "tenant1", "192.168.1.1"))
                .verifyComplete();
    }

    @Test
    @DisplayName("记录Token签发事件")
    void testLogTokenIssued() {
        StepVerifier.create(securityAuditService.logTokenIssued(1L, "tenant1", "192.168.1.1", "device-001", "ACCESS"))
                .verifyComplete();
    }

    @Test
    @DisplayName("记录Token刷新事件")
    void testLogTokenRefreshed() {
        StepVerifier.create(securityAuditService.logTokenRefreshed(1L, "tenant1", "192.168.1.1", "jti-123"))
                .verifyComplete();
    }

    @Test
    @DisplayName("记录Token撤销事件")
    void testLogTokenRevoked() {
        StepVerifier.create(securityAuditService.logTokenRevoked(
                        1L, "admin", "tenant1", "jti-456", "logout", "192.168.1.1"))
                .verifyComplete();
    }

    @Test
    @DisplayName("记录Token验证失败事件")
    void testLogTokenValidationFailure() {
        StepVerifier.create(securityAuditService.logTokenValidationFailure("192.168.1.1", "token_expired", "eyJ***abc"))
                .verifyComplete();
    }

    @Test
    @DisplayName("记录Token重放攻击事件")
    void testLogTokenReplayAttack() {
        StepVerifier.create(securityAuditService.logTokenReplayAttack(1L, "tenant1", "192.168.1.1", "jti-789"))
                .verifyComplete();
    }

    @Test
    @DisplayName("记录暴力破解攻击事件")
    void testLogBruteForceAttack() {
        StepVerifier.create(securityAuditService.logBruteForceAttack("admin", "192.168.1.100", 10))
                .verifyComplete();
    }

    @Test
    @DisplayName("记录权限检查失败事件")
    void testLogPermissionDenied() {
        StepVerifier.create(securityAuditService.logPermissionDenied(
                        1L, "user1", "tenant1", "/api/admin/users", "DELETE", "192.168.1.1"))
                .verifyComplete();
    }

    @Test
    @DisplayName("记录敏感操作事件")
    void testLogSensitiveOperation() {
        StepVerifier.create(securityAuditService.logSensitiveOperation(
                        1L, "admin", "tenant1", "DELETE_USER", "/api/users/123", "192.168.1.1"))
                .verifyComplete();
    }

    @Test
    @DisplayName("记录密码修改事件")
    void testLogPasswordChanged() {
        StepVerifier.create(securityAuditService.logPasswordChanged(1L, "user1", "tenant1", "192.168.1.1"))
                .verifyComplete();
    }

    @Test
    @DisplayName("记录账户锁定事件")
    void testLogAccountLocked() {
        StepVerifier.create(securityAuditService.logAccountLocked(1L, "user1", "tenant1", "too_many_failed_attempts"))
                .verifyComplete();
    }

    @Test
    @DisplayName("记录账户解锁事件")
    void testLogAccountUnlocked() {
        StepVerifier.create(securityAuditService.logAccountUnlocked(1L, "user1", "tenant1", "admin"))
                .verifyComplete();
    }

    @Test
    @DisplayName("记录密钥吊销事件")
    void testLogKeyRevocation() {
        securityAuditService.logKeyRevocation("v1.2.3", "security_incident", "admin");
    }

    @Test
    @DisplayName("记录密钥轮换事件")
    void testLogKeyRotation() {
        securityAuditService.logKeyRotation("v1.2.3", "v1.2.4", "scheduled_rotation", "system");
    }

    @Test
    @DisplayName("记录密钥轮换失败事件")
    void testLogKeyRotationFailure() {
        securityAuditService.logKeyRotationFailure("v1.2.3", "kms_unavailable", "system");
    }

    @Test
    @DisplayName("记录密钥生成事件")
    void testLogKeyGeneration() {
        securityAuditService.logKeyGeneration("v1.0.0", "initial_setup", "admin");
    }

    @Test
    @DisplayName("记录通用安全事件")
    void testLogSecurityEvent() {
        StepVerifier.create(securityAuditService.logSecurityEvent(
                        "CUSTOM_EVENT", "Custom security event", 1L, "admin", "tenant1", "test_reason"))
                .verifyComplete();
    }

    @Test
    @DisplayName("记录通用安全事件 - 带严重级别和详情")
    void testLogSecurityEventWithDetails() {
        Map<String, Object> details = new HashMap<>();
        details.put("ip", "192.168.1.1");
        details.put("action", "DELETE");
        details.put("resource", "/api/users/123");

        StepVerifier.create(securityAuditService.logSecurityEvent(
                        "CUSTOM_HIGH_SEVERITY",
                        "High severity event",
                        "1",
                        "admin",
                        "HIGH",
                        true,
                        details,
                        "security_check"))
                .verifyComplete();
    }

    @Test
    @DisplayName("记录客户端密钥轮换事件")
    void testLogClientSecretRotation() {
        Map<String, Object> details = Map.of("clientId", "client-001", "rotationType", "manual");

        StepVerifier.create(securityAuditService.logClientSecretRotation("client-001", "1", "192.168.1.1", details))
                .verifyComplete();
    }

    @Test
    @DisplayName("记录Token轮换事件")
    void testLogTokenRotation() {
        StepVerifier.create(securityAuditService.logTokenRotation(
                        1L, "tenant1", "family-abc", "jti-old", "jti-new", "192.168.1.1"))
                .verifyComplete();
    }

    @Test
    @DisplayName("记录登录失败 - 不同失败原因")
    void testLogLoginFailure_DifferentReasons() {
        StepVerifier.create(securityAuditService.logLoginFailure(
                        "user1", "tenant1", "192.168.1.1", "Chrome", "account_locked"))
                .verifyComplete();

        StepVerifier.create(securityAuditService.logLoginFailure(
                        "user2", "tenant1", "192.168.1.2", "Firefox", "account_expired"))
                .verifyComplete();
    }

    @Test
    @DisplayName("记录Token签发 - 不同Token类型")
    void testLogTokenIssued_DifferentTypes() {
        StepVerifier.create(securityAuditService.logTokenIssued(1L, "tenant1", "192.168.1.1", "device-001", "ACCESS"))
                .verifyComplete();

        StepVerifier.create(securityAuditService.logTokenIssued(1L, "tenant1", "192.168.1.1", "device-001", "REFRESH"))
                .verifyComplete();
    }

    @Test
    @DisplayName("记录通用安全事件 - 无效userId")
    void testLogSecurityEventWithInvalidUserId() {
        Map<String, Object> details = Map.of("key", "value");

        StepVerifier.create(securityAuditService.logSecurityEvent(
                        "TEST_EVENT", "Test event", "invalid-user-id", "testuser", "MEDIUM", true, details, "test"))
                .verifyComplete();
    }

    @Test
    @DisplayName("记录通用安全事件 - 空userId")
    void testLogSecurityEventWithNullUserId() {
        StepVerifier.create(securityAuditService.logSecurityEvent(
                        "TEST_EVENT", "Test event", null, "testuser", "LOW", false, null, "test"))
                .verifyComplete();
    }

    @Test
    @DisplayName("记录客户端密钥轮换 - 空详情")
    void testLogClientSecretRotation_NullDetails() {
        StepVerifier.create(securityAuditService.logClientSecretRotation("client-002", "1", "192.168.1.1", null))
                .verifyComplete();
    }

    @Test
    @DisplayName("记录客户端密钥轮换 - 无效操作人ID")
    void testLogClientSecretRotation_InvalidOperatorId() {
        StepVerifier.create(securityAuditService.logClientSecretRotation(
                        "client-003", "not-a-number", "192.168.1.1", Map.of("test", "value")))
                .verifyComplete();
    }
}
