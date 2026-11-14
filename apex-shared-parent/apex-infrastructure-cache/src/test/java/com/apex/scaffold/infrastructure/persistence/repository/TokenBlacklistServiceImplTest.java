package com.apex.scaffold.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.util.JwtUtil;
import com.apex.scaffold.common.util.SensitiveDataUtil;
import io.jsonwebtoken.Claims;
import java.time.Duration;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * TokenBlacklistServiceImpl Token黑名单服务实现测试
 *
 * <p>验证 Token 加入黑名单、黑名单查询、异常处理以及与 JWT jti 交互的核心逻辑
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TokenBlacklistServiceImpl Token黑名单服务测试")
class TokenBlacklistServiceImplTest {

    private static final String TEST_JTI = "test-jti-12345";
    private static final String TEST_TOKEN = "test-token-value";
    private static final String BLACKLIST_PREFIX = "token:blacklist:";
    private static final String SUBJECT_REVOKED_PREFIX = "token:blacklist:subject:";

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOps;

    @InjectMocks
    private TokenBlacklistServiceImpl tokenBlacklistService;

    @Captor
    private ArgumentCaptor<String> keyCaptor;

    @Captor
    private ArgumentCaptor<String> valueCaptor;

    @Captor
    private ArgumentCaptor<Duration> ttlCaptor;

    /**
     * 设置{@code up}。
     */
    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("应该将Token加入黑名单并设置正确的键前缀和TTL")
    void shouldAddTokenToBlacklist() {
        try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
            Claims claims = mock(Claims.class);
            when(claims.getId()).thenReturn(TEST_JTI);
            Date futureExpiration = new Date(System.currentTimeMillis() + 3600000);
            when(claims.getExpiration()).thenReturn(futureExpiration);
            jwtUtil.when(() -> JwtUtil.parseToken(anyString())).thenReturn(claims);
            when(valueOps.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));

            tokenBlacklistService
                    .add(TEST_TOKEN, 3600000)
                    .as(StepVerifier::create)
                    .verifyComplete();

            verify(valueOps).set(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture());

            assertThat(keyCaptor.getValue()).isEqualTo(BLACKLIST_PREFIX + TEST_JTI);
            assertThat(valueCaptor.getValue()).isEqualTo("1");
            assertThat(ttlCaptor.getValue()).isPositive();
        }
    }

    @Test
    @DisplayName("应该正确识别在黑名单中的Token")
    void shouldCheckTokenInBlacklist() {
        try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
            Claims claims = mock(Claims.class);
            when(claims.getId()).thenReturn(TEST_JTI);
            jwtUtil.when(() -> JwtUtil.parseToken(anyString())).thenReturn(claims);
            when(valueOps.get(anyString())).thenReturn(Mono.just("1"));

            tokenBlacklistService
                    .isBlacklisted(TEST_TOKEN)
                    .as(StepVerifier::create)
                    .expectNext(true)
                    .verifyComplete();

            verify(valueOps).get(BLACKLIST_PREFIX + TEST_JTI);
        }
    }

    @Test
    @DisplayName("应该正确识别不在黑名单中的Token")
    void shouldCheckTokenNotInBlacklist() {
        try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
            Claims claims = mock(Claims.class);
            when(claims.getId()).thenReturn(TEST_JTI);
            jwtUtil.when(() -> JwtUtil.parseToken(anyString())).thenReturn(claims);
            when(valueOps.get(anyString())).thenReturn(Mono.empty());

            tokenBlacklistService
                    .isBlacklisted(TEST_TOKEN)
                    .as(StepVerifier::create)
                    .expectNext(false)
                    .verifyComplete();
        }
    }

    @Test
    @DisplayName("Token解析异常时应优雅降级")
    void shouldHandleRedisError() {
        try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
            jwtUtil.when(() -> JwtUtil.parseToken(anyString())).thenThrow(new RuntimeException("Token解析失败"));

            tokenBlacklistService
                    .add(TEST_TOKEN, 3600000)
                    .as(StepVerifier::create)
                    .verifyComplete();

            tokenBlacklistService
                    .isBlacklisted(TEST_TOKEN)
                    .as(StepVerifier::create)
                    .expectNext(false)
                    .verifyComplete();
        }
    }

    @Test
    @DisplayName("应该使用JWT的jti作为Redis黑名单键")
    void shouldCheckBlacklistForJwtToken() {
        String expectedJti = "jti-from-real-token";
        try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
            Claims claims = mock(Claims.class);
            when(claims.getId()).thenReturn(expectedJti);
            jwtUtil.when(() -> JwtUtil.parseToken(anyString())).thenReturn(claims);
            when(valueOps.get(anyString())).thenReturn(Mono.just("1"));

            tokenBlacklistService
                    .isBlacklisted("any-jwt-token")
                    .as(StepVerifier::create)
                    .expectNext(true)
                    .verifyComplete();

            verify(valueOps).get(BLACKLIST_PREFIX + expectedJti);
        }
    }

    @Test
    @DisplayName("过期Token应视为不在黑名单且不抛异常")
    void shouldTreatExpiredTokenAsNotBlacklisted() {
        try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
            jwtUtil.when(() -> JwtUtil.parseToken(anyString()))
                    .thenThrow(new io.jsonwebtoken.ExpiredJwtException(null, null, "JWT expired"));

            tokenBlacklistService
                    .isBlacklisted(TEST_TOKEN)
                    .as(StepVerifier::create)
                    .expectNext(false)
                    .verifyComplete();
        }
    }

    @Test
    @DisplayName("过期Token加入黑名单时应静默完成")
    void shouldHandleExpiredTokenWhenAdding() {
        try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
            jwtUtil.when(() -> JwtUtil.parseToken(anyString()))
                    .thenThrow(new io.jsonwebtoken.ExpiredJwtException(null, null, "JWT expired"));

            tokenBlacklistService
                    .add(TEST_TOKEN, 3600000)
                    .as(StepVerifier::create)
                    .verifyComplete();
        }
    }

    /**
     * 注销身份应写入带 TTL 的摘要吊销标记。
     *
     * <p>期望键只含身份指纹摘要、值为吊销时刻毫秒时间戳、
     * TTL 与传入一致。</p>
     */
    @Test
    @DisplayName("注销身份应写入带TTL的摘要吊销标记")
    void shouldRevokeSubjectWithHashedKeyAndTtl() {
        when(valueOps.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        tokenBlacklistService
                .revokeSubject("wx-subject", 60000L)
                .as(StepVerifier::create)
                .verifyComplete();

        verify(valueOps).set(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture());
        assertThat(keyCaptor.getValue())
                .isEqualTo(SUBJECT_REVOKED_PREFIX
                        + SensitiveDataUtil.fingerprint("wx-subject").substring("sha256:".length()));
        assertThat(Long.parseLong(valueCaptor.getValue())).isPositive();
        assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofMillis(60000L));
    }

    /**
     * 身份吊销标记只拒绝标记写入前签发的令牌。
     *
     * <p>签发时间早于吊销时刻的令牌判定为已吊销，晚于吊销时刻的
     * 令牌不受影响。</p>
     */
    @Test
    @DisplayName("身份吊销标记只拒绝标记写入前签发的令牌")
    void shouldCompareTokenIssuedAtWithSubjectRevocationTime() {
        long revokedAt = System.currentTimeMillis();
        String key = SUBJECT_REVOKED_PREFIX
                + SensitiveDataUtil.fingerprint("wx-subject").substring("sha256:".length());
        when(valueOps.get(key)).thenReturn(Mono.just(String.valueOf(revokedAt)));

        StepVerifier.create(tokenBlacklistService.isSubjectRevoked("wx-subject", new Date(revokedAt - 1)))
                .expectNext(true)
                .verifyComplete();
        StepVerifier.create(tokenBlacklistService.isSubjectRevoked("wx-subject", new Date(revokedAt + 1)))
                .expectNext(false)
                .verifyComplete();
    }
}
