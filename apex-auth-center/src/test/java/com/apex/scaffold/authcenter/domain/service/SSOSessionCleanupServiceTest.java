package com.apex.scaffold.authcenter.domain.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SSO会话清理服务测试")
class SSOSessionCleanupServiceTest {

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;

    private SSOSessionCleanupService service;

    @BeforeEach
    void setUp() {
        service = new SSOSessionCleanupService(redisTemplate);
    }

    @Test
    @DisplayName("清理过期会话 - 成功清理多种类型")
    void testCleanupExpiredSessions_Success() {
        when(redisTemplate.keys(anyString()))
                .thenReturn(Flux.just("key1", "key2"))
                .thenReturn(Flux.just("key3"))
                .thenReturn(Flux.empty())
                .thenReturn(Flux.just("key4", "key5"))
                .thenReturn(Flux.just("key6"))
                .thenReturn(Flux.empty())
                .thenReturn(Flux.just("key7"));

        when(redisTemplate.getExpire(anyString())).thenReturn(Mono.just(Duration.ofSeconds(-2)));

        when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));

        StepVerifier.create(service.cleanupExpiredSessions()).expectNext(7L).verifyComplete();
    }

    @Test
    @DisplayName("清理过期会话 - 无过期数据")
    void testCleanupExpiredSessions_NoExpiredData() {
        when(redisTemplate.keys(anyString())).thenReturn(Flux.empty());

        StepVerifier.create(service.cleanupExpiredSessions()).expectNext(0L).verifyComplete();
    }

    @Test
    @DisplayName("清理过期会话 - 部分键未过期")
    void testCleanupExpiredSessions_MixedKeys() {
        when(redisTemplate.keys("sso:saml:assertion:*")).thenReturn(Flux.just("key1", "key2", "key3"));

        when(redisTemplate.keys(argThat(pattern -> !pattern.equals("sso:saml:assertion:*"))))
                .thenReturn(Flux.empty());

        when(redisTemplate.getExpire("key1")).thenReturn(Mono.just(Duration.ofSeconds(-2)));
        when(redisTemplate.getExpire("key2")).thenReturn(Mono.just(Duration.ofSeconds(300)));
        when(redisTemplate.getExpire("key3")).thenReturn(Mono.just(Duration.ofSeconds(-1)));

        when(redisTemplate.delete("key1")).thenReturn(Mono.just(1L));

        StepVerifier.create(service.cleanupExpiredSessions()).expectNext(1L).verifyComplete();

        verify(redisTemplate, times(1)).delete("key1");
        verify(redisTemplate, never()).delete("key2");
        verify(redisTemplate, never()).delete("key3");
    }

    @Test
    @DisplayName("清理过期会话 - Redis错误处理")
    void testCleanupExpiredSessions_RedisError() {
        when(redisTemplate.keys(anyString())).thenReturn(Flux.just("key1"));

        when(redisTemplate.getExpire("key1")).thenReturn(Mono.error(new RuntimeException("Redis connection failed")));

        StepVerifier.create(service.cleanupExpiredSessions()).expectNext(0L).verifyComplete();
    }

    @Test
    @DisplayName("清理用户会话 - 成功")
    void testCleanupUserSessions_Success() {
        String userId = "user-123";

        when(redisTemplate.keys("auth:token:*:" + userId + ":*")).thenReturn(Flux.just("token1", "token2"));
        when(redisTemplate.keys("sso:session:" + userId + ":*")).thenReturn(Flux.just("session1"));

        when(redisTemplate.getExpire(anyString())).thenReturn(Mono.just(Duration.ofSeconds(-2)));
        when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));

        StepVerifier.create(service.cleanupUserSessions(userId)).expectNext(3L).verifyComplete();
    }

    @Test
    @DisplayName("清理用户会话 - 无会话数据")
    void testCleanupUserSessions_NoSessions() {
        String userId = "user-456";

        when(redisTemplate.keys(anyString())).thenReturn(Flux.empty());

        StepVerifier.create(service.cleanupUserSessions(userId)).expectNext(0L).verifyComplete();
    }

    @Test
    @DisplayName("清理设备会话 - 成功")
    void testCleanupDeviceSession_Success() {
        String userId = "user-123";
        String deviceId = "device-abc";

        when(redisTemplate.keys("auth:token:*:" + userId + ":" + deviceId)).thenReturn(Flux.just("token1", "token2"));

        when(redisTemplate.getExpire(anyString())).thenReturn(Mono.just(Duration.ofSeconds(-2)));
        when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));

        StepVerifier.create(service.cleanupDeviceSession(userId, deviceId))
                .expectNext(2L)
                .verifyComplete();
    }

    @Test
    @DisplayName("清理设备会话 - 无设备会话")
    void testCleanupDeviceSession_NoSession() {
        String userId = "user-123";
        String deviceId = "device-xyz";

        when(redisTemplate.keys(anyString())).thenReturn(Flux.empty());

        StepVerifier.create(service.cleanupDeviceSession(userId, deviceId))
                .expectNext(0L)
                .verifyComplete();
    }

    @Test
    @DisplayName("批量清理用户会话 - 成功")
    void testCleanupUserSessionsBatch_Success() {
        Set<String> userIds = Set.of("user-1", "user-2", "user-3");

        when(redisTemplate.keys(contains("user-1")))
                .thenReturn(Flux.just("key1"))
                .thenReturn(Flux.just("session1", "session2"));
        when(redisTemplate.keys(contains("user-2")))
                .thenReturn(Flux.just("key2", "key3"))
                .thenReturn(Flux.empty());
        when(redisTemplate.keys(contains("user-3")))
                .thenReturn(Flux.just("key4"))
                .thenReturn(Flux.just("session3"));

        when(redisTemplate.getExpire(anyString())).thenReturn(Mono.just(Duration.ofSeconds(-2)));
        when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));

        StepVerifier.create(service.cleanupUserSessionsBatch(userIds))
                .expectNext(7L)
                .verifyComplete();
    }

    @Test
    @DisplayName("批量清理用户会话 - 空用户集合")
    void testCleanupUserSessionsBatch_EmptySet() {
        Set<String> userIds = Set.of();

        StepVerifier.create(service.cleanupUserSessionsBatch(userIds))
                .expectNext(0L)
                .verifyComplete();

        verify(redisTemplate, never()).keys(anyString());
    }

    @Test
    @DisplayName("批量清理用户会话 - 部分用户无会话")
    void testCleanupUserSessionsBatch_PartialSessions() {
        Set<String> userIds = Set.of("user-1", "user-2");

        when(redisTemplate.keys(contains("user-1")))
                .thenReturn(Flux.just("key1"))
                .thenReturn(Flux.just("session1"));
        when(redisTemplate.keys(contains("user-2"))).thenReturn(Flux.empty()).thenReturn(Flux.empty());

        when(redisTemplate.getExpire(anyString())).thenReturn(Mono.just(Duration.ofSeconds(-2)));
        when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));

        StepVerifier.create(service.cleanupUserSessionsBatch(userIds))
                .expectNext(2L)
                .verifyComplete();
    }

    @Test
    @DisplayName("清理租户会话 - 成功")
    void testCleanupTenantSessions_Success() {
        String tenantId = "tenant-abc";

        when(redisTemplate.keys("auth:token:*:*:" + tenantId + ":*"))
                .thenReturn(Flux.just("token1", "token2", "token3"));

        when(redisTemplate.getExpire(anyString())).thenReturn(Mono.just(Duration.ofSeconds(-2)));
        when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));

        StepVerifier.create(service.cleanupTenantSessions(tenantId))
                .expectNext(3L)
                .verifyComplete();
    }

    @Test
    @DisplayName("清理租户会话 - 无租户会话")
    void testCleanupTenantSessions_NoSessions() {
        String tenantId = "tenant-xyz";

        when(redisTemplate.keys(anyString())).thenReturn(Flux.empty());

        StepVerifier.create(service.cleanupTenantSessions(tenantId))
                .expectNext(0L)
                .verifyComplete();
    }

    @Test
    @DisplayName("获取清理统计信息 - 有数据")
    void testGetCleanupStatistics_WithData() {
        when(redisTemplate.keys("sso:saml:assertion:*")).thenReturn(Flux.just("s1", "s2"));
        when(redisTemplate.keys("sso:oidc:state:*")).thenReturn(Flux.just("o1"));
        when(redisTemplate.keys("sso:oidc:nonce:*")).thenReturn(Flux.just("n1", "n2", "n3"));
        when(redisTemplate.keys("oauth2:auth_code:*")).thenReturn(Flux.just("a1"));
        when(redisTemplate.keys("auth:token:blacklist:*")).thenReturn(Flux.just("b1", "b2"));
        when(redisTemplate.keys("auth:login:failure:*")).thenReturn(Flux.just("f1"));
        when(redisTemplate.keys("permission:temp:*")).thenReturn(Flux.just("p1", "p2"));

        StepVerifier.create(service.getCleanupStatistics())
                .assertNext(stats -> {
                    assertEquals(2L, stats.getSamlAssertions());
                    assertEquals(1L, stats.getOidcStates());
                    assertEquals(3L, stats.getOidcNonces());
                    assertEquals(1L, stats.getAuthCodes());
                    assertEquals(2L, stats.getTokenBlacklist());
                    assertEquals(1L, stats.getLoginFailures());
                    assertEquals(2L, stats.getTempPermissions());
                    assertEquals(12L, stats.getTotal());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("获取清理统计信息 - 无数据")
    void testGetCleanupStatistics_NoData() {
        when(redisTemplate.keys(anyString())).thenReturn(Flux.empty());

        StepVerifier.create(service.getCleanupStatistics())
                .assertNext(stats -> {
                    assertEquals(0L, stats.getSamlAssertions());
                    assertEquals(0L, stats.getOidcStates());
                    assertEquals(0L, stats.getOidcNonces());
                    assertEquals(0L, stats.getAuthCodes());
                    assertEquals(0L, stats.getTokenBlacklist());
                    assertEquals(0L, stats.getLoginFailures());
                    assertEquals(0L, stats.getTempPermissions());
                    assertEquals(0L, stats.getTotal());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("CleanupStatistics toString测试")
    void testCleanupStatistics_ToString() {
        SSOSessionCleanupService.CleanupStatistics stats =
                new SSOSessionCleanupService.CleanupStatistics(2L, 1L, 3L, 1L, 2L, 1L, 2L);

        String result = stats.toString();

        assertTrue(result.contains("total=12"));
        assertTrue(result.contains("saml=2"));
        assertTrue(result.contains("oidcState=1"));
        assertTrue(result.contains("oidcNonce=3"));
        assertTrue(result.contains("authCode=1"));
        assertTrue(result.contains("blacklist=2"));
        assertTrue(result.contains("failures=1"));
        assertTrue(result.contains("tempPerm=2"));
    }

    @Test
    @DisplayName("定时清理任务 - 成功执行")
    void testScheduledCleanup_Success() {
        when(redisTemplate.keys(anyString())).thenReturn(Flux.just("key1"));

        when(redisTemplate.getExpire(anyString())).thenReturn(Mono.just(Duration.ofSeconds(-2)));
        when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));

        assertDoesNotThrow(() -> service.scheduledCleanup());
    }

    @Test
    @DisplayName("清理SAML断言 - 仅过期键")
    void testCleanupSamlAssertions_OnlyExpired() {
        when(redisTemplate.keys("sso:saml:assertion:*")).thenReturn(Flux.just("assertion1", "assertion2"));
        when(redisTemplate.keys("sso:oidc:state:*")).thenReturn(Flux.empty());
        when(redisTemplate.keys("sso:oidc:nonce:*")).thenReturn(Flux.empty());
        when(redisTemplate.keys("oauth2:auth_code:*")).thenReturn(Flux.empty());
        when(redisTemplate.keys("auth:token:blacklist:*")).thenReturn(Flux.empty());
        when(redisTemplate.keys("auth:login:failure:*")).thenReturn(Flux.empty());
        when(redisTemplate.keys("permission:temp:*")).thenReturn(Flux.empty());

        when(redisTemplate.getExpire("assertion1")).thenReturn(Mono.just(Duration.ofSeconds(-2)));
        when(redisTemplate.getExpire("assertion2")).thenReturn(Mono.just(Duration.ofSeconds(100)));

        when(redisTemplate.delete("assertion1")).thenReturn(Mono.just(1L));

        StepVerifier.create(service.cleanupExpiredSessions()).expectNext(1L).verifyComplete();
    }

    @Test
    @DisplayName("清理Token黑名单 - 大量数据")
    void testCleanupTokenBlacklist_LargeDataset() {
        Flux<String> largeKeySet = Flux.range(1, 1000).map(i -> "auth:token:blacklist:" + i);

        when(redisTemplate.keys("auth:token:blacklist:*")).thenReturn(largeKeySet);

        when(redisTemplate.keys(argThat(pattern -> !pattern.equals("auth:token:blacklist:*"))))
                .thenReturn(Flux.empty());

        when(redisTemplate.getExpire(anyString())).thenReturn(Mono.just(Duration.ofSeconds(-2)));
        when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));

        StepVerifier.create(service.cleanupExpiredSessions()).expectNext(1000L).verifyComplete();
    }

    @Test
    @DisplayName("清理失败 - 部分键检查失败")
    void testCleanup_PartialFailure() {
        when(redisTemplate.keys("sso:saml:assertion:*")).thenReturn(Flux.just("key1", "key2", "key3"));

        when(redisTemplate.keys(argThat(pattern -> !pattern.equals("sso:saml:assertion:*"))))
                .thenReturn(Flux.empty());

        when(redisTemplate.getExpire("key1")).thenReturn(Mono.just(Duration.ofSeconds(-2)));
        when(redisTemplate.getExpire("key2")).thenReturn(Mono.error(new RuntimeException("Connection timeout")));
        when(redisTemplate.getExpire("key3")).thenReturn(Mono.just(Duration.ofSeconds(-2)));

        when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));

        StepVerifier.create(service.cleanupExpiredSessions()).expectNext(2L).verifyComplete();
    }

    @Test
    @DisplayName("清理用户会话 - 并发清理多个模式")
    void testCleanupUserSessions_MultiplePatterns() {
        String userId = "user-999";

        when(redisTemplate.keys("auth:token:*:" + userId + ":*")).thenReturn(Flux.just("t1", "t2", "t3"));
        when(redisTemplate.keys("sso:session:" + userId + ":*")).thenReturn(Flux.just("s1", "s2"));

        when(redisTemplate.getExpire(anyString())).thenReturn(Mono.just(Duration.ofSeconds(-2)));
        when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));

        StepVerifier.create(service.cleanupUserSessions(userId)).expectNext(5L).verifyComplete();

        verify(redisTemplate).keys("auth:token:*:" + userId + ":*");
        verify(redisTemplate).keys("sso:session:" + userId + ":*");
    }

    @Test
    @DisplayName("清理永久键 - 不应删除")
    void testCleanup_PermanentKeys() {
        when(redisTemplate.keys("sso:saml:assertion:*")).thenReturn(Flux.just("permanent-key"));

        when(redisTemplate.keys(argThat(pattern -> !pattern.equals("sso:saml:assertion:*"))))
                .thenReturn(Flux.empty());

        when(redisTemplate.getExpire("permanent-key")).thenReturn(Mono.just(Duration.ofSeconds(-1)));

        StepVerifier.create(service.cleanupExpiredSessions()).expectNext(0L).verifyComplete();

        verify(redisTemplate, never()).delete("permanent-key");
    }
}
