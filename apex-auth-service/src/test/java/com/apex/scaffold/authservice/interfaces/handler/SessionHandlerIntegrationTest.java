package com.apex.scaffold.authservice.interfaces.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.apex.scaffold.auth.application.assembler.SessionAssembler;
import com.apex.scaffold.auth.application.dto.OnlineUserDTO;
import com.apex.scaffold.auth.application.dto.SessionDTO;
import com.apex.scaffold.auth.application.service.SessionService;
import com.apex.scaffold.auth.domain.model.entity.SessionEntity;
import com.apex.scaffold.authservice.AuthCenterSliceTestConfiguration;
import com.apex.scaffold.authservice.interfaces.router.SessionRouter;
import com.apex.scaffold.common.result.ResultCode;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 会话Handler集成测试
 *
 * <p>测试会话管理相关的HTTP接口，验证：
 * <ul>
 *   <li>查询用户会话：根据用户ID查询会话列表</li>
 *   <li>终止会话：强制终止指定会话</li>
 *   <li>批量下线：终止用户所有会话</li>
 *   <li>在线用户：查询在线用户列表</li>
 *   <li>在线统计：查询在线用户数量</li>
 *   <li>会话详情：根据会话ID查询详细信息</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2024-01-01
 */
@WebFluxTest(controllers = SessionHandler.class)
@Import({SessionHandler.class, SessionRouter.class, AuthCenterSliceTestConfiguration.class})
@DisplayName("会话Handler集成测试")
@WithMockUser
class SessionHandlerIntegrationTest {

    /**
     * {@code WebTestClient} 在 {@code @WebFluxTest} 中无法通过构造器解析，
     * 故采用 JSR-250 {@code @Resource} 字段注入。
     */
    @Resource
    private WebTestClient webTestClient;

    @MockBean
    private SessionService sessionService;

    @MockBean
    private SessionAssembler sessionAssembler;

    private SessionEntity testSessionEntity;
    private SessionDTO testSessionDto;
    private OnlineUserDTO testOnlineUserDto;

    @BeforeEach
    void setUp() {
        testSessionEntity = SessionEntity.builder()
                .id(1L)
                .tenantId("default")
                .userId(100L)
                .sessionId("session-123")
                .deviceId("device-456")
                .deviceType("PC")
                .ipAddress("192.168.1.100")
                .userAgent("Mozilla/5.0")
                .lastActiveAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(2))
                .createdAt(LocalDateTime.now())
                .build();

        testSessionDto = SessionDTO.builder()
                .id(1L)
                .tenantId("default")
                .userId(100L)
                .sessionId("session-123")
                .deviceId("device-456")
                .deviceType("PC")
                .clientIp("192.168.1.100")
                .userAgent("Mozilla/5.0")
                .lastActiveAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(2))
                .createdAt(LocalDateTime.now())
                .build();

        testOnlineUserDto = OnlineUserDTO.builder()
                .userId(100L)
                .username("testuser")
                .lastActiveAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("查询用户会话列表 - 成功")
    void testGetUserSessions_Success() {
        when(sessionService.getUserSessions(anyLong(), anyString())).thenReturn(Flux.just(testSessionEntity));
        when(sessionAssembler.toDto(any(SessionEntity.class))).thenReturn(testSessionDto);

        webTestClient
                .get()
                .uri("/auth/v1/sessions/user/100")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data")
                .isArray()
                .jsonPath("$.data[0].userId")
                .isEqualTo(100)
                .jsonPath("$.data[0].sessionId")
                .isEqualTo("session-123");
    }

    @Test
    @DisplayName("终止指定会话 - 成功")
    void testTerminateSession_Success() {
        when(sessionService.terminateSession(anyString())).thenReturn(Mono.empty());

        webTestClient
                .mutateWith(SecurityMockServerConfigurers.csrf())
                .delete()
                .uri("/auth/v1/sessions/1")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("批量终止用户所有会话 - 成功")
    void testTerminateUserSessions_Success() {
        when(sessionService.terminateUserSessions(anyLong(), anyString())).thenReturn(Mono.empty());

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken("admin", "", java.util.List.of());
        authentication.setDetails("default");

        webTestClient
                .mutateWith(SecurityMockServerConfigurers.csrf())
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(authentication))
                .delete()
                .uri("/auth/v1/sessions/user/100")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("获取在线用户列表 - 成功")
    void testGetOnlineUsers_Success() {
        when(sessionService.getOnlineUsers()).thenReturn(Flux.just(testSessionEntity));
        when(sessionAssembler.toDto(any(SessionEntity.class))).thenReturn(testSessionDto);

        webTestClient
                .get()
                .uri("/auth/v1/sessions/online")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data")
                .isArray()
                .jsonPath("$.data[0].userId")
                .isEqualTo(100)
                .jsonPath("$.data[0].sessionId")
                .isEqualTo("session-123");
    }

    @Test
    @DisplayName("获取在线用户统计 - 成功")
    void testGetOnlineStatistics_Success() {
        when(sessionService.countOnlineUsers()).thenReturn(Mono.just(50L));

        webTestClient
                .get()
                .uri("/auth/v1/sessions/statistics")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.totalOnlineUsers")
                .isEqualTo(50)
                .jsonPath("$.data.timestamp")
                .exists();
    }

    @Test
    @DisplayName("根据会话ID查询详情 - 成功")
    void testGetSessionById_Success() {
        when(sessionService.getSessionById(anyLong())).thenReturn(Mono.just(testSessionEntity));
        when(sessionAssembler.toDto(any(SessionEntity.class))).thenReturn(testSessionDto);

        webTestClient
                .get()
                .uri("/auth/v1/sessions/1")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.id")
                .isEqualTo(1)
                .jsonPath("$.data.sessionId")
                .isEqualTo("session-123");
    }

    @Test
    @DisplayName("查询不存在的会话 - 返回空")
    void testGetSessionById_NotFound() {
        when(sessionService.getSessionById(anyLong())).thenReturn(Mono.empty());

        webTestClient
                .get()
                .uri("/auth/v1/sessions/999")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data")
                .doesNotExist();
    }

    @Test
    @DisplayName("查询无会话用户 - 返回空列表")
    void testGetUserSessions_Empty() {
        when(sessionService.getUserSessions(anyLong(), anyString())).thenReturn(Flux.empty());

        webTestClient
                .get()
                .uri("/auth/v1/sessions/user/999")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data")
                .isArray()
                .jsonPath("$.data.length()")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("在线用户为空 - 返回空列表")
    void testGetOnlineUsers_Empty() {
        when(sessionService.getOnlineUsers()).thenReturn(Flux.empty());

        webTestClient
                .get()
                .uri("/auth/v1/sessions/online")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data")
                .isArray()
                .jsonPath("$.data.length()")
                .isEqualTo(0);
    }

    @Test
    void testGetUserSessions_SuccessServiceError() {
        when(sessionService.getUserSessions(anyLong(), anyString()))
                .thenReturn(Flux.error(new IllegalStateException("service unavailable")));
        when(sessionAssembler.toDto(any(SessionEntity.class))).thenReturn(testSessionDto);

        webTestClient
                .get()
                .uri("/auth/v1/sessions/user/100")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.ERROR.getCode())
                .jsonPath("$.msg")
                .isNotEmpty()
                .jsonPath("$.data")
                .doesNotExist();
    }

    @Test
    void testTerminateSession_SuccessServiceError() {
        when(sessionService.terminateSession(anyString()))
                .thenReturn(reactor.core.publisher.Mono.error(new IllegalStateException("service unavailable")));

        webTestClient
                .mutateWith(SecurityMockServerConfigurers.csrf())
                .delete()
                .uri("/auth/v1/sessions/1")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.ERROR.getCode())
                .jsonPath("$.msg")
                .isNotEmpty()
                .jsonPath("$.data")
                .doesNotExist();
    }

    @Test
    void testTerminateUserSessions_SuccessServiceError() {
        when(sessionService.terminateUserSessions(anyLong(), anyString()))
                .thenReturn(reactor.core.publisher.Mono.error(new IllegalStateException("service unavailable")));

        webTestClient
                .mutateWith(SecurityMockServerConfigurers.csrf())
                .delete()
                .uri("/auth/v1/sessions/user/100")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.ERROR.getCode())
                .jsonPath("$.msg")
                .isNotEmpty()
                .jsonPath("$.data")
                .doesNotExist();
    }

    @Test
    void testGetOnlineUsers_SuccessServiceError() {
        when(sessionService.getOnlineUsers()).thenReturn(Flux.error(new IllegalStateException("service unavailable")));
        when(sessionAssembler.toDto(any(SessionEntity.class))).thenReturn(testSessionDto);

        webTestClient
                .get()
                .uri("/auth/v1/sessions/online")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.ERROR.getCode())
                .jsonPath("$.msg")
                .isNotEmpty()
                .jsonPath("$.data")
                .doesNotExist();
    }

    @Test
    void testGetOnlineStatistics_SuccessServiceError() {
        when(sessionService.countOnlineUsers())
                .thenReturn(reactor.core.publisher.Mono.error(new IllegalStateException("service unavailable")));

        webTestClient
                .get()
                .uri("/auth/v1/sessions/statistics")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.ERROR.getCode())
                .jsonPath("$.msg")
                .isNotEmpty()
                .jsonPath("$.data")
                .doesNotExist();
    }

    @Test
    void testGetSessionById_SuccessServiceError() {
        when(sessionService.getSessionById(anyLong()))
                .thenReturn(reactor.core.publisher.Mono.error(new IllegalStateException("service unavailable")));
        when(sessionAssembler.toDto(any(SessionEntity.class))).thenReturn(testSessionDto);

        webTestClient
                .get()
                .uri("/auth/v1/sessions/1")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.ERROR.getCode())
                .jsonPath("$.msg")
                .isNotEmpty()
                .jsonPath("$.data")
                .doesNotExist();
    }

    @Test
    void testUserSessionsTenantParameter() {
        when(sessionService.getUserSessions(100L, "tenant-b")).thenReturn(Flux.empty());
        webTestClient
                .get()
                .uri("/auth/v1/sessions/user/100?tenantId=tenant-b")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data")
                .isEmpty();
        org.mockito.Mockito.verify(sessionService).getUserSessions(100L, "tenant-b");
    }

    @Test
    void testTerminateUsesSessionIdentifier() {
        when(sessionService.terminateSession("session-123")).thenReturn(Mono.empty());
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.csrf())
                .delete()
                .uri("/auth/v1/sessions/session-123")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0);
        org.mockito.Mockito.verify(sessionService).terminateSession("session-123");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "/auth/v1/sessions/user/100,default",
        "/auth/v1/sessions/user/100?tenantId=tenant-b,tenant-b"
    })
    void testTerminateUserSessionsPassesTenant(String uri, String tenantId) {
        when(sessionService.terminateUserSessions(100L, tenantId)).thenReturn(Mono.empty());
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken("admin", "", java.util.List.of());
        authentication.setDetails(tenantId);

        webTestClient
                .mutateWith(SecurityMockServerConfigurers.csrf())
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(authentication))
                .delete()
                .uri(uri)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data")
                .doesNotExist();
        org.mockito.Mockito.verify(sessionService).terminateUserSessions(100L, tenantId);
    }

    @Test
    void testUserSessionsDefaultTenant() {
        when(sessionService.getUserSessions(100L, "default")).thenReturn(Flux.empty());
        webTestClient
                .get()
                .uri("/auth/v1/sessions/user/100")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data")
                .isEmpty();
        org.mockito.Mockito.verify(sessionService).getUserSessions(100L, "default");
    }

    @Test
    void testOnlineStatisticsZeroUsers() {
        when(sessionService.countOnlineUsers()).thenReturn(Mono.just(0L));
        webTestClient
                .get()
                .uri("/auth/v1/sessions/statistics")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.totalOnlineUsers")
                .isEqualTo(0)
                .jsonPath("$.data.timestamp")
                .isNotEmpty();
    }
}
