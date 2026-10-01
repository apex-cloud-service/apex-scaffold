package com.apex.scaffold.authcenter.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("暴力破解防护服务测试")
class BruteForcePreventionTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOps;

    private BruteForcePrevention bruteForcePrevention;

    private static final String TEST_USERNAME = "test_user_brute";
    private static final String TEST_IP = "192.168.1.100";

    @BeforeEach
    void setUp() {
        bruteForcePrevention = new BruteForcePrevention(redisTemplate);
    }

    @Test
    @DisplayName("首次失败应返回正确的剩余尝试次数")
    void testFirstLoginFailure() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        StepVerifier.create(bruteForcePrevention.recordLoginFailure(TEST_USERNAME, TEST_IP))
                .assertNext(remaining -> assertThat(remaining).isEqualTo(4))
                .verifyComplete();
    }

    @Test
    @DisplayName("连续失败应递减剩余尝试次数")
    void testMultipleLoginFailures() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        // 第1次失败
        when(valueOps.increment(anyString())).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        StepVerifier.create(bruteForcePrevention.recordLoginFailure(TEST_USERNAME, TEST_IP))
                .assertNext(remaining -> assertThat(remaining).isEqualTo(4))
                .verifyComplete();

        // 第2次失败
        when(valueOps.increment(anyString())).thenReturn(Mono.just(2L));

        StepVerifier.create(bruteForcePrevention.recordLoginFailure(TEST_USERNAME, TEST_IP))
                .assertNext(remaining -> assertThat(remaining).isEqualTo(3))
                .verifyComplete();

        // 第3次失败
        when(valueOps.increment(anyString())).thenReturn(Mono.just(3L));

        StepVerifier.create(bruteForcePrevention.recordLoginFailure(TEST_USERNAME, TEST_IP))
                .assertNext(remaining -> assertThat(remaining).isEqualTo(2))
                .verifyComplete();
    }

    @Test
    @DisplayName("达到最大失败次数后应触发锁定")
    void testAccountLockAfterMaxAttempts() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        // 模拟达到最大失败次数
        when(valueOps.increment(anyString())).thenReturn(Mono.just(5L));
        when(valueOps.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(true));

        // 触发锁定
        bruteForcePrevention.recordLoginFailure(TEST_USERNAME, TEST_IP).block();

        // 验证账户被锁定
        StepVerifier.create(bruteForcePrevention.isBlocked(TEST_USERNAME, TEST_IP))
                .assertNext(blocked -> assertThat(blocked).isTrue())
                .verifyComplete();
    }

    @Test
    @DisplayName("锁定后应返回0剩余尝试次数")
    void testRemainingAttemptsAfterLockout() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        // 模拟锁定状态
        when(valueOps.increment(anyString())).thenReturn(Mono.just(5L));
        when(valueOps.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        StepVerifier.create(bruteForcePrevention.recordLoginFailure(TEST_USERNAME, TEST_IP))
                .assertNext(remaining -> assertThat(remaining).isEqualTo(0))
                .verifyComplete();
    }

    @Test
    @DisplayName("登录成功应清除失败记录")
    void testClearAttemptsOnSuccess() {
        when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));

        StepVerifier.create(bruteForcePrevention.recordLoginSuccess(TEST_USERNAME, TEST_IP))
                .verifyComplete();
    }

    @Test
    @DisplayName("手动解锁用户名应成功")
    void testUnlockUsername() {
        when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));

        StepVerifier.create(bruteForcePrevention.unlockUsername(TEST_USERNAME)).verifyComplete();
    }

    @Test
    @DisplayName("手动解锁IP应成功")
    void testUnlockIp() {
        when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));

        StepVerifier.create(bruteForcePrevention.unlockIp(TEST_IP)).verifyComplete();
    }

    @Test
    @DisplayName("获取剩余锁定时间")
    void testGetRemainingLockoutTime() {
        when(redisTemplate.getExpire(anyString())).thenReturn(Mono.just(Duration.ofMinutes(10)));

        StepVerifier.create(bruteForcePrevention.getRemainingLockoutTime(TEST_USERNAME, TEST_IP))
                .assertNext(remainingSeconds -> {
                    assertThat(remainingSeconds).isGreaterThan(0);
                    assertThat(remainingSeconds)
                            .isLessThanOrEqualTo(Duration.ofMinutes(15).getSeconds());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("未锁定时剩余锁定时间应为0")
    void testGetRemainingLockoutTimeWhenNotLocked() {
        when(redisTemplate.getExpire(anyString())).thenReturn(Mono.just(Duration.ofSeconds(0)));

        StepVerifier.create(bruteForcePrevention.getRemainingLockoutTime(TEST_USERNAME, TEST_IP))
                .assertNext(remainingSeconds -> assertThat(remainingSeconds).isEqualTo(0L))
                .verifyComplete();
    }

    @Test
    @DisplayName("获取失败尝试次数")
    void testGetFailedAttempts() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(Mono.just("3"));

        StepVerifier.create(bruteForcePrevention.getFailedAttempts(TEST_USERNAME, TEST_IP))
                .assertNext(attempts -> assertThat(attempts).isEqualTo(3))
                .verifyComplete();
    }

    @Test
    @DisplayName("初始状态不应被锁定")
    void testInitialStateNotBlocked() {
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(false));

        StepVerifier.create(bruteForcePrevention.isBlocked("new_user", "10.10.10.10"))
                .assertNext(blocked -> assertThat(blocked).isFalse())
                .verifyComplete();
    }
}
