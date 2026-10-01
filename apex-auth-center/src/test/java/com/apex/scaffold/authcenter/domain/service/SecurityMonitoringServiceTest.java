package com.apex.scaffold.authcenter.domain.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("安全监控服务测试")
class SecurityMonitoringServiceTest {

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Mock
    private UnifiedAuditService auditService;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    private SecurityMonitoringService service;

    @BeforeEach
    void setUp() {
        service = new SecurityMonitoringService(redisTemplate, auditService);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("记录登录成功 - 首次记录")
    void testRecordLoginSuccess_FirstRecord() {
        String userId = "user-123";
        String clientIp = "192.168.1.100";

        when(valueOperations.increment("metric:login:success")).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(eq("metric:login:success"), any(Duration.class)))
                .thenReturn(Mono.just(true));
        when(valueOperations.increment("metric:ip:activity:" + clientIp)).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(eq("metric:ip:activity:" + clientIp), any(Duration.class)))
                .thenReturn(Mono.just(true));

        StepVerifier.create(service.recordLoginSuccess(userId, clientIp)).verifyComplete();

        verify(valueOperations).increment("metric:login:success");
        verify(redisTemplate).expire(eq("metric:login:success"), any(Duration.class));
    }

    @Test
    @DisplayName("记录登录成功 - 非首次记录")
    void testRecordLoginSuccess_SubsequentRecord() {
        String userId = "user-123";
        String clientIp = "192.168.1.100";

        when(valueOperations.increment("metric:login:success")).thenReturn(Mono.just(5L));
        when(valueOperations.increment("metric:ip:activity:" + clientIp)).thenReturn(Mono.just(2L));

        StepVerifier.create(service.recordLoginSuccess(userId, clientIp)).verifyComplete();

        verify(valueOperations).increment("metric:login:success");
        verify(redisTemplate, never()).expire(eq("metric:login:success"), any(Duration.class));
    }

    @Test
    @DisplayName("记录登录失败 - 失败率未超过阈值")
    void testRecordLoginFailure_BelowThreshold() {
        String username = "testuser";
        String clientIp = "192.168.1.100";

        when(valueOperations.increment("metric:login:fail")).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(eq("metric:login:fail"), any(Duration.class))).thenReturn(Mono.just(true));
        when(valueOperations.increment("metric:ip:activity:" + clientIp)).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(eq("metric:ip:activity:" + clientIp), any(Duration.class)))
                .thenReturn(Mono.just(true));
        when(valueOperations.get("metric:login:success")).thenReturn(Mono.just("10"));
        when(valueOperations.get("metric:login:fail")).thenReturn(Mono.just("1"));

        StepVerifier.create(service.recordLoginFailure(username, clientIp)).verifyComplete();

        verify(valueOperations).increment("metric:login:fail");
        verify(auditService, never())
                .logSecurityEvent(
                        anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    @DisplayName("记录登录失败 - 失败率超过阈值触发告警")
    void testRecordLoginFailure_ExceedsThreshold() {
        String username = "testuser";
        String clientIp = "192.168.1.100";

        when(valueOperations.increment("metric:login:fail")).thenReturn(Mono.just(5L));
        when(valueOperations.increment("metric:ip:activity:" + clientIp)).thenReturn(Mono.just(5L));
        when(valueOperations.get("metric:login:success")).thenReturn(Mono.just("5"));
        when(valueOperations.get("metric:login:fail")).thenReturn(Mono.just("5"));
        when(auditService.logSecurityEvent(
                        eq("LOGIN_FAILURE_RATE_HIGH"), isNull(), isNull(), isNull(), eq("HIGH"), anyString(), anyMap()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.recordLoginFailure(username, clientIp)).verifyComplete();

        verify(auditService)
                .logSecurityEvent(
                        eq("LOGIN_FAILURE_RATE_HIGH"), isNull(), isNull(), isNull(), eq("HIGH"), anyString(), anyMap());
    }

    @Test
    @DisplayName("记录Token验证成功")
    void testRecordTokenValidateSuccess() {
        String tokenId = "token-123";

        when(valueOperations.increment("metric:token:validate:success")).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(eq("metric:token:validate:success"), any(Duration.class)))
                .thenReturn(Mono.just(true));

        StepVerifier.create(service.recordTokenValidateSuccess(tokenId)).verifyComplete();

        verify(valueOperations).increment("metric:token:validate:success");
    }

    @Test
    @DisplayName("记录Token验证失败 - 未超过阈值")
    void testRecordTokenValidateFail_BelowThreshold() {
        String tokenId = "token-123";
        String reason = "expired";

        when(valueOperations.increment("metric:token:validate:fail")).thenReturn(Mono.just(50L));

        StepVerifier.create(service.recordTokenValidateFail(tokenId, reason)).verifyComplete();

        verify(valueOperations).increment("metric:token:validate:fail");
        verify(auditService, never())
                .logSecurityEvent(
                        anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    @DisplayName("记录Token验证失败 - 超过阈值触发告警")
    void testRecordTokenValidateFail_ExceedsThreshold() {
        String tokenId = "token-123";
        String reason = "expired";

        when(valueOperations.increment("metric:token:validate:fail")).thenReturn(Mono.just(100L));
        when(auditService.logSecurityEvent(
                        eq("TOKEN_VALIDATE_FAIL_SPIKE"),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq("HIGH"),
                        anyString(),
                        anyMap()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.recordTokenValidateFail(tokenId, reason)).verifyComplete();

        verify(auditService)
                .logSecurityEvent(
                        eq("TOKEN_VALIDATE_FAIL_SPIKE"),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq("HIGH"),
                        anyString(),
                        anyMap());
    }

    @Test
    @DisplayName("记录用户锁定 - 未超过阈值")
    void testRecordUserLocked_BelowThreshold() {
        String userId = "user-123";
        String reason = "多次登录失败";

        when(valueOperations.increment("metric:user:locked")).thenReturn(Mono.just(5L));

        StepVerifier.create(service.recordUserLocked(userId, reason)).verifyComplete();

        verify(valueOperations).increment("metric:user:locked");
        verify(auditService, never())
                .logSecurityEvent(
                        anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    @DisplayName("记录用户锁定 - 超过阈值触发告警")
    void testRecordUserLocked_ExceedsThreshold() {
        String userId = "user-123";
        String reason = "多次登录失败";

        when(valueOperations.increment("metric:user:locked")).thenReturn(Mono.just(10L));
        when(auditService.logSecurityEvent(
                        eq("USER_LOCKED_SPIKE"), isNull(), isNull(), isNull(), eq("MEDIUM"), anyString(), anyMap()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.recordUserLocked(userId, reason)).verifyComplete();

        verify(auditService)
                .logSecurityEvent(
                        eq("USER_LOCKED_SPIKE"), isNull(), isNull(), isNull(), eq("MEDIUM"), anyString(), anyMap());
    }

    @Test
    @DisplayName("记录Token重放攻击")
    void testRecordTokenReplay() {
        String tokenId = "token-123";
        String userId = "user-123";

        when(valueOperations.increment("metric:token:replay")).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(eq("metric:token:replay"), any(Duration.class)))
                .thenReturn(Mono.just(true));
        when(auditService.logSecurityEvent(
                        eq("TOKEN_REPLAY_DETECTED"),
                        eq(userId),
                        isNull(),
                        isNull(),
                        eq("CRITICAL"),
                        anyString(),
                        anyMap()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.recordTokenReplay(tokenId, userId)).verifyComplete();

        verify(auditService)
                .logSecurityEvent(
                        eq("TOKEN_REPLAY_DETECTED"),
                        eq(userId),
                        isNull(),
                        isNull(),
                        eq("CRITICAL"),
                        anyString(),
                        anyMap());
    }

    @Test
    @DisplayName("记录IP活动 - 超过阈值触发告警")
    void testRecordIpActivity_ExceedsThreshold() {
        String userId = "user-123";
        String clientIp = "192.168.1.100";

        when(valueOperations.increment("metric:login:success")).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        when(valueOperations.increment("metric:ip:activity:" + clientIp)).thenReturn(Mono.just(20L));
        when(auditService.logSecurityEvent(
                        eq("IP_ACTIVITY_SPIKE"), isNull(), isNull(), eq(clientIp), eq("MEDIUM"), anyString(), anyMap()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.recordLoginSuccess(userId, clientIp)).verifyComplete();

        verify(auditService)
                .logSecurityEvent(
                        eq("IP_ACTIVITY_SPIKE"), isNull(), isNull(), eq(clientIp), eq("MEDIUM"), anyString(), anyMap());
    }

    @Test
    @DisplayName("获取安全指标 - 所有指标均有数据")
    void testGetSecurityMetrics_WithData() {
        when(valueOperations.get("metric:login:success")).thenReturn(Mono.just("100"));
        when(valueOperations.get("metric:login:fail")).thenReturn(Mono.just("20"));
        when(valueOperations.get("metric:token:validate:success")).thenReturn(Mono.just("500"));
        when(valueOperations.get("metric:token:validate:fail")).thenReturn(Mono.just("50"));
        when(valueOperations.get("metric:user:locked")).thenReturn(Mono.just("5"));
        when(valueOperations.get("metric:token:replay")).thenReturn(Mono.just("2"));

        StepVerifier.create(service.getSecurityMetrics())
                .assertNext(metrics -> {
                    assertEquals(100L, metrics.getLoginSuccess());
                    assertEquals(20L, metrics.getLoginFail());
                    assertEquals(500L, metrics.getTokenValidateSuccess());
                    assertEquals(50L, metrics.getTokenValidateFail());
                    assertEquals(5L, metrics.getUserLocked());
                    assertEquals(2L, metrics.getTokenReplay());
                    assertNotNull(metrics.getTimestamp());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("获取安全指标 - 无数据返回默认值")
    void testGetSecurityMetrics_NoData() {
        when(valueOperations.get(anyString())).thenReturn(Mono.empty());

        StepVerifier.create(service.getSecurityMetrics())
                .assertNext(metrics -> {
                    assertEquals(0L, metrics.getLoginSuccess());
                    assertEquals(0L, metrics.getLoginFail());
                    assertEquals(0L, metrics.getTokenValidateSuccess());
                    assertEquals(0L, metrics.getTokenValidateFail());
                    assertEquals(0L, metrics.getUserLocked());
                    assertEquals(0L, metrics.getTokenReplay());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("SecurityMetrics - 计算登录失败率")
    void testSecurityMetrics_LoginFailureRate() {
        SecurityMonitoringService.SecurityMetrics metrics =
                new SecurityMonitoringService.SecurityMetrics(80, 20, 0, 0, 0, 0, Instant.now());

        assertEquals(0.20, metrics.getLoginFailureRate(), 0.001);
    }

    @Test
    @DisplayName("SecurityMetrics - 登录失败率为0（无登录）")
    void testSecurityMetrics_LoginFailureRate_NoLogins() {
        SecurityMonitoringService.SecurityMetrics metrics =
                new SecurityMonitoringService.SecurityMetrics(0, 0, 0, 0, 0, 0, Instant.now());

        assertEquals(0.0, metrics.getLoginFailureRate(), 0.001);
    }

    @Test
    @DisplayName("SecurityMetrics - 计算Token验证失败率")
    void testSecurityMetrics_TokenValidateFailureRate() {
        SecurityMonitoringService.SecurityMetrics metrics =
                new SecurityMonitoringService.SecurityMetrics(0, 0, 900, 100, 0, 0, Instant.now());

        assertEquals(0.10, metrics.getTokenValidateFailureRate(), 0.001);
    }

    @Test
    @DisplayName("SecurityMetrics - Token验证失败率为0（无验证）")
    void testSecurityMetrics_TokenValidateFailureRate_NoValidations() {
        SecurityMonitoringService.SecurityMetrics metrics =
                new SecurityMonitoringService.SecurityMetrics(0, 0, 0, 0, 0, 0, Instant.now());

        assertEquals(0.0, metrics.getTokenValidateFailureRate(), 0.001);
    }

    @Test
    @DisplayName("SecurityMetrics - 转换为Map")
    void testSecurityMetrics_ToMap() {
        SecurityMonitoringService.SecurityMetrics metrics =
                new SecurityMonitoringService.SecurityMetrics(80, 20, 900, 100, 5, 2, Instant.now());

        Map<String, Object> map = metrics.toMap();

        assertEquals(80L, map.get("loginSuccess"));
        assertEquals(20L, map.get("loginFail"));
        assertEquals("20.00%", map.get("loginFailureRate"));
        assertEquals(900L, map.get("tokenValidateSuccess"));
        assertEquals(100L, map.get("tokenValidateFail"));
        assertEquals("10.00%", map.get("tokenValidateFailureRate"));
        assertEquals(5L, map.get("userLocked"));
        assertEquals(2L, map.get("tokenReplay"));
        assertNotNull(map.get("timestamp"));
    }

    @Test
    @DisplayName("获取异常IP列表 - 有异常IP")
    void testGetTopAbnormalIps_WithAbnormalIps() {
        when(redisTemplate.keys("metric:ip:activity:*"))
                .thenReturn(Flux.just(
                        "metric:ip:activity:192.168.1.100",
                        "metric:ip:activity:192.168.1.101",
                        "metric:ip:activity:192.168.1.102"));

        when(valueOperations.get("metric:ip:activity:192.168.1.100")).thenReturn(Mono.just("50"));
        when(valueOperations.get("metric:ip:activity:192.168.1.101")).thenReturn(Mono.just("30"));
        when(valueOperations.get("metric:ip:activity:192.168.1.102")).thenReturn(Mono.just("25"));

        StepVerifier.create(service.getTopAbnormalIps(2))
                .assertNext(metric -> {
                    assertEquals("192.168.1.100", metric.getIp());
                    assertEquals(50L, metric.getCount());
                })
                .assertNext(metric -> {
                    assertEquals("192.168.1.101", metric.getIp());
                    assertEquals(30L, metric.getCount());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("获取异常IP列表 - 过滤低于阈值的IP")
    void testGetTopAbnormalIps_FilterBelowThreshold() {
        when(redisTemplate.keys("metric:ip:activity:*"))
                .thenReturn(Flux.just("metric:ip:activity:192.168.1.100", "metric:ip:activity:192.168.1.101"));

        when(valueOperations.get("metric:ip:activity:192.168.1.100")).thenReturn(Mono.just("25"));
        when(valueOperations.get("metric:ip:activity:192.168.1.101")).thenReturn(Mono.just("10"));

        StepVerifier.create(service.getTopAbnormalIps(10))
                .assertNext(metric -> {
                    assertEquals("192.168.1.100", metric.getIp());
                    assertEquals(25L, metric.getCount());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("获取异常IP列表 - 无异常IP")
    void testGetTopAbnormalIps_NoAbnormalIps() {
        when(redisTemplate.keys("metric:ip:activity:*")).thenReturn(Flux.empty());

        StepVerifier.create(service.getTopAbnormalIps(10)).verifyComplete();
    }

    @Test
    @DisplayName("定期同步指标到日志 - 有数据")
    void testSyncMetricsToLog_WithData() {
        when(valueOperations.increment("metric:login:success")).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        when(valueOperations.increment("metric:ip:activity:192.168.1.1")).thenReturn(Mono.just(1L));
        when(valueOperations.increment("metric:login:fail")).thenReturn(Mono.just(1L));
        when(valueOperations.increment("metric:ip:activity:192.168.1.2")).thenReturn(Mono.just(1L));
        when(valueOperations.get("metric:login:success")).thenReturn(Mono.just("10"));
        when(valueOperations.get("metric:login:fail")).thenReturn(Mono.just("1"));

        service.recordLoginSuccess("user1", "192.168.1.1").block();
        service.recordLoginFailure("user2", "192.168.1.2").block();

        assertDoesNotThrow(() -> service.syncMetricsToLog());
    }

    @Test
    @DisplayName("定期同步指标到日志 - 无数据")
    void testSyncMetricsToLog_NoData() {
        assertDoesNotThrow(() -> service.syncMetricsToLog());
    }

    @Test
    @DisplayName("IpActivityMetric - 基本属性")
    void testIpActivityMetric_Properties() {
        SecurityMonitoringService.IpActivityMetric metric =
                new SecurityMonitoringService.IpActivityMetric("192.168.1.100", 50);

        assertEquals("192.168.1.100", metric.getIp());
        assertEquals(50L, metric.getCount());
    }
}
