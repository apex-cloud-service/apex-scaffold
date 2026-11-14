package com.apex.scaffold.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveSetOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("Token黑名单服务测试")
class TokenBlacklistServiceTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOps;

    @Mock
    private ReactiveSetOperations<String, String> setOps;

    private TokenBlacklistService tokenBlacklistService;

    private static final String TEST_JTI = "test_jti_12345";
    private static final String TEST_USER_ID = "user_999";
    private static final Duration TEST_TTL = Duration.ofMinutes(30);

    @BeforeEach
    void setUp() {
        tokenBlacklistService = new TokenBlacklistService(redisTemplate);
    }

    @Test
    @DisplayName("添加Token到黑名单应成功")
    void testAddToBlacklist() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(valueOps.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        when(setOps.add(anyString(), anyString())).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        StepVerifier.create(tokenBlacklistService.addToBlacklist(TEST_JTI, TEST_USER_ID, TEST_TTL))
                .assertNext(success -> assertThat(success).isTrue())
                .verifyComplete();
    }

    @Test
    @DisplayName("初始状态Token不应在黑名单中")
    void testInitialStateNotBlacklisted() {
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(false));

        StepVerifier.create(tokenBlacklistService.isBlacklisted("non_existent_jti"))
                .assertNext(blacklisted -> assertThat(blacklisted).isFalse())
                .verifyComplete();
    }

    @Test
    @DisplayName("从黑名单移除Token应成功")
    void testRemoveFromBlacklist() {
        when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));

        StepVerifier.create(tokenBlacklistService.removeFromBlacklist(TEST_JTI))
                .assertNext(success -> assertThat(success).isTrue())
                .verifyComplete();
    }

    @Test
    @DisplayName("移除不存在的Token应返回false")
    void testRemoveNonExistentToken() {
        when(redisTemplate.delete(anyString())).thenReturn(Mono.just(0L));

        StepVerifier.create(tokenBlacklistService.removeFromBlacklist("non_existent_jti"))
                .assertNext(success -> assertThat(success).isFalse())
                .verifyComplete();
    }

    @Test
    @DisplayName("撤销用户所有Token应成功")
    void testRevokeAllUserTokens() {
        String userId = "user_multi_tokens";

        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(setOps.members(anyString())).thenReturn(Flux.just("jti_1", "jti_2", "jti_3"));
        when(valueOps.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        when(setOps.add(anyString(), anyString())).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        StepVerifier.create(tokenBlacklistService.revokeAllUserTokens(userId, "测试撤销", TEST_TTL))
                .assertNext(count -> assertThat(count).isEqualTo(3L))
                .verifyComplete();
    }

    @Test
    @DisplayName("批量添加Token到黑名单应成功")
    void testAddBatchToBlacklist() {
        Set<String> jtis = Set.of("batch_jti_1", "batch_jti_2", "batch_jti_3");

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(valueOps.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        when(setOps.add(anyString(), anyString())).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        StepVerifier.create(tokenBlacklistService.addBatchToBlacklist(jtis, TEST_USER_ID, TEST_TTL))
                .assertNext(count -> assertThat(count).isEqualTo(3L))
                .verifyComplete();
    }

    @Test
    @DisplayName("获取用户黑名单Token列表")
    void testGetUserBlacklistedTokens() {
        String userId = "user_token_list";

        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members(anyString())).thenReturn(Flux.just("list_jti_1", "list_jti_2"));

        StepVerifier.create(tokenBlacklistService.getUserBlacklistedTokens(userId))
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    @DisplayName("清理过期Token应成功")
    void testCleanupUserExpiredTokens() {
        String userId = "user_cleanup";

        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members(anyString())).thenReturn(Flux.just("cleanup_jti_1", "cleanup_jti_2"));
        when(redisTemplate.hasKey("token:blacklist:cleanup_jti_1")).thenReturn(Mono.just(false));
        when(redisTemplate.hasKey("token:blacklist:cleanup_jti_2")).thenReturn(Mono.just(true));
        when(setOps.remove(anyString(), any())).thenReturn(Mono.just(1L));

        StepVerifier.create(tokenBlacklistService.cleanupUserExpiredTokens(userId))
                .assertNext(count -> assertThat(count).isGreaterThanOrEqualTo(0L))
                .verifyComplete();
    }

    @Test
    @DisplayName("获取黑名单信息 - 已黑名单Token")
    void testGetBlacklistInfoForBlacklistedToken() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(true));
        when(valueOps.get(anyString())).thenReturn(Mono.just(TEST_USER_ID));
        when(redisTemplate.getExpire(anyString())).thenReturn(Mono.just(Duration.ofMinutes(10)));

        StepVerifier.create(tokenBlacklistService.getBlacklistInfo(TEST_JTI))
                .assertNext(info -> {
                    assertThat(info.isBlacklisted()).isTrue();
                    assertThat(info.getUserId()).isEqualTo(TEST_USER_ID);
                    assertThat(info.getRemainingTtl()).isGreaterThan(0);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("获取黑名单信息 - 未黑名单Token")
    void testGetBlacklistInfoForNonBlacklistedToken() {
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(false));

        StepVerifier.create(tokenBlacklistService.getBlacklistInfo("non_existent"))
                .assertNext(info -> assertThat(info.isBlacklisted()).isFalse())
                .verifyComplete();
    }

    @Test
    @DisplayName("获取用户黑名单统计信息")
    void testGetUserBlacklistStats() {
        String userId = "user_stats";

        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.size(anyString())).thenReturn(Mono.just(2L));
        when(setOps.members(anyString())).thenReturn(Flux.just("stats_jti_1", "stats_jti_2"));
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(true));

        StepVerifier.create(tokenBlacklistService.getUserBlacklistStats(userId))
                .assertNext(stats -> {
                    assertThat(stats.getUserId()).isEqualTo(userId);
                    assertThat(stats.getTotalCount()).isEqualTo(2L);
                    assertThat(stats.getActiveCount()).isEqualTo(2L);
                    assertThat(stats.getExpiredCount()).isEqualTo(0L);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("用户无Token时统计应为空")
    void testGetUserBlacklistStatsWhenNoTokens() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.size(anyString())).thenReturn(Mono.just(0L));

        StepVerifier.create(tokenBlacklistService.getUserBlacklistStats("user_no_tokens"))
                .assertNext(stats -> {
                    assertThat(stats.getTotalCount()).isEqualTo(0L);
                    assertThat(stats.getActiveCount()).isEqualTo(0L);
                    assertThat(stats.getExpiredCount()).isEqualTo(0L);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("RevokeReason枚举应包含所有预定义原因")
    void testRevokeReasonEnum() {
        assertThat(TokenBlacklistService.RevokeReason.USER_LOGOUT.getDescription())
                .isEqualTo("用户主动登出");
        assertThat(TokenBlacklistService.RevokeReason.PASSWORD_CHANGED.getDescription())
                .isEqualTo("密码已修改");
        assertThat(TokenBlacklistService.RevokeReason.ACCOUNT_LOCKED.getDescription())
                .isEqualTo("账户被锁定");
        assertThat(TokenBlacklistService.RevokeReason.SECURITY_BREACH.getDescription())
                .isEqualTo("安全事件");
        assertThat(TokenBlacklistService.RevokeReason.TOKEN_COMPROMISED.getDescription())
                .isEqualTo("Token泄露");
        assertThat(TokenBlacklistService.RevokeReason.ADMIN_REVOKED.getDescription())
                .isEqualTo("管理员撤销");
        assertThat(TokenBlacklistService.RevokeReason.EXPIRED.getDescription()).isEqualTo("Token过期");
    }

    @Test
    @DisplayName("批量添加空集合应返回0")
    void testAddBatchEmptySet() {
        StepVerifier.create(tokenBlacklistService.addBatchToBlacklist(Set.of(), TEST_USER_ID, TEST_TTL))
                .assertNext(count -> assertThat(count).isEqualTo(0L))
                .verifyComplete();
    }

    @Test
    @DisplayName("撤销无Token用户应返回0")
    void testRevokeAllTokensForUserWithNoTokens() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members(anyString())).thenReturn(Flux.empty());

        StepVerifier.create(tokenBlacklistService.revokeAllUserTokens("user_no_tokens", "测试", TEST_TTL))
                .assertNext(count -> assertThat(count).isEqualTo(0L))
                .verifyComplete();
    }
}
