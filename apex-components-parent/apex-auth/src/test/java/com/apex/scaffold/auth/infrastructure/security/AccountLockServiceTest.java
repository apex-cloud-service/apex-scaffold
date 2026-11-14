package com.apex.scaffold.auth.infrastructure.security;

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
@DisplayName("账户锁定服务测试")
class AccountLockServiceTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOps;

    private AccountLockService accountLockService;

    private static final String TEST_USER_ID = "test_user_123";
    private static final String TEST_OPERATOR = "admin";

    @BeforeEach
    void setUp() {
        accountLockService = new AccountLockService(redisTemplate);
    }

    @Test
    @DisplayName("初始状态账户不应被锁定")
    void testInitialStateNotLocked() {
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(false));

        StepVerifier.create(accountLockService.isLocked(TEST_USER_ID))
                .assertNext(locked -> assertThat(locked).isFalse())
                .verifyComplete();
    }

    @Test
    @DisplayName("临时锁定账户应成功")
    void testLockTemporarily() {
        Duration lockDuration = Duration.ofMinutes(10);
        String reason = "测试锁定";

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        StepVerifier.create(accountLockService.lockTemporarily(TEST_USER_ID, reason, lockDuration))
                .assertNext(success -> assertThat(success).isTrue())
                .verifyComplete();
    }

    @Test
    @DisplayName("永久锁定账户应成功")
    void testLockPermanently() {
        String reason = "违反安全策略";

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.set(anyString(), anyString())).thenReturn(Mono.just(true));

        StepVerifier.create(accountLockService.lockPermanently(TEST_USER_ID, reason))
                .assertNext(success -> assertThat(success).isTrue())
                .verifyComplete();
    }

    @Test
    @DisplayName("解锁账户应成功")
    void testUnlock() {
        when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));

        StepVerifier.create(accountLockService.unlock(TEST_USER_ID, TEST_OPERATOR))
                .assertNext(success -> assertThat(success).isTrue())
                .verifyComplete();
    }

    @Test
    @DisplayName("解锁未锁定的账户应返回false")
    void testUnlockNotLockedAccount() {
        when(redisTemplate.delete(anyString())).thenReturn(Mono.just(0L));

        StepVerifier.create(accountLockService.unlock(TEST_USER_ID, TEST_OPERATOR))
                .assertNext(success -> assertThat(success).isFalse())
                .verifyComplete();
    }

    @Test
    @DisplayName("获取锁定原因")
    void testGetLockReason() {
        String reason = "暴力破解攻击";
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(Mono.just(reason));

        StepVerifier.create(accountLockService.getLockReason(TEST_USER_ID))
                .assertNext(retrievedReason -> assertThat(retrievedReason).isEqualTo(reason))
                .verifyComplete();
    }

    @Test
    @DisplayName("未锁定账户的锁定原因应为空")
    void testGetLockReasonForUnlockedAccount() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(Mono.empty());

        StepVerifier.create(accountLockService.getLockReason(TEST_USER_ID)).verifyComplete();
    }

    @Test
    @DisplayName("获取锁定时间戳")
    void testGetLockTimestamp() {
        long timestamp = System.currentTimeMillis();
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(Mono.just(String.valueOf(timestamp)));

        StepVerifier.create(accountLockService.getLockTimestamp(TEST_USER_ID))
                .assertNext(ts -> assertThat(ts).isEqualTo(timestamp))
                .verifyComplete();
    }

    @Test
    @DisplayName("临时锁定应返回剩余锁定时间")
    void testGetRemainingLockTimeForTemporaryLock() {
        when(redisTemplate.getExpire(anyString())).thenReturn(Mono.just(Duration.ofMinutes(5)));

        StepVerifier.create(accountLockService.getRemainingLockTime(TEST_USER_ID))
                .assertNext(remainingSeconds -> {
                    assertThat(remainingSeconds).isGreaterThan(0);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("永久锁定应返回-1剩余时间")
    void testGetRemainingLockTimeForPermanentLock() {
        when(redisTemplate.getExpire(anyString())).thenReturn(Mono.just(Duration.ofSeconds(-1)));

        StepVerifier.create(accountLockService.getRemainingLockTime(TEST_USER_ID))
                .assertNext(remainingSeconds -> assertThat(remainingSeconds).isEqualTo(-1L))
                .verifyComplete();
    }

    @Test
    @DisplayName("未锁定账户剩余时间应为0")
    void testGetRemainingLockTimeForUnlockedAccount() {
        when(redisTemplate.getExpire(anyString())).thenReturn(Mono.empty());

        StepVerifier.create(accountLockService.getRemainingLockTime(TEST_USER_ID))
                .assertNext(remainingSeconds -> assertThat(remainingSeconds).isEqualTo(0L))
                .verifyComplete();
    }

    @Test
    @DisplayName("获取锁定信息 - 未锁定账户")
    void testGetLockInfoForUnlockedAccount() {
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(false));

        StepVerifier.create(accountLockService.getLockInfo(TEST_USER_ID))
                .assertNext(lockInfo -> assertThat(lockInfo.isLocked()).isFalse())
                .verifyComplete();
    }

    @Test
    @DisplayName("延长锁定时间应成功")
    void testExtendLockTime() {
        Duration additionalDuration = Duration.ofMinutes(3);

        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(true));
        when(redisTemplate.getExpire(anyString())).thenReturn(Mono.just(Duration.ofMinutes(5)));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        StepVerifier.create(accountLockService.extendLockTime(TEST_USER_ID, additionalDuration))
                .assertNext(success -> assertThat(success).isTrue())
                .verifyComplete();
    }

    @Test
    @DisplayName("延长未锁定账户的锁定时间应返回false")
    void testExtendLockTimeForUnlockedAccount() {
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(false));

        StepVerifier.create(accountLockService.extendLockTime(TEST_USER_ID, Duration.ofMinutes(5)))
                .assertNext(success -> assertThat(success).isFalse())
                .verifyComplete();
    }

    @Test
    @DisplayName("LockReason枚举应包含所有预定义原因")
    void testLockReasonEnum() {
        assertThat(AccountLockService.LockReason.BRUTE_FORCE.getDescription()).isEqualTo("暴力破解攻击");
        assertThat(AccountLockService.LockReason.SUSPICIOUS_ACTIVITY.getDescription())
                .isEqualTo("可疑活动");
        assertThat(AccountLockService.LockReason.MANUAL_LOCK.getDescription()).isEqualTo("管理员手动锁定");
        assertThat(AccountLockService.LockReason.POLICY_VIOLATION.getDescription())
                .isEqualTo("违反安全策略");
        assertThat(AccountLockService.LockReason.PASSWORD_EXPIRED.getDescription())
                .isEqualTo("密码过期");
        assertThat(AccountLockService.LockReason.MULTIPLE_DEVICES.getDescription())
                .isEqualTo("多设备异常登录");
    }
}
