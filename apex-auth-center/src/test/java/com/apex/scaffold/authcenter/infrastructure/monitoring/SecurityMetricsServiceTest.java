package com.apex.scaffold.authcenter.infrastructure.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("安全监控指标服务测试")
class SecurityMetricsServiceTest {

    private SecurityMetricsService service;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new SecurityMetricsService(meterRegistry);
    }

    @Test
    @DisplayName("记录认证成功")
    void testRecordAuthSuccess() {
        service.recordAuthSuccess("user1", "tenant1", "password");

        double count = meterRegistry.counter("auth.success.total").count();
        assertThat(count).isEqualTo(1.0);

        double methodCount = meterRegistry
                .counter("auth.success.by.method", "method", "password", "tenant", "tenant1")
                .count();
        assertThat(methodCount).isEqualTo(1.0);
    }

    @Test
    @DisplayName("记录认证失败")
    void testRecordAuthFailure() {
        String ipAddress = "192.168.1.1";
        service.recordAuthFailure("admin", "invalid_credentials", ipAddress);

        double count = meterRegistry
                .counter("auth.failure.total", "reason", "invalid_credentials")
                .count();
        assertThat(count).isEqualTo(1.0);

        int failCount = service.getFailedLoginAttempts(ipAddress);
        assertThat(failCount).isEqualTo(1);
    }

    @Test
    @DisplayName("记录多次认证失败")
    void testRecordMultipleAuthFailures() {
        String ipAddress = "192.168.1.100";

        service.recordAuthFailure("user1", "invalid_credentials", ipAddress);
        service.recordAuthFailure("user2", "invalid_credentials", ipAddress);
        service.recordAuthFailure("user3", "account_locked", ipAddress);

        int failCount = service.getFailedLoginAttempts(ipAddress);
        assertThat(failCount).isEqualTo(3);
    }

    @Test
    @DisplayName("记录Token颁发")
    void testRecordTokenIssued() {
        service.recordTokenIssued("user1", "access");

        double count = meterRegistry.counter("token.issued.total").count();
        assertThat(count).isEqualTo(1.0);

        double typeCount =
                meterRegistry.counter("token.issued.by.type", "type", "access").count();
        assertThat(typeCount).isEqualTo(1.0);
    }

    @Test
    @DisplayName("记录Token刷新")
    void testRecordTokenRefreshed() {
        service.recordTokenRefreshed("user1");

        double count = meterRegistry.counter("token.refreshed.total").count();
        assertThat(count).isEqualTo(1.0);
    }

    @Test
    @DisplayName("记录Token撤销")
    void testRecordTokenRevoked() {
        service.recordTokenRevoked("user1", "logout");

        double count = meterRegistry.counter("token.revoked.total").count();
        assertThat(count).isEqualTo(1.0);

        double reasonCount = meterRegistry
                .counter("token.revoked.by.reason", "reason", "logout")
                .count();
        assertThat(reasonCount).isEqualTo(1.0);
    }

    @Test
    @DisplayName("记录暴力破解检测")
    void testRecordBruteForceDetected() {
        service.recordBruteForceDetected("192.168.1.1", "login_attempts");

        double count =
                meterRegistry.counter("security.bruteforce.detected.total").count();
        assertThat(count).isEqualTo(1.0);

        double typeCount = meterRegistry
                .counter("security.bruteforce.by.type", "type", "login_attempts")
                .count();
        assertThat(typeCount).isEqualTo(1.0);
    }

    @Test
    @DisplayName("记录权限拒绝")
    void testRecordPermissionDenied() {
        service.recordPermissionDenied("user1", "/api/admin", "read");

        double count = meterRegistry.counter("permission.denied.total").count();
        assertThat(count).isEqualTo(1.0);

        double resourceCount = meterRegistry
                .counter("permission.denied.by.resource", "resource", "/api/admin", "action", "read")
                .count();
        assertThat(resourceCount).isEqualTo(1.0);
    }

    @Test
    @DisplayName("记录认证耗时")
    void testRecordAuthDuration() {
        Duration duration = Duration.ofMillis(100);
        service.recordAuthDuration(duration, true);

        long count = meterRegistry.timer("auth.duration").count();
        assertThat(count).isEqualTo(1);

        long successCount = meterRegistry
                .timer("auth.duration.by.result", "success", "true")
                .count();
        assertThat(successCount).isEqualTo(1);
    }

    @Test
    @DisplayName("记录Token验证耗时")
    void testRecordTokenValidationDuration() {
        Duration duration = Duration.ofMillis(50);
        service.recordTokenValidationDuration(duration, true);

        long count = meterRegistry.timer("token.validation.duration").count();
        assertThat(count).isEqualTo(1);

        long validCount = meterRegistry
                .timer("token.validation.duration.by.result", "valid", "true")
                .count();
        assertThat(validCount).isEqualTo(1);
    }

    @Test
    @DisplayName("记录权限检查耗时")
    void testRecordPermissionCheckDuration() {
        Duration duration = Duration.ofMillis(20);
        service.recordPermissionCheckDuration(duration, false);

        long count = meterRegistry.timer("permission.check.duration").count();
        assertThat(count).isEqualTo(1);

        long deniedCount = meterRegistry
                .timer("permission.check.duration.by.result", "allowed", "false")
                .count();
        assertThat(deniedCount).isEqualTo(1);
    }

    @Test
    @DisplayName("记录用户登出 - 减少活跃会话数")
    void testRecordUserLogout() {
        String userId = "user1";

        service.recordAuthSuccess(userId, "tenant1", "password");
        int sessionsBefore = service.getActiveSessionCount(userId);
        assertThat(sessionsBefore).isGreaterThan(0);

        service.recordUserLogout(userId);
        int sessionsAfter = service.getActiveSessionCount(userId);
        assertThat(sessionsAfter).isLessThan(sessionsBefore);
    }

    @Test
    @DisplayName("清除IP失败记录")
    void testClearFailedLoginAttempts() {
        String ipAddress = "10.0.0.1";

        service.recordAuthFailure("user1", "invalid_credentials", ipAddress);
        assertThat(service.getFailedLoginAttempts(ipAddress)).isEqualTo(1);

        service.clearFailedLoginAttempts(ipAddress);
        assertThat(service.getFailedLoginAttempts(ipAddress)).isEqualTo(0);
    }

    @Test
    @DisplayName("获取IP失败登录次数 - 无记录时返回0")
    void testGetFailedLoginAttempts_NoRecord() {
        int count = service.getFailedLoginAttempts("192.168.99.99");
        assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("获取用户活跃会话数 - 无记录时返回0")
    void testGetActiveSessionCount_NoRecord() {
        int count = service.getActiveSessionCount("non-existent-user");
        assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("记录多种Token类型")
    void testRecordMultipleTokenTypes() {
        service.recordTokenIssued("user1", "access");
        service.recordTokenIssued("user1", "refresh");
        service.recordTokenIssued("user2", "access");

        double totalCount = meterRegistry.counter("token.issued.total").count();
        assertThat(totalCount).isEqualTo(3.0);

        double accessCount =
                meterRegistry.counter("token.issued.by.type", "type", "access").count();
        assertThat(accessCount).isEqualTo(2.0);

        double refreshCount =
                meterRegistry.counter("token.issued.by.type", "type", "refresh").count();
        assertThat(refreshCount).isEqualTo(1.0);
    }

    @Test
    @DisplayName("记录不同认证方式")
    void testRecordDifferentAuthMethods() {
        service.recordAuthSuccess("user1", "tenant1", "password");
        service.recordAuthSuccess("user2", "tenant1", "sms");
        service.recordAuthSuccess("user3", "tenant1", "biometric");

        double passwordCount = meterRegistry
                .counter("auth.success.by.method", "method", "password", "tenant", "tenant1")
                .count();
        assertThat(passwordCount).isEqualTo(1.0);

        double smsCount = meterRegistry
                .counter("auth.success.by.method", "method", "sms", "tenant", "tenant1")
                .count();
        assertThat(smsCount).isEqualTo(1.0);
    }

    @Test
    @DisplayName("记录不同失败原因")
    void testRecordDifferentFailureReasons() {
        service.recordAuthFailure("user1", "invalid_credentials", "192.168.1.1");
        service.recordAuthFailure("user2", "account_locked", "192.168.1.2");
        service.recordAuthFailure("user3", "account_expired", "192.168.1.3");

        double invalidCredsCount = meterRegistry
                .counter("auth.failure.by.reason", "reason", "invalid_credentials")
                .count();
        assertThat(invalidCredsCount).isEqualTo(1.0);

        double lockedCount = meterRegistry
                .counter("auth.failure.by.reason", "reason", "account_locked")
                .count();
        assertThat(lockedCount).isEqualTo(1.0);
    }

    @Test
    @DisplayName("用户登出 - 会话数为0时不减少")
    void testRecordUserLogout_ZeroSessions() {
        String userId = "user-without-sessions";

        service.recordUserLogout(userId);
        int sessions = service.getActiveSessionCount(userId);
        assertThat(sessions).isEqualTo(0);
    }
}
