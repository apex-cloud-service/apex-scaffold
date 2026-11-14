package com.apex.scaffold.auth.domain.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveSetOperations;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 暴力破解防护服务测试
 *
 * @author Apex Security Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("暴力破解防护服务测试")
class BruteForceProtectionServiceTest {

    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_IP = "192.168.1.100";
    private static final String TEST_OPERATOR = "admin";

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Mock
    private UnifiedAuditService auditService;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    @Mock
    private ReactiveSetOperations<String, String> setOperations;

    @InjectMocks
    private BruteForceProtectionService service;

    @Test
    @DisplayName("检查登录允许 - 用户和IP都未锁定")
    void testCheckLoginAllowed_NotLocked() {
        when(redisTemplate.hasKey("login:locked:user:" + TEST_USERNAME)).thenReturn(Mono.just(false));
        when(redisTemplate.hasKey("login:locked:ip:" + TEST_IP)).thenReturn(Mono.just(false));

        StepVerifier.create(service.checkLoginAllowed(TEST_USERNAME, TEST_IP)).verifyComplete();

        verify(redisTemplate).hasKey("login:locked:user:" + TEST_USERNAME);
        verify(redisTemplate).hasKey("login:locked:ip:" + TEST_IP);
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("检查登录允许 - 用户已锁定")
    void testCheckLoginAllowed_UserLocked() {
        when(redisTemplate.hasKey("login:locked:user:" + TEST_USERNAME)).thenReturn(Mono.just(true));
        when(redisTemplate.hasKey("login:locked:ip:" + TEST_IP)).thenReturn(Mono.just(false));
        when(auditService.logSecurityEvent(anyString(), any(), any(), any(), anyString(), anyString(), anyMap()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.checkLoginAllowed(TEST_USERNAME, TEST_IP))
                .expectErrorMatches(
                        e -> e instanceof RuntimeException && e.getMessage().contains("账户已被锁定"))
                .verify();

        verify(auditService)
                .logSecurityEvent(
                        eq("LOGIN_BLOCKED_USER_LOCKED"),
                        isNull(),
                        isNull(),
                        eq(TEST_IP),
                        eq("MEDIUM"),
                        anyString(),
                        argThat(map -> map.containsKey("username")
                                && map.get("username").equals(TEST_USERNAME)));
    }

    @Test
    @DisplayName("检查登录允许 - IP已锁定")
    void testCheckLoginAllowed_IpLocked() {
        when(redisTemplate.hasKey("login:locked:user:" + TEST_USERNAME)).thenReturn(Mono.just(false));
        when(redisTemplate.hasKey("login:locked:ip:" + TEST_IP)).thenReturn(Mono.just(true));
        when(auditService.logSecurityEvent(anyString(), any(), any(), any(), anyString(), anyString(), anyMap()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.checkLoginAllowed(TEST_USERNAME, TEST_IP))
                .expectErrorMatches(
                        e -> e instanceof RuntimeException && e.getMessage().contains("IP地址已被锁定"))
                .verify();

        verify(auditService)
                .logSecurityEvent(
                        eq("LOGIN_BLOCKED_IP_LOCKED"),
                        isNull(),
                        isNull(),
                        eq(TEST_IP),
                        eq("HIGH"),
                        anyString(),
                        argThat(map -> map.containsKey("ip") && map.get("ip").equals(TEST_IP)));
    }

    @Test
    @DisplayName("记录登录失败 - 首次失败")
    void testRecordLoginFailure_FirstAttempt() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(valueOperations.increment("login:fail:user:" + TEST_USERNAME)).thenReturn(Mono.just(1L));
        when(valueOperations.increment("login:fail:ip:" + TEST_IP)).thenReturn(Mono.just(1L));
        when(valueOperations.increment("login:fail:global:counter")).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        when(setOperations.add(anyString(), anyString())).thenReturn(Mono.just(1L));
        when(setOperations.size(anyString())).thenReturn(Mono.just(1L));
        when(auditService.logAuthenticationEvent(
                        anyString(), any(), any(), anyString(), anyBoolean(), anyString(), anyMap()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.recordLoginFailure(TEST_USERNAME, TEST_IP)).verifyComplete();

        verify(valueOperations).increment("login:fail:user:" + TEST_USERNAME);
        verify(valueOperations).increment("login:fail:ip:" + TEST_IP);
        verify(valueOperations).increment("login:fail:global:counter");
        verify(redisTemplate, atLeast(3)).expire(anyString(), any(Duration.class));
        verify(auditService)
                .logAuthenticationEvent(
                        eq("LOGIN_FAILED"),
                        isNull(),
                        isNull(),
                        eq(TEST_IP),
                        eq(false),
                        eq("密码错误"),
                        argThat(map -> map.containsKey("username")));
    }

    @Test
    @DisplayName("记录登录失败 - 达到用户锁定阈值")
    void testRecordLoginFailure_UserLockThreshold() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(valueOperations.increment("login:fail:user:" + TEST_USERNAME)).thenReturn(Mono.just(5L));
        when(valueOperations.increment("login:fail:ip:" + TEST_IP)).thenReturn(Mono.just(2L));
        when(valueOperations.increment("login:fail:global:counter")).thenReturn(Mono.just(10L));
        when(valueOperations.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        when(setOperations.add(anyString(), anyString())).thenReturn(Mono.just(0L));
        when(setOperations.size(anyString())).thenReturn(Mono.just(2L));
        when(auditService.logSecurityEvent(anyString(), any(), any(), any(), anyString(), anyString(), anyMap()))
                .thenReturn(Mono.empty());
        when(auditService.logAuthenticationEvent(
                        anyString(), any(), any(), anyString(), anyBoolean(), anyString(), anyMap()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.recordLoginFailure(TEST_USERNAME, TEST_IP)).verifyComplete();

        verify(valueOperations).set(eq("login:locked:user:" + TEST_USERNAME), anyString(), any(Duration.class));
        verify(auditService)
                .logSecurityEvent(
                        eq("USER_LOCKED"),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq("HIGH"),
                        anyString(),
                        argThat(map -> map.containsKey("username")
                                && map.get("username").equals(TEST_USERNAME)));
    }

    @Test
    @DisplayName("记录登录失败 - 达到IP锁定阈值")
    void testRecordLoginFailure_IpLockThreshold() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(valueOperations.increment("login:fail:user:" + TEST_USERNAME)).thenReturn(Mono.just(2L));
        when(valueOperations.increment("login:fail:ip:" + TEST_IP)).thenReturn(Mono.just(10L));
        when(valueOperations.increment("login:fail:global:counter")).thenReturn(Mono.just(15L));
        when(valueOperations.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        when(setOperations.add(anyString(), anyString())).thenReturn(Mono.just(0L));
        when(setOperations.size(anyString())).thenReturn(Mono.just(2L));
        when(auditService.logSecurityEvent(anyString(), any(), any(), any(), anyString(), anyString(), anyMap()))
                .thenReturn(Mono.empty());
        when(auditService.logAuthenticationEvent(
                        anyString(), any(), any(), anyString(), anyBoolean(), anyString(), anyMap()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.recordLoginFailure(TEST_USERNAME, TEST_IP)).verifyComplete();

        verify(valueOperations).set(eq("login:locked:ip:" + TEST_IP), anyString(), any(Duration.class));
        verify(auditService)
                .logSecurityEvent(
                        eq("IP_LOCKED"),
                        isNull(),
                        isNull(),
                        eq(TEST_IP),
                        eq("HIGH"),
                        anyString(),
                        argThat(map -> map.containsKey("ip") && map.get("ip").equals(TEST_IP)));
    }

    @Test
    @DisplayName("记录登录失败 - 全局失败率超过阈值")
    void testRecordLoginFailure_GlobalThresholdExceeded() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(valueOperations.increment("login:fail:user:" + TEST_USERNAME)).thenReturn(Mono.just(2L));
        when(valueOperations.increment("login:fail:ip:" + TEST_IP)).thenReturn(Mono.just(3L));
        when(valueOperations.increment("login:fail:global:counter")).thenReturn(Mono.just(100L));
        when(setOperations.add(anyString(), anyString())).thenReturn(Mono.just(0L));
        when(setOperations.size(anyString())).thenReturn(Mono.just(2L));
        when(auditService.logSecurityEvent(anyString(), any(), any(), any(), anyString(), anyString(), anyMap()))
                .thenReturn(Mono.empty());
        when(auditService.logAuthenticationEvent(
                        anyString(), any(), any(), anyString(), anyBoolean(), anyString(), anyMap()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.recordLoginFailure(TEST_USERNAME, TEST_IP)).verifyComplete();

        verify(auditService)
                .logSecurityEvent(
                        eq("GLOBAL_BRUTE_FORCE_DETECTED"),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq("CRITICAL"),
                        anyString(),
                        argThat(map -> map.containsKey("failureCount")
                                && map.get("failureCount").equals(100L)));
    }

    @Test
    @DisplayName("记录登录失败 - 检测到分布式攻击")
    void testRecordLoginFailure_DistributedAttackDetected() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(valueOperations.increment("login:fail:user:" + TEST_USERNAME)).thenReturn(Mono.just(2L));
        when(valueOperations.increment("login:fail:ip:" + TEST_IP)).thenReturn(Mono.just(3L));
        when(valueOperations.increment("login:fail:global:counter")).thenReturn(Mono.just(20L));
        when(setOperations.add(anyString(), anyString())).thenReturn(Mono.just(0L));
        when(setOperations.size(anyString())).thenReturn(Mono.just(5L));
        when(setOperations.members(anyString()))
                .thenReturn(Flux.just("192.168.1.1", "192.168.1.2", "192.168.1.3", "192.168.1.4", TEST_IP));
        when(auditService.logSecurityEvent(anyString(), any(), any(), any(), anyString(), anyString(), anyMap()))
                .thenReturn(Mono.empty());
        when(auditService.logAuthenticationEvent(
                        anyString(), any(), any(), anyString(), anyBoolean(), anyString(), anyMap()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.recordLoginFailure(TEST_USERNAME, TEST_IP)).verifyComplete();

        verify(auditService)
                .logSecurityEvent(
                        eq("DISTRIBUTED_BRUTE_FORCE_DETECTED"),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq("CRITICAL"),
                        anyString(),
                        argThat(map -> map.containsKey("username")
                                && map.containsKey("ipCount")
                                && map.get("ipCount").equals(5L)));
    }

    @Test
    @DisplayName("记录登录成功 - 清除失败计数")
    void testRecordLoginSuccess() {
        when(redisTemplate.delete("login:fail:user:" + TEST_USERNAME)).thenReturn(Mono.just(1L));
        when(redisTemplate.delete("login:fail:ip:" + TEST_IP)).thenReturn(Mono.just(1L));
        when(redisTemplate.delete("login:distributed:attack:" + TEST_USERNAME)).thenReturn(Mono.just(1L));

        StepVerifier.create(service.recordLoginSuccess(TEST_USERNAME, TEST_IP)).verifyComplete();

        verify(redisTemplate).delete("login:fail:user:" + TEST_USERNAME);
        verify(redisTemplate).delete("login:fail:ip:" + TEST_IP);
        verify(redisTemplate).delete("login:distributed:attack:" + TEST_USERNAME);
    }

    @Test
    @DisplayName("手动解锁用户")
    void testUnlockUser() {
        when(redisTemplate.delete("login:locked:user:" + TEST_USERNAME)).thenReturn(Mono.just(1L));
        when(redisTemplate.delete("login:fail:user:" + TEST_USERNAME)).thenReturn(Mono.just(1L));
        when(auditService.logSystemEvent(anyString(), anyString(), anyBoolean(), anyMap()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.unlockUser(TEST_USERNAME, TEST_OPERATOR)).verifyComplete();

        verify(redisTemplate).delete("login:locked:user:" + TEST_USERNAME);
        verify(redisTemplate).delete("login:fail:user:" + TEST_USERNAME);
        verify(auditService)
                .logSystemEvent(
                        eq("USER_UNLOCKED"),
                        eq(TEST_OPERATOR),
                        eq(true),
                        argThat(map -> map.containsKey("username")
                                && map.get("username").equals(TEST_USERNAME)
                                && map.get("operator").equals(TEST_OPERATOR)));
    }

    @Test
    @DisplayName("手动解锁IP")
    void testUnlockIp() {
        when(redisTemplate.delete("login:locked:ip:" + TEST_IP)).thenReturn(Mono.just(1L));
        when(redisTemplate.delete("login:fail:ip:" + TEST_IP)).thenReturn(Mono.just(1L));
        when(auditService.logSystemEvent(anyString(), anyString(), anyBoolean(), anyMap()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.unlockIp(TEST_IP, TEST_OPERATOR)).verifyComplete();

        verify(redisTemplate).delete("login:locked:ip:" + TEST_IP);
        verify(redisTemplate).delete("login:fail:ip:" + TEST_IP);
        verify(auditService)
                .logSystemEvent(
                        eq("IP_UNLOCKED"),
                        eq(TEST_OPERATOR),
                        eq(true),
                        argThat(map -> map.containsKey("ip")
                                && map.get("ip").equals(TEST_IP)
                                && map.get("operator").equals(TEST_OPERATOR)));
    }

    @Test
    @DisplayName("获取剩余尝试次数 - 没有失败记录")
    void testGetRemainingAttempts_NoFailures() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("login:fail:user:" + TEST_USERNAME)).thenReturn(Mono.empty());

        StepVerifier.create(service.getRemainingAttempts(TEST_USERNAME))
                .expectNext(5)
                .verifyComplete();
    }

    @Test
    @DisplayName("获取剩余尝试次数 - 已有失败记录")
    void testGetRemainingAttempts_WithFailures() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("login:fail:user:" + TEST_USERNAME)).thenReturn(Mono.just("3"));

        StepVerifier.create(service.getRemainingAttempts(TEST_USERNAME))
                .expectNext(2)
                .verifyComplete();
    }

    @Test
    @DisplayName("获取剩余尝试次数 - 失败次数已达上限")
    void testGetRemainingAttempts_MaxFailures() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("login:fail:user:" + TEST_USERNAME)).thenReturn(Mono.just("5"));

        StepVerifier.create(service.getRemainingAttempts(TEST_USERNAME))
                .expectNext(0)
                .verifyComplete();
    }

    @Test
    @DisplayName("获取剩余尝试次数 - 失败次数超过上限")
    void testGetRemainingAttempts_ExceedMaxFailures() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("login:fail:user:" + TEST_USERNAME)).thenReturn(Mono.just("10"));

        StepVerifier.create(service.getRemainingAttempts(TEST_USERNAME))
                .expectNext(0)
                .verifyComplete();
    }
}
