package com.apex.scaffold.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.auth.domain.model.entity.SessionEntity;
import com.apex.scaffold.auth.domain.repository.SessionRepository;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 会话服务单元测试
 *
 * @author Apex Scaffold Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("会话服务单元测试")
class SessionServiceTest {

    @Mock
    private SessionRepository sessionRepository;

    @InjectMocks
    private SessionService sessionService;

    private SessionEntity mockSession;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();
        mockSession = SessionEntity.builder()
                .id(1L)
                .sessionId("session-uuid-001")
                .userId(100L)
                .tenantId("test-tenant")
                .tokenId(1001L)
                .deviceType("WEB")
                .deviceId("device-001")
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .status("ACTIVE")
                .createdAt(now)
                .lastActiveAt(now)
                .expiresAt(now.plusHours(24))
                .deleted(false)
                .build();
    }

    @Nested
    @DisplayName("createSession() 方法测试")
    class CreateSessionTest {

        @Test
        @DisplayName("正常创建会话成功")
        void testCreateSessionSuccess() {
            // 准备测试数据
            when(sessionRepository.findActiveByUserId(100L, "test-tenant")).thenReturn(Flux.empty());
            when(sessionRepository.save(any(SessionEntity.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

            // 执行测试
            Mono<SessionEntity> result = sessionService.createSession(
                    100L, "test-tenant", 1001L, "WEB", "device-001", "192.168.1.1", "Mozilla/5.0");

            // 验证结果
            StepVerifier.create(result)
                    .assertNext(session -> {
                        assertThat(session.getUserId()).isEqualTo(100L);
                        assertThat(session.getTenantId()).isEqualTo("test-tenant");
                        assertThat(session.getTokenId()).isEqualTo(1001L);
                        assertThat(session.getDeviceType()).isEqualTo("WEB");
                        assertThat(session.getDeviceId()).isEqualTo("device-001");
                        assertThat(session.getIpAddress()).isEqualTo("192.168.1.1");
                        assertThat(session.getStatus()).isEqualTo("ACTIVE");
                        assertThat(session.getSessionId()).isNotNull();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("超过最大并发会话数时创建失败")
        void testCreateSessionExceedsMaxConcurrent() {
            // 模拟已有5个活跃会话
            when(sessionRepository.findActiveByUserId(100L, "test-tenant"))
                    .thenReturn(Flux.range(1, 5).map(i -> mockSession));

            // 执行测试
            Mono<SessionEntity> result = sessionService.createSession(
                    100L, "test-tenant", 1001L, "WEB", "device-001", "192.168.1.1", "Mozilla/5.0");

            // 验证结果
            StepVerifier.create(result)
                    .expectErrorMatches(ex -> ex instanceof BusinessException
                            && ((BusinessException) ex).getCode().equals(ResultCode.FORBIDDEN.getCode())
                            && ex.getMessage().contains("超过最大并发会话数限制"))
                    .verify();
        }
    }

    @Nested
    @DisplayName("getSessionBySessionId() 方法测试")
    class GetSessionBySessionIdTest {

        @Test
        @DisplayName("根据sessionId查询成功")
        void testGetSessionBySessionIdSuccess() {
            when(sessionRepository.findBySessionId("session-uuid-001")).thenReturn(Mono.just(mockSession));

            Mono<SessionEntity> result = sessionService.getSessionBySessionId("session-uuid-001");

            StepVerifier.create(result)
                    .assertNext(session -> {
                        assertThat(session.getSessionId()).isEqualTo("session-uuid-001");
                        assertThat(session.getUserId()).isEqualTo(100L);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("会话不存在时抛出异常")
        void testGetSessionBySessionIdNotFound() {
            when(sessionRepository.findBySessionId("non-existent")).thenReturn(Mono.empty());

            Mono<SessionEntity> result = sessionService.getSessionBySessionId("non-existent");

            StepVerifier.create(result)
                    .expectErrorMatches(ex -> ex instanceof BusinessException
                            && ((BusinessException) ex).getCode().equals(ResultCode.NOT_FOUND.getCode())
                            && ex.getMessage().contains("会话不存在"))
                    .verify();
        }
    }

    @Nested
    @DisplayName("getSessionByTokenId() 方法测试")
    class GetSessionByTokenIdTest {

        @Test
        @DisplayName("根据tokenId查询成功")
        void testGetSessionByTokenIdSuccess() {
            when(sessionRepository.findByTokenId(1001L)).thenReturn(Mono.just(mockSession));

            Mono<SessionEntity> result = sessionService.getSessionByTokenId(1001L);

            StepVerifier.create(result)
                    .assertNext(session -> {
                        assertThat(session.getTokenId()).isEqualTo(1001L);
                        assertThat(session.getSessionId()).isEqualTo("session-uuid-001");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("会话不存在时抛出异常")
        void testGetSessionByTokenIdNotFound() {
            when(sessionRepository.findByTokenId(9999L)).thenReturn(Mono.empty());

            Mono<SessionEntity> result = sessionService.getSessionByTokenId(9999L);

            StepVerifier.create(result)
                    .expectErrorMatches(ex -> ex instanceof BusinessException
                            && ((BusinessException) ex).getCode().equals(ResultCode.NOT_FOUND.getCode()))
                    .verify();
        }
    }

    @Nested
    @DisplayName("getUserSessions() 方法测试")
    class GetUserSessionsTest {

        @Test
        @DisplayName("获取用户所有会话成功")
        void testGetUserSessionsSuccess() {
            SessionEntity session2 = SessionEntity.builder()
                    .id(2L)
                    .sessionId("session-uuid-002")
                    .userId(100L)
                    .tenantId("test-tenant")
                    .status("TERMINATED")
                    .build();

            when(sessionRepository.findByUserId(100L, "test-tenant")).thenReturn(Flux.just(mockSession, session2));

            Flux<SessionEntity> result = sessionService.getUserSessions(100L, "test-tenant");

            StepVerifier.create(result)
                    .expectNext(mockSession)
                    .expectNext(session2)
                    .verifyComplete();
        }

        @Test
        @DisplayName("用户无会话时返回空流")
        void testGetUserSessionsEmpty() {
            when(sessionRepository.findByUserId(100L, "test-tenant")).thenReturn(Flux.empty());

            Flux<SessionEntity> result = sessionService.getUserSessions(100L, "test-tenant");

            StepVerifier.create(result).verifyComplete();
        }
    }

    @Nested
    @DisplayName("getActiveSessions() 方法测试")
    class GetActiveSessionsTest {

        @Test
        @DisplayName("获取用户活跃会话成功")
        void testGetActiveSessionsSuccess() {
            when(sessionRepository.findActiveByUserId(100L, "test-tenant")).thenReturn(Flux.just(mockSession));

            Flux<SessionEntity> result = sessionService.getActiveSessions(100L, "test-tenant");

            StepVerifier.create(result).expectNext(mockSession).verifyComplete();
        }
    }

    @Nested
    @DisplayName("updateSessionActivity() 方法测试")
    class UpdateSessionActivityTest {

        @Test
        @DisplayName("更新会话活跃时间成功")
        void testUpdateSessionActivitySuccess() {
            when(sessionRepository.updateLastActive("session-uuid-001")).thenReturn(Mono.just(true));

            Mono<Void> result = sessionService.updateSessionActivity("session-uuid-001");

            StepVerifier.create(result).verifyComplete();
        }

        @Test
        @DisplayName("更新失败时抛出异常")
        void testUpdateSessionActivityFailure() {
            when(sessionRepository.updateLastActive("session-uuid-001")).thenReturn(Mono.just(false));

            Mono<Void> result = sessionService.updateSessionActivity("session-uuid-001");

            StepVerifier.create(result)
                    .expectErrorMatches(ex -> ex instanceof BusinessException
                            && ((BusinessException) ex).getCode().equals(ResultCode.ERROR.getCode())
                            && ex.getMessage().contains("会话活跃时间更新失败"))
                    .verify();
        }
    }

    @Nested
    @DisplayName("terminateSession() 方法测试")
    class TerminateSessionTest {

        @Test
        @DisplayName("终止会话成功")
        void testTerminateSessionSuccess() {
            when(sessionRepository.terminateBySessionId("session-uuid-001")).thenReturn(Mono.just(true));

            Mono<Void> result = sessionService.terminateSession("session-uuid-001");

            StepVerifier.create(result).verifyComplete();
        }

        @Test
        @DisplayName("终止失败时抛出异常")
        void testTerminateSessionFailure() {
            when(sessionRepository.terminateBySessionId("session-uuid-001")).thenReturn(Mono.just(false));

            Mono<Void> result = sessionService.terminateSession("session-uuid-001");

            StepVerifier.create(result)
                    .expectErrorMatches(ex ->
                            ex instanceof BusinessException && ex.getMessage().contains("会话终止失败"))
                    .verify();
        }
    }

    @Nested
    @DisplayName("terminateUserSessions() 方法测试")
    class TerminateUserSessionsTest {

        @Test
        @DisplayName("终止用户所有会话成功")
        void testTerminateUserSessionsSuccess() {
            when(sessionRepository.terminateByUserId(100L, "test-tenant")).thenReturn(Mono.just(true));

            Mono<Void> result = sessionService.terminateUserSessions(100L, "test-tenant");

            StepVerifier.create(result).verifyComplete();
        }

        @Test
        @DisplayName("终止失败时抛出异常")
        void testTerminateUserSessionsFailure() {
            when(sessionRepository.terminateByUserId(100L, "test-tenant")).thenReturn(Mono.just(false));

            Mono<Void> result = sessionService.terminateUserSessions(100L, "test-tenant");

            StepVerifier.create(result)
                    .expectErrorMatches(ex ->
                            ex instanceof BusinessException && ex.getMessage().contains("用户会话终止失败"))
                    .verify();
        }
    }

    @Nested
    @DisplayName("kickOutSession() 方法测试")
    class KickOutSessionTest {

        @Test
        @DisplayName("踢出会话成功")
        void testKickOutSessionSuccess() {
            when(sessionRepository.findBySessionId("session-uuid-001")).thenReturn(Mono.just(mockSession));
            when(sessionRepository.update(any(SessionEntity.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

            Mono<Void> result = sessionService.kickOutSession("session-uuid-001");

            StepVerifier.create(result).verifyComplete();
        }

        @Test
        @DisplayName("会话不存在时抛出异常")
        void testKickOutSessionNotFound() {
            when(sessionRepository.findBySessionId("non-existent")).thenReturn(Mono.empty());

            Mono<Void> result = sessionService.kickOutSession("non-existent");

            StepVerifier.create(result)
                    .expectErrorMatches(ex -> ex instanceof BusinessException
                            && ((BusinessException) ex).getCode().equals(ResultCode.NOT_FOUND.getCode()))
                    .verify();
        }
    }

    @Nested
    @DisplayName("cleanupExpiredSessions() 方法测试")
    class CleanupExpiredSessionsTest {

        @Test
        @DisplayName("清理过期会话成功")
        void testCleanupExpiredSessionsSuccess() {
            when(sessionRepository.cleanupExpiredSessions()).thenReturn(Mono.just(10L));

            Mono<Long> result = sessionService.cleanupExpiredSessions();

            StepVerifier.create(result).expectNext(10L).verifyComplete();
        }

        @Test
        @DisplayName("无过期会话时返回0")
        void testCleanupExpiredSessionsNone() {
            when(sessionRepository.cleanupExpiredSessions()).thenReturn(Mono.just(0L));

            Mono<Long> result = sessionService.cleanupExpiredSessions();

            StepVerifier.create(result).expectNext(0L).verifyComplete();
        }
    }

    @Nested
    @DisplayName("validateSession() 方法测试")
    class ValidateSessionTest {

        @Test
        @DisplayName("验证活跃会话返回true")
        void testValidateSessionActive() {
            when(sessionRepository.findBySessionId("session-uuid-001")).thenReturn(Mono.just(mockSession));

            Mono<Boolean> result = sessionService.validateSession("session-uuid-001");

            StepVerifier.create(result).expectNext(true).verifyComplete();
        }

        @Test
        @DisplayName("验证已过期会话返回false")
        void testValidateSessionExpired() {
            SessionEntity expiredSession = SessionEntity.builder()
                    .sessionId("session-uuid-001")
                    .status("ACTIVE")
                    .expiresAt(LocalDateTime.now().minusHours(1))
                    .deleted(false)
                    .build();

            when(sessionRepository.findBySessionId("session-uuid-001")).thenReturn(Mono.just(expiredSession));

            Mono<Boolean> result = sessionService.validateSession("session-uuid-001");

            StepVerifier.create(result).expectNext(false).verifyComplete();
        }

        @Test
        @DisplayName("验证不存在的会话返回false")
        void testValidateSessionNotFound() {
            when(sessionRepository.findBySessionId("non-existent")).thenReturn(Mono.empty());

            Mono<Boolean> result = sessionService.validateSession("non-existent");

            StepVerifier.create(result).expectNext(false).verifyComplete();
        }
    }

    @Nested
    @DisplayName("getOnlineUsers() 方法测试")
    class GetOnlineUsersTest {

        @Test
        @DisplayName("获取所有在线用户成功")
        void testGetOnlineUsersSuccess() {
            SessionEntity session2 = SessionEntity.builder()
                    .id(2L)
                    .sessionId("session-uuid-002")
                    .userId(200L)
                    .status("ACTIVE")
                    .expiresAt(LocalDateTime.now().plusHours(24))
                    .deleted(false)
                    .build();

            when(sessionRepository.findAll()).thenReturn(Flux.just(mockSession, session2));

            Flux<SessionEntity> result = sessionService.getOnlineUsers();

            StepVerifier.create(result)
                    .expectNext(mockSession)
                    .expectNext(session2)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("countOnlineUsers() 方法测试")
    class CountOnlineUsersTest {

        @Test
        @DisplayName("统计在线用户数成功")
        void testCountOnlineUsersSuccess() {
            SessionEntity session2 = SessionEntity.builder()
                    .id(2L)
                    .userId(200L)
                    .status("ACTIVE")
                    .expiresAt(LocalDateTime.now().plusHours(24))
                    .deleted(false)
                    .build();

            SessionEntity session3 = SessionEntity.builder()
                    .id(3L)
                    .userId(100L) // 同一用户的另一个会话
                    .status("ACTIVE")
                    .expiresAt(LocalDateTime.now().plusHours(24))
                    .deleted(false)
                    .build();

            when(sessionRepository.findAll()).thenReturn(Flux.just(mockSession, session2, session3));

            Mono<Long> result = sessionService.countOnlineUsers();

            StepVerifier.create(result)
                    .expectNext(2L) // 两个不同的用户
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("getSessionById() 方法测试")
    class GetSessionByIdTest {

        @Test
        @DisplayName("根据ID查询会话成功")
        void testGetSessionByIdSuccess() {
            when(sessionRepository.findById(1L)).thenReturn(Mono.just(mockSession));

            Mono<SessionEntity> result = sessionService.getSessionById(1L);

            StepVerifier.create(result)
                    .assertNext(session -> {
                        assertThat(session.getId()).isEqualTo(1L);
                        assertThat(session.getSessionId()).isEqualTo("session-uuid-001");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("会话不存在时抛出异常")
        void testGetSessionByIdNotFound() {
            when(sessionRepository.findById(999L)).thenReturn(Mono.empty());

            Mono<SessionEntity> result = sessionService.getSessionById(999L);

            StepVerifier.create(result)
                    .expectErrorMatches(ex -> ex instanceof BusinessException
                            && ((BusinessException) ex).getCode().equals(ResultCode.NOT_FOUND.getCode()))
                    .verify();
        }
    }

    @Nested
    @DisplayName("getOnlineUserCount() 方法测试")
    class GetOnlineUserCountTest {

        @Test
        @DisplayName("获取租户在线用户数成功")
        void testGetOnlineUserCountSuccess() {
            SessionEntity session2 = SessionEntity.builder()
                    .userId(200L)
                    .tenantId("test-tenant")
                    .status("ACTIVE")
                    .expiresAt(LocalDateTime.now().plusHours(24))
                    .deleted(false)
                    .build();

            when(sessionRepository.findByUserId(null, "test-tenant")).thenReturn(Flux.just(mockSession, session2));

            Mono<Long> result = sessionService.getOnlineUserCount("test-tenant");

            StepVerifier.create(result).expectNext(2L).verifyComplete();
        }

        @Test
        @DisplayName("无在线用户时返回0")
        void testGetOnlineUserCountZero() {
            when(sessionRepository.findByUserId(null, "test-tenant")).thenReturn(Flux.empty());

            Mono<Long> result = sessionService.getOnlineUserCount("test-tenant");

            StepVerifier.create(result).expectNext(0L).verifyComplete();
        }
    }

    @Test
    void fourthExistingSessionStillAllowsCreation() {
        when(sessionRepository.findActiveByUserId(100L, "test-tenant"))
                .thenReturn(Flux.range(1, 4).map(index -> mockSession));
        when(sessionRepository.save(any(SessionEntity.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(sessionService.createSession(
                        100L, "test-tenant", 1001L, "WEB", "device-001", "127.0.0.1", "agent"))
                .assertNext(session -> {
                    assertThat(session.getUserId()).isEqualTo(100L);
                    assertThat(session.getTenantId()).isEqualTo("test-tenant");
                    assertThat(session.getStatus()).isEqualTo("ACTIVE");
                })
                .verifyComplete();
        verify(sessionRepository).save(any(SessionEntity.class));
    }

    @Test
    void concurrentSessionQueryFailurePreventsSave() {
        IllegalStateException failure = new IllegalStateException("query failed");
        when(sessionRepository.findActiveByUserId(100L, "test-tenant")).thenReturn(Flux.error(failure));

        StepVerifier.create(sessionService.createSession(
                        100L, "test-tenant", 1001L, "WEB", "device-001", "127.0.0.1", "agent"))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
                .verify();
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void sessionLookupFailureIsNotReportedAsInvalidSession() {
        IllegalStateException failure = new IllegalStateException("lookup failed");
        when(sessionRepository.findBySessionId("session-uuid-001")).thenReturn(Mono.error(failure));

        StepVerifier.create(sessionService.validateSession("session-uuid-001"))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
                .verify();
    }

    @Test
    void kickOutUpdateFailureIsPropagated() {
        IllegalStateException failure = new IllegalStateException("update failed");
        when(sessionRepository.findBySessionId("session-uuid-001")).thenReturn(Mono.just(mockSession));
        when(sessionRepository.update(mockSession)).thenReturn(Mono.error(failure));

        StepVerifier.create(sessionService.kickOutSession("session-uuid-001"))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
                .verify();
        verify(sessionRepository).update(mockSession);
    }
}
