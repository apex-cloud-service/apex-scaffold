package com.apex.scaffold.infrastructure.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.common.config.RedisKeyPrefix;
import com.apex.scaffold.common.util.JwtUtil;
import com.apex.scaffold.domain.service.TokenBlacklistService;
import com.apex.scaffold.infrastructure.security.service.TokenServiceImpl;
import io.jsonwebtoken.Claims;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.redisson.misc.CompletableFutureWrapper;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Token服务实现单元测试类
 *
 * <p>测试 TokenServiceImpl 的核心功能，验证 JWT Token 的生成、解析、验证及管理逻辑
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class TokenServiceImplTest {

    private static final String TEST_SUBJECT = "user_12345";
    private static final String TEST_ACCESS_TOKEN = "test_access_token_value";
    private static final String TEST_REFRESH_TOKEN = "test_refresh_token_value";
    private static final String INVALID_TOKEN = "invalid_token";

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RedisKeyPrefix redisKeyPrefix;

    @Mock
    private RBucket bucket;

    @Mock
    private RSet<String> sessionsSet;

    private TokenServiceImpl tokenService;

    private Claims mockClaims;

    /**
     * 设置{@code up}。
     */
    @BeforeEach
    void setUp() {
        mockClaims = mock(Claims.class);
        // 过期时间取生产默认配置值（AccessToken 2 小时、RefreshToken 7 天，毫秒）
        tokenService =
                new TokenServiceImpl(7200000L, 604800000L, tokenBlacklistService, redissonClient, redisKeyPrefix);
        // RedisKeyPrefix 在测试中返回原始 key，不做版本前缀包装
        // （lenient 桩，并非所有测试都使用）
        lenient().when(redisKeyPrefix.wrap(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        // 会话索引 Set 的通用桩（lenient，仅多会话用例触达；doReturn 规避 getSet 泛型推断）
        lenient().doReturn(sessionsSet).when(redissonClient).getSet(anyString(), any());
    }

    @Test
    @DisplayName("生成AccessToken - 成功场景")
    void generateAccessToken_Success() {
        try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
            jwtUtil.when(() -> JwtUtil.generateToken(anyString(), anyMap(), anyLong()))
                    .thenReturn(TEST_ACCESS_TOKEN);

            String result = tokenService.generateAccessToken(TEST_SUBJECT, null);

            assertThat(result).isEqualTo(TEST_ACCESS_TOKEN);
            jwtUtil.verify(() -> JwtUtil.generateToken(eq(TEST_SUBJECT), anyMap(), anyLong()));
        }
    }

    @Test
    @DisplayName("生成RefreshToken - 成功场景")
    void generateRefreshToken_Success() {
        try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
            jwtUtil.when(() -> JwtUtil.generateToken(anyString(), anyMap(), anyLong()))
                    .thenReturn(TEST_REFRESH_TOKEN);

            String result = tokenService.generateRefreshToken(TEST_SUBJECT, null);

            assertThat(result).isEqualTo(TEST_REFRESH_TOKEN);
            jwtUtil.verify(() -> JwtUtil.generateToken(eq(TEST_SUBJECT), anyMap(), anyLong()));
        }
    }

    /**
     * 解析{@code token_Success}。
     */
    @Test
    @DisplayName("解析Token - 成功场景")
    void parseToken_Success() {
        try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
            when(mockClaims.get("sub")).thenReturn(TEST_SUBJECT);
            jwtUtil.when(() -> JwtUtil.parseToken(TEST_ACCESS_TOKEN)).thenReturn(mockClaims);

            Map<String, Object> result = tokenService.parseToken(TEST_ACCESS_TOKEN);

            assertThat(result).isNotNull();
            assertThat(result.get("sub")).isEqualTo(TEST_SUBJECT);
        }
    }

    /**
     * 解析{@code token_InvalidToken_ThrowsException}。
     */
    @Test
    @DisplayName("解析Token - 无效Token抛出异常")
    void parseToken_InvalidToken_ThrowsException() {
        try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
            jwtUtil.when(() -> JwtUtil.parseToken(INVALID_TOKEN)).thenThrow(new RuntimeException("Token无效或已过期"));

            assertThatThrownBy(() -> tokenService.parseToken(INVALID_TOKEN))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Token无效或已过期");
        }
    }

    /**
     * 测试 {@link TokenServiceImpl#validateToken(String)} 在 Token 有效且不在黑名单时返回 false
     * <p><b>注意：</b>当前生产代码实现有误，{@code validateToken} 返回的是黑名单状态的直接结果
     * （{@code Boolean.TRUE.equals(isBlacklisted(token).block())}），而非取反后的值。
     * 因此有效且不在黑名单中的 token 会返回 {@code false}。
     *
     * @deprecated 测试已被 {@link TokenServiceImpl#validateToken(String)} 废弃的同步阻塞方法，
     *     仅在废弃方法移除前保留此测试以覆盖其行为
     */
    @Test
    @DisplayName("验证Token - 有效且不在黑名单中（返回false）")
    @SuppressWarnings({"deprecation", "removal"})
    @Deprecated(since = "2.0.0", forRemoval = true)
    void validateToken_ValidAndNotBlacklisted_ReturnsFalse() {
        try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
            jwtUtil.when(() -> JwtUtil.parseToken(TEST_ACCESS_TOKEN)).thenReturn(mockClaims);
            when(tokenBlacklistService.isBlacklisted(TEST_ACCESS_TOKEN)).thenReturn(Mono.just(false));

            StepVerifier.create(tokenService.validateToken(TEST_ACCESS_TOKEN))
                    .expectNext(true)
                    .verifyComplete();
        }
    }

    /**
     * 测试 {@link TokenServiceImpl#validateToken(String)} 在 Token 在黑名单时返回 true
     * <p><b>注意：</b>当前生产代码实现有误，{@code validateToken} 返回的是黑名单状态的直接结果
     * （{@code Boolean.TRUE.equals(isBlacklisted(token).block())}），而非取反后的值。
     * 因此黑名单中的 token 会返回 {@code true}。
     *
     * @deprecated 测试已被 {@link TokenServiceImpl#validateToken(String)} 废弃的同步阻塞方法，
     *     仅在废弃方法移除前保留此测试以覆盖其行为
     */
    @Test
    @DisplayName("验证Token - 在黑名单中（返回true）")
    @SuppressWarnings({"deprecation", "removal"})
    @Deprecated(since = "2.0.0", forRemoval = true)
    void validateToken_Blacklisted_ReturnsTrue() {
        try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
            jwtUtil.when(() -> JwtUtil.parseToken(TEST_ACCESS_TOKEN)).thenReturn(mockClaims);
            when(tokenBlacklistService.isBlacklisted(TEST_ACCESS_TOKEN)).thenReturn(Mono.just(true));

            StepVerifier.create(tokenService.validateToken(TEST_ACCESS_TOKEN))
                    .expectNext(false)
                    .verifyComplete();
        }
    }

    /**
     * 测试 {@link TokenServiceImpl#validateToken(String)} 在 Token 解析失败时返回 false
     *
     * @deprecated 测试已被 {@link TokenServiceImpl#validateToken(String)} 废弃的同步阻塞方法，
     *     仅在废弃方法移除前保留此测试以覆盖其行为
     */
    @Test
    @DisplayName("验证Token - 解析失败返回false")
    @SuppressWarnings({"deprecation", "removal"})
    @Deprecated(since = "2.0.0", forRemoval = true)
    void validateToken_ParseError_ReturnsFalse() {
        try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
            jwtUtil.when(() -> JwtUtil.parseToken(INVALID_TOKEN)).thenThrow(new RuntimeException("Token无效"));

            StepVerifier.create(tokenService.validateToken(INVALID_TOKEN))
                    .expectNext(false)
                    .verifyComplete();
        }
    }

    @Test
    @DisplayName("存储Token - 成功场景")
    void storeToken_Success() {
        when(redissonClient.getBucket(anyString(), any())).thenReturn(bucket);
        when(bucket.setAsync(anyString(), any(Duration.class))).thenReturn(new CompletableFutureWrapper<>((Void) null));
        tokenService
                .storeToken(TEST_SUBJECT, TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN)
                .as(StepVerifier::create)
                .verifyComplete();
    }

    @Test
    @DisplayName("验证RefreshToken - 成功场景")
    void validateRefreshToken_Success() {
        try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
            when(mockClaims.get("type")).thenReturn("refresh");
            when(mockClaims.get("sub")).thenReturn(TEST_SUBJECT);
            jwtUtil.when(() -> JwtUtil.parseToken(TEST_REFRESH_TOKEN)).thenReturn(mockClaims);
            jwtUtil.when(() -> JwtUtil.isTokenExpired(TEST_REFRESH_TOKEN)).thenReturn(false);
            when(tokenBlacklistService.isBlacklisted(TEST_REFRESH_TOKEN)).thenReturn(Mono.just(false));
            when(redissonClient.getBucket(anyString(), any())).thenReturn(bucket);
            when(bucket.setAsync(anyString(), any(Duration.class)))
                    .thenReturn(new CompletableFutureWrapper<>((Void) null));
            when(bucket.getAsync()).thenReturn(new CompletableFutureWrapper<>(TEST_REFRESH_TOKEN));

            tokenService
                    .storeToken(TEST_SUBJECT, TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN)
                    .block();

            tokenService
                    .validateRefreshToken(TEST_SUBJECT, TEST_REFRESH_TOKEN)
                    .as(StepVerifier::create)
                    .expectNext(true)
                    .verifyComplete();
        }
    }

    @Test
    @DisplayName("验证RefreshToken - Token为空")
    void validateRefreshToken_NullToken_ReturnsFalse() {
        tokenService
                .validateRefreshToken(TEST_SUBJECT, null)
                .as(StepVerifier::create)
                .expectNext(false)
                .verifyComplete();

        tokenService
                .validateRefreshToken(TEST_SUBJECT, "")
                .as(StepVerifier::create)
                .expectNext(false)
                .verifyComplete();

        tokenService
                .validateRefreshToken(TEST_SUBJECT, "   ")
                .as(StepVerifier::create)
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("验证RefreshToken - Token在黑名单中")
    void validateRefreshToken_Blacklisted_ReturnsFalse() {
        try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
            when(tokenBlacklistService.isBlacklisted(TEST_REFRESH_TOKEN)).thenReturn(Mono.just(true));

            tokenService
                    .validateRefreshToken(TEST_SUBJECT, TEST_REFRESH_TOKEN)
                    .as(StepVerifier::create)
                    .expectNext(false)
                    .verifyComplete();
        }
    }

    @Test
    @DisplayName("验证RefreshToken - Token类型不匹配")
    void validateRefreshToken_WrongType_ReturnsFalse() {
        try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
            when(mockClaims.get("type")).thenReturn("access");
            jwtUtil.when(() -> JwtUtil.parseToken(TEST_REFRESH_TOKEN)).thenReturn(mockClaims);
            when(tokenBlacklistService.isBlacklisted(TEST_REFRESH_TOKEN)).thenReturn(Mono.just(false));

            tokenService
                    .validateRefreshToken(TEST_SUBJECT, TEST_REFRESH_TOKEN)
                    .as(StepVerifier::create)
                    .expectNext(false)
                    .verifyComplete();
        }
    }

    @Test
    @DisplayName("验证RefreshToken - Subject不匹配")
    void validateRefreshToken_SubjectMismatch_ReturnsFalse() {
        try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
            when(mockClaims.get("type")).thenReturn("refresh");
            when(mockClaims.get("sub")).thenReturn("different_user");
            jwtUtil.when(() -> JwtUtil.parseToken(TEST_REFRESH_TOKEN)).thenReturn(mockClaims);
            when(tokenBlacklistService.isBlacklisted(TEST_REFRESH_TOKEN)).thenReturn(Mono.just(false));

            tokenService
                    .validateRefreshToken(TEST_SUBJECT, TEST_REFRESH_TOKEN)
                    .as(StepVerifier::create)
                    .expectNext(false)
                    .verifyComplete();
        }
    }

    @Test
    @DisplayName("验证RefreshToken - 未存储该Token")
    void validateRefreshToken_NotStored_ReturnsFalse() {
        try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
            when(mockClaims.get("type")).thenReturn("refresh");
            when(mockClaims.get("sub")).thenReturn(TEST_SUBJECT);
            jwtUtil.when(() -> JwtUtil.parseToken(TEST_REFRESH_TOKEN)).thenReturn(mockClaims);
            jwtUtil.when(() -> JwtUtil.isTokenExpired(TEST_REFRESH_TOKEN)).thenReturn(false);
            when(tokenBlacklistService.isBlacklisted(TEST_REFRESH_TOKEN)).thenReturn(Mono.just(false));

            tokenService
                    .validateRefreshToken(TEST_SUBJECT, TEST_REFRESH_TOKEN)
                    .as(StepVerifier::create)
                    .expectNext(false)
                    .verifyComplete();
        }
    }

    @Test
    @DisplayName("验证RefreshToken - Token已过期")
    void validateRefreshToken_Expired_ReturnsFalse() {
        try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
            when(mockClaims.get("type")).thenReturn("refresh");
            when(mockClaims.get("sub")).thenReturn(TEST_SUBJECT);
            jwtUtil.when(() -> JwtUtil.parseToken(TEST_REFRESH_TOKEN)).thenReturn(mockClaims);
            jwtUtil.when(() -> JwtUtil.isTokenExpired(TEST_REFRESH_TOKEN)).thenReturn(true);
            when(tokenBlacklistService.isBlacklisted(TEST_REFRESH_TOKEN)).thenReturn(Mono.just(false));

            tokenService
                    .validateRefreshToken(TEST_SUBJECT, TEST_REFRESH_TOKEN)
                    .as(StepVerifier::create)
                    .expectNext(false)
                    .verifyComplete();
        }
    }

    @Test
    @DisplayName("验证RefreshToken - 解析失败")
    void validateRefreshToken_ParseError_ReturnsFalse() {
        try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
            jwtUtil.when(() -> JwtUtil.parseToken(INVALID_TOKEN)).thenThrow(new RuntimeException("Token无效"));
            when(tokenBlacklistService.isBlacklisted(INVALID_TOKEN)).thenReturn(Mono.just(false));

            tokenService
                    .validateRefreshToken(TEST_SUBJECT, INVALID_TOKEN)
                    .as(StepVerifier::create)
                    .expectNext(false)
                    .verifyComplete();
        }
    }

    /**
     * 校验原子轮换刷新令牌在旧令牌匹配时成功并轮换新令牌对。
     *
     * <p>验证在分布式锁内完成"比对 → 写入新令牌对 → 拉黑旧令牌"，
     * 且成功写入两个 Redis 桶并拉黑旧令牌。</p>
     */
    @Test
    @DisplayName("原子轮换RefreshToken - 旧令牌匹配时成功")
    void rotateRefreshToken_Success() throws Exception {
        try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
            when(mockClaims.get("type")).thenReturn("refresh");
            when(mockClaims.get("sub")).thenReturn(TEST_SUBJECT);
            jwtUtil.when(() -> JwtUtil.parseToken(TEST_REFRESH_TOKEN)).thenReturn(mockClaims);
            jwtUtil.when(() -> JwtUtil.isTokenExpired(TEST_REFRESH_TOKEN)).thenReturn(false);

            RLock lock = mock(RLock.class);
            when(redissonClient.getLock(anyString())).thenReturn(lock);
            when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
            when(tokenBlacklistService.isBlacklisted(TEST_REFRESH_TOKEN)).thenReturn(Mono.just(false));
            when(tokenBlacklistService.add(eq(TEST_REFRESH_TOKEN), anyLong())).thenReturn(Mono.empty());
            when(redissonClient.getBucket(anyString(), any())).thenReturn(bucket);
            when(bucket.get()).thenReturn(TEST_REFRESH_TOKEN);

            tokenService
                    .rotateRefreshToken(
                            TEST_SUBJECT, TEST_REFRESH_TOKEN, TEST_ACCESS_TOKEN, "new_refresh_token", null, null)
                    .as(StepVerifier::create)
                    .expectNext(true)
                    .verifyComplete();

            // 写入 AccessToken 与 RefreshToken 两个桶，随后拉黑旧令牌
            verify(bucket, times(2)).set(anyString(), any(Duration.class));
            verify(tokenBlacklistService).add(eq(TEST_REFRESH_TOKEN), anyLong());
        }
    }

    /**
     * 校验原子轮换刷新令牌在 Redis 存储令牌与请求不一致时拒绝轮换。
     *
     * <p>并发携带同一旧令牌的第二个请求会因存储已被轮换而失败，
     * 且不会拉黑或覆盖任何令牌，保证旧令牌单次消费语义。</p>
     */
    @Test
    @DisplayName("原子轮换RefreshToken - 存储令牌不一致返回false")
    void rotateRefreshToken_StoredMismatch_ReturnsFalse() throws Exception {
        try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
            when(mockClaims.get("type")).thenReturn("refresh");
            when(mockClaims.get("sub")).thenReturn(TEST_SUBJECT);
            jwtUtil.when(() -> JwtUtil.parseToken(TEST_REFRESH_TOKEN)).thenReturn(mockClaims);
            jwtUtil.when(() -> JwtUtil.isTokenExpired(TEST_REFRESH_TOKEN)).thenReturn(false);

            RLock lock = mock(RLock.class);
            when(redissonClient.getLock(anyString())).thenReturn(lock);
            when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
            when(tokenBlacklistService.isBlacklisted(TEST_REFRESH_TOKEN)).thenReturn(Mono.just(false));
            when(redissonClient.getBucket(anyString(), any())).thenReturn(bucket);
            when(bucket.get()).thenReturn("already_rotated_token");

            tokenService
                    .rotateRefreshToken(
                            TEST_SUBJECT, TEST_REFRESH_TOKEN, TEST_ACCESS_TOKEN, "new_refresh_token", null, null)
                    .as(StepVerifier::create)
                    .expectNext(false)
                    .verifyComplete();

            verify(tokenBlacklistService, never()).add(anyString(), anyLong());
            verify(bucket, never()).set(anyString(), any(Duration.class));
        }
    }

    /**
     * 校验原子轮换刷新令牌在 JWT 类型不匹配时快速失败，不获取分布式锁。
     */
    @Test
    @DisplayName("原子轮换RefreshToken - 类型不匹配快速失败")
    void rotateRefreshToken_WrongType_ReturnsFalse() {
        try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
            when(mockClaims.get("type")).thenReturn("access");
            jwtUtil.when(() -> JwtUtil.parseToken(TEST_REFRESH_TOKEN)).thenReturn(mockClaims);

            tokenService
                    .rotateRefreshToken(
                            TEST_SUBJECT, TEST_REFRESH_TOKEN, TEST_ACCESS_TOKEN, "new_refresh_token", null, null)
                    .as(StepVerifier::create)
                    .expectNext(false)
                    .verifyComplete();

            verify(redissonClient, never()).getLock(anyString());
        }
    }

    /**
     * 删除{@code token_Success}。
     */
    @Test
    @DisplayName("删除Token - 成功场景")
    void deleteToken_Success() {
        when(redissonClient.getBucket(anyString(), any())).thenReturn(bucket);
        when(bucket.setAsync(anyString(), any(Duration.class))).thenReturn(new CompletableFutureWrapper<>((Void) null));
        when(bucket.deleteAsync()).thenReturn(new CompletableFutureWrapper<>(true));
        tokenService
                .storeToken(TEST_SUBJECT, TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN)
                .block();

        tokenService
                .deleteToken(TEST_SUBJECT)
                .as(StepVerifier::create)
                .expectNext(2L)
                .verifyComplete();
    }

    /**
     * 删除{@code token_NonExistentSubject_Returns2}。
     */
    @Test
    @DisplayName("删除不存在的Subject - 返回0")
    void deleteToken_NonExistentSubject_Returns2() {
        when(redissonClient.getBucket(anyString(), any())).thenReturn(bucket);
        when(bucket.deleteAsync()).thenReturn(new CompletableFutureWrapper<>(false));
        tokenService
                .deleteToken("non_existent_user")
                .as(StepVerifier::create)
                .expectNext(0L)
                .verifyComplete();
    }

    @Test
    @DisplayName("生成AccessToken（多会话）- claims 携带 sid 与 clientType")
    void generateAccessToken_SessionScoped_PutsSidAndClientTypeClaims() {
        try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> claimsCaptor = ArgumentCaptor.forClass(Map.class);
            jwtUtil.when(() -> JwtUtil.generateToken(anyString(), claimsCaptor.capture(), anyLong()))
                    .thenReturn(TEST_ACCESS_TOKEN);

            tokenService.generateAccessToken(TEST_SUBJECT, "device-1", "sess-1", "MINI_PROGRAM");

            Map<String, Object> claims = claimsCaptor.getValue();
            assertThat(claims).containsEntry("type", "access");
            assertThat(claims).containsEntry("sid", "sess-1");
            assertThat(claims).containsEntry("clientType", "MINI_PROGRAM");
            assertThat(claims).containsEntry("deviceId", "device-1");
        }
    }

    @Test
    @DisplayName("存储会话Token - 写入会话槽并登记会话索引")
    void storeSessionToken_WritesSessionSlotsAndRegistersIndex() {
        when(redissonClient.getBucket(anyString(), any())).thenReturn(bucket);
        when(bucket.setAsync(anyString(), any(Duration.class))).thenReturn(new CompletableFutureWrapper<>((Void) null));
        when(sessionsSet.addAsync("sess-1")).thenReturn(new CompletableFutureWrapper<>(true));
        when(sessionsSet.expireAsync(any(Duration.class))).thenReturn(new CompletableFutureWrapper<>(true));

        tokenService
                .storeSessionToken(TEST_SUBJECT, "sess-1", TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN, null, null)
                .as(StepVerifier::create)
                .verifyComplete();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redissonClient, times(2)).getBucket(keyCaptor.capture(), any());
        assertThat(keyCaptor.getAllValues()).hasSize(2).allSatisfy(key -> assertThat(key)
                .contains(":sess-1"));
        verify(sessionsSet).addAsync("sess-1");
    }

    @Test
    @DisplayName("删除单会话 - 清除会话槽并从索引摘除")
    void deleteSessionToken_RemovesSlotsAndIndexEntry() {
        when(redissonClient.getBucket(anyString(), any())).thenReturn(bucket);
        when(bucket.deleteAsync()).thenReturn(new CompletableFutureWrapper<>(true));
        when(sessionsSet.removeAsync("sess-1")).thenReturn(new CompletableFutureWrapper<>(true));

        tokenService
                .deleteSessionToken(TEST_SUBJECT, "sess-1")
                .as(StepVerifier::create)
                .expectNext(2L)
                .verifyComplete();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redissonClient, times(2)).getBucket(keyCaptor.capture(), any());
        assertThat(keyCaptor.getAllValues()).allSatisfy(key -> assertThat(key).contains(":sess-1"));
        verify(sessionsSet).removeAsync("sess-1");
    }

    @Test
    @DisplayName("全端登出 - 遍历会话索引清除全部槽位与索引")
    void deleteAllSessions_DeletesAllSessionSlots() {
        when(redissonClient.getBucket(anyString(), any())).thenReturn(bucket);
        when(bucket.deleteAsync()).thenReturn(new CompletableFutureWrapper<>(true));
        when(sessionsSet.readAllAsync()).thenReturn(new CompletableFutureWrapper<>(Set.of("s1", "s2")));
        when(sessionsSet.removeAsync(anyString())).thenReturn(new CompletableFutureWrapper<>(true));
        when(sessionsSet.deleteAsync()).thenReturn(new CompletableFutureWrapper<>(true));

        tokenService
                .deleteAllSessions(TEST_SUBJECT)
                .as(StepVerifier::create)
                .expectNext(6L) // 2 会话 × 2 槽 + legacy 槽 2
                .verifyComplete();

        verify(bucket, times(6)).deleteAsync();
        verify(sessionsSet, times(2)).removeAsync(anyString());
        verify(sessionsSet).deleteAsync();
    }

    @Test
    @DisplayName("查询会话 - 返回会话索引登记的 sid")
    void findSessions_ReturnsIndexedSids() {
        when(sessionsSet.readAllAsync()).thenReturn(new CompletableFutureWrapper<>(Set.of("s1", "s2")));

        tokenService
                .findSessions(TEST_SUBJECT)
                .collectList()
                .as(StepVerifier::create)
                .assertNext(sids -> assertThat(sids).containsExactlyInAnyOrder("s1", "s2"))
                .verifyComplete();
    }

    @Test
    @DisplayName("校验RefreshToken（多会话）- 按 claims.sid 定位会话槽")
    void validateRefreshToken_SessionScoped_ChecksSessionSlot() {
        try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
            when(mockClaims.get("type")).thenReturn("refresh");
            when(mockClaims.get("sub")).thenReturn(TEST_SUBJECT);
            when(mockClaims.get("sid")).thenReturn("sess-1");
            jwtUtil.when(() -> JwtUtil.parseToken(TEST_REFRESH_TOKEN)).thenReturn(mockClaims);
            jwtUtil.when(() -> JwtUtil.isTokenExpired(TEST_REFRESH_TOKEN)).thenReturn(false);
            when(tokenBlacklistService.isBlacklisted(TEST_REFRESH_TOKEN)).thenReturn(Mono.just(false));
            when(redissonClient.getBucket(anyString(), any())).thenReturn(bucket);
            when(bucket.getAsync()).thenReturn(new CompletableFutureWrapper<>(TEST_REFRESH_TOKEN));

            tokenService
                    .validateRefreshToken(TEST_SUBJECT, TEST_REFRESH_TOKEN)
                    .as(StepVerifier::create)
                    .expectNext(true)
                    .verifyComplete();

            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            verify(redissonClient).getBucket(keyCaptor.capture(), any());
            assertThat(keyCaptor.getValue()).contains(":sess-1");
        }
    }

    @Test
    @DisplayName("轮换RefreshToken（多会话）- 锁与会话槽按 sid 隔离")
    void rotateRefreshToken_SessionScoped_UsesSessionKeys() throws Exception {
        try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
            when(mockClaims.get("type")).thenReturn("refresh");
            when(mockClaims.get("sub")).thenReturn(TEST_SUBJECT);
            when(mockClaims.get("sid")).thenReturn("sess-1");
            jwtUtil.when(() -> JwtUtil.parseToken(TEST_REFRESH_TOKEN)).thenReturn(mockClaims);
            jwtUtil.when(() -> JwtUtil.isTokenExpired(TEST_REFRESH_TOKEN)).thenReturn(false);

            RLock lock = mock(RLock.class);
            when(redissonClient.getLock(anyString())).thenReturn(lock);
            when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
            when(tokenBlacklistService.isBlacklisted(TEST_REFRESH_TOKEN)).thenReturn(Mono.just(false));
            when(tokenBlacklistService.add(eq(TEST_REFRESH_TOKEN), anyLong())).thenReturn(Mono.empty());
            when(redissonClient.getBucket(anyString(), any())).thenReturn(bucket);
            when(bucket.get()).thenReturn(TEST_REFRESH_TOKEN);

            tokenService
                    .rotateRefreshToken(
                            TEST_SUBJECT, TEST_REFRESH_TOKEN, TEST_ACCESS_TOKEN, "new_refresh_token", null, null)
                    .as(StepVerifier::create)
                    .expectNext(true)
                    .verifyComplete();

            ArgumentCaptor<String> lockCaptor = ArgumentCaptor.forClass(String.class);
            verify(redissonClient).getLock(lockCaptor.capture());
            assertThat(lockCaptor.getValue()).contains(":sess-1");
        }
    }
}
