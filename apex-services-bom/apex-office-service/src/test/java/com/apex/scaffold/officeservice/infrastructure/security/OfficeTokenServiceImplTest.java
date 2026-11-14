package com.apex.scaffold.officeservice.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.config.RedisKeyPrefix;
import com.apex.scaffold.common.dto.TokenStoreEntry;
import com.apex.scaffold.common.util.JwtUtil;
import com.apex.scaffold.domain.service.TokenBlacklistService;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveSetOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("OfficeTokenServiceImpl 单元测试")
class OfficeTokenServiceImplTest {

    private static final String SECRET_KEY = "office-token-service-unit-test-secret-key-32chars";

    private ReactiveStringRedisTemplate redisTemplate;
    private ReactiveValueOperations<String, String> valueOperations;
    private ReactiveSetOperations<String, String> setOperations;
    private RedisKeyPrefix redisKeyPrefix;
    private TokenBlacklistService tokenBlacklistService;
    private OfficeTokenServiceImpl service;

    @BeforeAll
    static void configureJwt() {
        System.setProperty("jwt.secret", SECRET_KEY);
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(ReactiveStringRedisTemplate.class);
        valueOperations = mock(ReactiveValueOperations.class);
        setOperations = mock(ReactiveSetOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        redisKeyPrefix = mock(RedisKeyPrefix.class);
        when(redisKeyPrefix.wrap(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        tokenBlacklistService = mock(TokenBlacklistService.class);
        when(tokenBlacklistService.isBlacklisted(anyString())).thenReturn(Mono.just(false));
        when(tokenBlacklistService.add(anyString(), anyLong())).thenReturn(Mono.empty());
        when(valueOperations.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(true));
        when(valueOperations.delete(anyString())).thenReturn(Mono.just(true));
        when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        when(setOperations.add(anyString(), org.mockito.ArgumentMatchers.<String>any()))
                .thenReturn(Mono.just(1L));
        when(setOperations.remove(anyString(), anyString())).thenReturn(Mono.just(1L));
        service = new OfficeTokenServiceImpl(redisTemplate, redisKeyPrefix, tokenBlacklistService);
        ReflectionTestUtils.setField(service, "accessTokenExpireTime", 7200000L);
        ReflectionTestUtils.setField(service, "refreshTokenExpireTime", 604800000L);
    }

    private String refreshToken(String subject, String sid) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "refresh");
        if (sid != null) {
            claims.put("sid", sid);
        }
        return JwtUtil.generateToken(subject, claims, 3600000L);
    }

    @Nested
    @DisplayName("generateAccessToken")
    class GenerateAccessTokenTest {

        @Test
        @DisplayName("两参重载生成可解析的 access 令牌")
        void twoArgOverload() {
            String token = service.generateAccessToken("user-1", "device-1");
            Map<String, Object> claims = JwtUtil.parseToken(token);
            assertThat(claims.get("sub")).isEqualTo("user-1");
            assertThat(claims.get("type")).isEqualTo("access");
            assertThat(claims.get("deviceId")).isEqualTo("device-1");
            assertThat(claims).doesNotContainKey("sid");
            assertThat(claims).doesNotContainKey("clientType");
        }

        @Test
        @DisplayName("四参重载携带 sid 与 clientType")
        void fourArgOverload() {
            String token = service.generateAccessToken("user-1", "device-1", "sid-1", "PC");
            Map<String, Object> claims = JwtUtil.parseToken(token);
            assertThat(claims.get("sid")).isEqualTo("sid-1");
            assertThat(claims.get("clientType")).isEqualTo("PC");
        }

        @Test
        @DisplayName("空白 deviceId/sid/clientType 时跳过对应 claims")
        void blankOptionalClaims() {
            String token = service.generateAccessToken("user-1", "  ", "  ", "  ");
            Map<String, Object> claims = JwtUtil.parseToken(token);
            assertThat(claims).doesNotContainKey("deviceId");
            assertThat(claims).doesNotContainKey("sid");
            assertThat(claims).doesNotContainKey("clientType");
        }
    }

    @Nested
    @DisplayName("generateRefreshToken")
    class GenerateRefreshTokenTest {

        @Test
        @DisplayName("两参重载生成 refresh 令牌")
        void twoArgOverload() {
            String token = service.generateRefreshToken("user-1", null);
            Map<String, Object> claims = JwtUtil.parseToken(token);
            assertThat(claims.get("type")).isEqualTo("refresh");
            assertThat(claims).doesNotContainKey("deviceId");
        }

        @Test
        @DisplayName("四参重载携带 sid 与 clientType")
        void fourArgOverload() {
            String token = service.generateRefreshToken("user-1", "device-1", "sid-1", "PC");
            Map<String, Object> claims = JwtUtil.parseToken(token);
            assertThat(claims.get("sid")).isEqualTo("sid-1");
            assertThat(claims.get("clientType")).isEqualTo("PC");
        }
    }

    @Test
    @DisplayName("parseToken 委托 JwtUtil")
    void parseToken() {
        String token = service.generateAccessToken("user-1", "device-1");
        assertThat(service.parseToken(token).get("sub")).isEqualTo("user-1");
    }

    @Nested
    @DisplayName("validateToken")
    class ValidateTokenTest {

        @Test
        @DisplayName("合法且未拉黑令牌返回 true")
        void validAndNotBlacklisted() {
            String token = service.generateAccessToken("user-1", "device-1");
            StepVerifier.create(service.validateToken(token)).expectNext(true).verifyComplete();
        }

        @Test
        @DisplayName("已拉黑令牌返回 false")
        void blacklisted() {
            String token = service.generateAccessToken("user-1", "device-1");
            when(tokenBlacklistService.isBlacklisted(token)).thenReturn(Mono.just(true));
            StepVerifier.create(service.validateToken(token)).expectNext(false).verifyComplete();
        }

        @Test
        @DisplayName("非法令牌返回 false")
        void invalidToken() {
            StepVerifier.create(service.validateToken("not-a-jwt"))
                    .expectNext(false)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("storeToken / storeSessionToken")
    class StoreTokenTest {

        @Test
        @DisplayName("legacy 三参存储写入 access/refresh 键")
        void legacyStore() {
            StepVerifier.create(service.storeToken("user-1", "access-1", "refresh-1"))
                    .verifyComplete();
            verify(valueOperations).set("token:access:user-1", "access-1", Duration.ofMillis(7200000L));
            verify(valueOperations).set("token:refresh:user-1", "refresh-1", Duration.ofMillis(604800000L));
        }

        @Test
        @DisplayName("携带 TokenStoreEntry 时存储为 JSON")
        void storeWithEntryAsJson() {
            TokenStoreEntry entry = TokenStoreEntry.builder().userId(1L).build();
            StepVerifier.create(service.storeToken("user-1", "access-1", "refresh-1", entry, null))
                    .verifyComplete();
            verify(valueOperations).set(anyString(), org.mockito.ArgumentMatchers.startsWith("{"), any(Duration.class));
        }

        @Test
        @DisplayName("sid 为空退化为 legacy 单会话槽")
        void sessionBlankSidFallsBackToLegacy() {
            StepVerifier.create(service.storeSessionToken("user-1", "  ", "access-1", "refresh-1", null, null))
                    .verifyComplete();
            verify(valueOperations).set("token:access:user-1", "access-1", Duration.ofMillis(7200000L));
        }

        @Test
        @DisplayName("sid 非空写入会话槽并登记会话索引")
        void sessionStore() {
            StepVerifier.create(service.storeSessionToken("user-1", "sid-1", "access-1", "refresh-1", null, null))
                    .verifyComplete();
            verify(valueOperations).set("token:access:user-1:sid-1", "access-1", Duration.ofMillis(7200000L));
            verify(valueOperations).set("token:refresh:user-1:sid-1", "refresh-1", Duration.ofMillis(604800000L));
            verify(setOperations).add("token:sessions:user-1", "sid-1");
        }
    }

    @Nested
    @DisplayName("storeAccessToken / storeSessionAccessToken")
    class StoreAccessTokenTest {

        @Test
        @DisplayName("存储独立 AccessToken")
        void storeAccessToken() {
            StepVerifier.create(service.storeAccessToken("user-1", "access-1", 60))
                    .verifyComplete();
            verify(valueOperations).set("token:access:user-1", "access-1", Duration.ofSeconds(60));
        }

        @Test
        @DisplayName("sid 为空回退 legacy")
        void sessionAccessBlankSid() {
            StepVerifier.create(service.storeSessionAccessToken("user-1", null, "access-1", 60))
                    .verifyComplete();
            verify(valueOperations).set("token:access:user-1", "access-1", Duration.ofSeconds(60));
        }

        @Test
        @DisplayName("sid 非空写入会话槽")
        void sessionAccess() {
            StepVerifier.create(service.storeSessionAccessToken("user-1", "sid-1", "access-1", 60))
                    .verifyComplete();
            verify(valueOperations).set("token:access:user-1:sid-1", "access-1", Duration.ofSeconds(60));
        }
    }

    @Nested
    @DisplayName("validateRefreshToken")
    class ValidateRefreshTokenTest {

        @Test
        @DisplayName("null 令牌返回 false")
        void nullToken() {
            StepVerifier.create(service.validateRefreshToken("user-1", null))
                    .expectNext(false)
                    .verifyComplete();
        }

        @Test
        @DisplayName("空白令牌返回 false")
        void blankToken() {
            StepVerifier.create(service.validateRefreshToken("user-1", "  "))
                    .expectNext(false)
                    .verifyComplete();
        }

        @Test
        @DisplayName("已拉黑返回 false")
        void blacklisted() {
            when(tokenBlacklistService.isBlacklisted("refresh-1")).thenReturn(Mono.just(true));
            StepVerifier.create(service.validateRefreshToken("user-1", "refresh-1"))
                    .expectNext(false)
                    .verifyComplete();
        }

        @Test
        @DisplayName("令牌类型非 refresh 返回 false")
        void notRefreshType() {
            String accessToken = service.generateAccessToken("user-1", "device-1");
            StepVerifier.create(service.validateRefreshToken("user-1", accessToken))
                    .expectNext(false)
                    .verifyComplete();
        }

        @Test
        @DisplayName("令牌主题不匹配返回 false")
        void subjectMismatch() {
            String token = refreshToken("other-user", null);
            StepVerifier.create(service.validateRefreshToken("user-1", token))
                    .expectNext(false)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Redis 存储值匹配返回 true")
        void matchingStoredValue() {
            String token = refreshToken("user-1", null);
            when(valueOperations.get("token:refresh:user-1")).thenReturn(Mono.just(token));
            StepVerifier.create(service.validateRefreshToken("user-1", token))
                    .expectNext(true)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Redis 无存储值返回 false")
        void absentStoredValue() {
            String token = refreshToken("user-1", null);
            when(valueOperations.get("token:refresh:user-1")).thenReturn(Mono.empty());
            StepVerifier.create(service.validateRefreshToken("user-1", token))
                    .expectNext(false)
                    .verifyComplete();
        }

        @Test
        @DisplayName("JSON 存储值匹配返回 true")
        void jsonStoredValueMatch() {
            String token = refreshToken("user-1", "sid-1");
            TokenStoreEntry entry = TokenStoreEntry.builder().token(token).build();
            when(valueOperations.get("token:refresh:user-1:sid-1"))
                    .thenReturn(Mono.just(com.apex.scaffold.common.util.JsonUtil.toJson(entry)));
            StepVerifier.create(service.validateRefreshToken("user-1", token))
                    .expectNext(true)
                    .verifyComplete();
        }

        @Test
        @DisplayName("JSON 解析失败回退字符串比较返回 false")
        void jsonParseFallback() {
            String token = refreshToken("user-1", null);
            when(valueOperations.get("token:refresh:user-1")).thenReturn(Mono.just("{invalid-json"));
            StepVerifier.create(service.validateRefreshToken("user-1", token))
                    .expectNext(false)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("rotateRefreshToken")
    class RotateRefreshTokenTest {

        @Test
        @DisplayName("主题为空返回 false")
        void blankSubject() {
            StepVerifier.create(service.rotateRefreshToken("  ", "old", "new-a", "new-r", null, null))
                    .expectNext(false)
                    .verifyComplete();
        }

        @Test
        @DisplayName("旧令牌类型非 refresh 返回 false")
        void notRefreshType() {
            String accessToken = service.generateAccessToken("user-1", "device-1");
            StepVerifier.create(service.rotateRefreshToken("user-1", accessToken, "new-a", "new-r", null, null))
                    .expectNext(false)
                    .verifyComplete();
        }

        @Test
        @DisplayName("未获取到锁返回 false")
        void lockNotAcquired() {
            String old = refreshToken("user-1", null);
            when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                    .thenReturn(Mono.just(false));
            StepVerifier.create(service.rotateRefreshToken("user-1", old, "new-a", "new-r", null, null))
                    .expectNext(false)
                    .verifyComplete();
        }

        @Test
        @DisplayName("成功轮换返回 true 并拉黑旧令牌")
        void successfulRotation() {
            String old = refreshToken("user-1", null);
            when(valueOperations.get("token:refresh:user-1")).thenReturn(Mono.just(old));
            StepVerifier.create(service.rotateRefreshToken("user-1", old, "new-a", "new-r", null, null))
                    .expectNext(true)
                    .verifyComplete();
            verify(tokenBlacklistService).add(old, 604800000L);
            verify(redisTemplate).delete("token:refresh:rotate:user-1");
        }

        @Test
        @DisplayName("多会话成功轮换按会话槽写入")
        void multiSessionRotation() {
            String old = refreshToken("user-1", "sid-1");
            when(valueOperations.get("token:refresh:user-1:sid-1")).thenReturn(Mono.just(old));
            StepVerifier.create(service.rotateRefreshToken("user-1", old, "new-a", "new-r", null, null))
                    .expectNext(true)
                    .verifyComplete();
            verify(redisTemplate).delete("token:refresh:rotate:user-1:sid-1");
        }

        @Test
        @DisplayName("旧令牌已拉黑返回 false")
        void blacklistedOldToken() {
            String old = refreshToken("user-1", null);
            when(tokenBlacklistService.isBlacklisted(old)).thenReturn(Mono.just(true));
            StepVerifier.create(service.rotateRefreshToken("user-1", old, "new-a", "new-r", null, null))
                    .expectNext(false)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Redis 存储不一致返回 false")
        void storedMismatch() {
            String old = refreshToken("user-1", null);
            when(valueOperations.get("token:refresh:user-1")).thenReturn(Mono.just("different"));
            StepVerifier.create(service.rotateRefreshToken("user-1", old, "new-a", "new-r", null, null))
                    .expectNext(false)
                    .verifyComplete();
        }

        @Test
        @DisplayName("轮换过程中异常被捕获返回 false")
        void rotationErrorResumed() {
            String old = refreshToken("user-1", null);
            when(valueOperations.get("token:refresh:user-1")).thenReturn(Mono.error(new RuntimeException("boom")));
            StepVerifier.create(service.rotateRefreshToken("user-1", old, "new-a", "new-r", null, null))
                    .expectNext(false)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("deleteToken / deleteSessionToken")
    class DeleteTokenTest {

        @Test
        @DisplayName("legacy 删除返回删除计数")
        void deleteLegacy() {
            StepVerifier.create(service.deleteToken("user-1")).expectNext(2L).verifyComplete();
        }

        @Test
        @DisplayName("sid 为空退化为 legacy 删除")
        void sessionDeleteBlankSid() {
            StepVerifier.create(service.deleteSessionToken("user-1", null))
                    .expectNext(2L)
                    .verifyComplete();
        }

        @Test
        @DisplayName("sid 非空删除会话槽并移除索引")
        void sessionDelete() {
            StepVerifier.create(service.deleteSessionToken("user-1", "sid-1"))
                    .expectNext(2L)
                    .verifyComplete();
            verify(setOperations).remove("token:sessions:user-1", "sid-1");
        }

        @Test
        @DisplayName("无删除时返回 0 且不移除索引")
        void sessionDeleteNothing() {
            when(valueOperations.delete(anyString())).thenReturn(Mono.just(false));
            StepVerifier.create(service.deleteSessionToken("user-1", "sid-1"))
                    .expectNext(0L)
                    .verifyComplete();
            verify(setOperations, never()).remove(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("deleteAllSessions")
    class DeleteAllSessionsTest {

        @Test
        @DisplayName("空白 subject 返回 0")
        void blankSubject() {
            StepVerifier.create(service.deleteAllSessions("  ")).expectNext(0L).verifyComplete();
        }

        @Test
        @DisplayName("遍历会话索引并清理 legacy 槽")
        void withSessions() {
            when(setOperations.members("token:sessions:user-1")).thenReturn(Flux.just("sid-1"));
            StepVerifier.create(service.deleteAllSessions("user-1"))
                    .expectNext(4L)
                    .verifyComplete();
            verify(valueOperations).delete("token:sessions:user-1");
        }
    }

    @Nested
    @DisplayName("findSessions")
    class FindSessionsTest {

        @Test
        @DisplayName("空白 subject 返回空 Flux")
        void blankSubject() {
            StepVerifier.create(service.findSessions(null)).verifyComplete();
        }

        @Test
        @DisplayName("返回会话索引内容")
        void withSessions() {
            when(setOperations.members("token:sessions:user-1")).thenReturn(Flux.just("sid-1", "sid-2"));
            StepVerifier.create(service.findSessions("user-1"))
                    .expectNext("sid-1", "sid-2")
                    .verifyComplete();
        }

        @Test
        @DisplayName("查询异常时返回空 Flux")
        void errorResumed() {
            when(setOperations.members("token:sessions:user-1")).thenReturn(Flux.error(new RuntimeException("boom")));
            StepVerifier.create(service.findSessions("user-1")).verifyComplete();
        }
    }

    @Nested
    @DisplayName("findRefreshToken / findSessionRefreshToken")
    class FindRefreshTokenTest {

        @Test
        @DisplayName("空白 subject 返回空")
        void blankSubject() {
            StepVerifier.create(service.findRefreshToken("  ")).verifyComplete();
        }

        @Test
        @DisplayName("纯字符串存储值原样返回")
        void plainStringValue() {
            when(valueOperations.get("token:refresh:user-1")).thenReturn(Mono.just("refresh-1"));
            StepVerifier.create(service.findRefreshToken("user-1"))
                    .expectNext("refresh-1")
                    .verifyComplete();
        }

        @Test
        @DisplayName("JSON 存储值提取 token 字段")
        void jsonValue() {
            TokenStoreEntry entry = TokenStoreEntry.builder().token("refresh-1").build();
            when(valueOperations.get("token:refresh:user-1:sid-1"))
                    .thenReturn(Mono.just(com.apex.scaffold.common.util.JsonUtil.toJson(entry)));
            StepVerifier.create(service.findSessionRefreshToken("user-1", "sid-1"))
                    .expectNext("refresh-1")
                    .verifyComplete();
        }

        @Test
        @DisplayName("无存储值返回空")
        void absentValue() {
            when(valueOperations.get("token:refresh:user-1")).thenReturn(Mono.empty());
            StepVerifier.create(service.findRefreshToken("user-1")).verifyComplete();
        }

        @Test
        @DisplayName("JSON 解析失败返回空")
        void invalidJsonValue() {
            when(valueOperations.get("token:refresh:user-1")).thenReturn(Mono.just("{invalid-json"));
            StepVerifier.create(service.findRefreshToken("user-1")).verifyComplete();
        }

        @Test
        @DisplayName("JSON token 为空白返回空")
        void blankJsonToken() {
            TokenStoreEntry entry = TokenStoreEntry.builder().token("  ").build();
            when(valueOperations.get("token:refresh:user-1"))
                    .thenReturn(Mono.just(com.apex.scaffold.common.util.JsonUtil.toJson(entry)));
            StepVerifier.create(service.findRefreshToken("user-1")).verifyComplete();
        }
    }
}
