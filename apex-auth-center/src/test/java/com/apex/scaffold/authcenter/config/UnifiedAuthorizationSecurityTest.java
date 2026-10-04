package com.apex.scaffold.authcenter.config;

import static org.mockito.Mockito.*;

import com.apex.scaffold.authcenter.application.service.RoleService;
import com.apex.scaffold.authcenter.application.service.UnifiedAuthorizationQueryService;
import com.apex.scaffold.authcenter.domain.model.entity.RoleEntity;
import com.apex.scaffold.authcenter.domain.model.entity.TokenEntity;
import com.apex.scaffold.authcenter.domain.service.TokenService;
import com.apex.scaffold.authcenter.interfaces.handler.UnifiedAuthorizationHandler;
import com.apex.scaffold.authcenter.interfaces.util.RequestHandlerUtil;
import com.apex.scaffold.common.config.JwtProperties;
import jakarta.validation.Validation;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

class UnifiedAuthorizationSecurityTest {
    private AnnotationConfigApplicationContext context;
    private WebTestClient client;
    private TokenService tokens;
    private UnifiedAuthorizationQueryService service;
    private RoleService roles;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TestConfiguration.class);
        tokens = context.getBean(TokenService.class);
        service = context.getBean(UnifiedAuthorizationQueryService.class);
        roles = context.getBean(RoleService.class);
        client = WebTestClient.bindToApplicationContext(context).build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void missingBearerIsUnauthorized() {
        client.get()
                .uri("/auth/v1/authorization/users/99")
                .exchange()
                .expectStatus()
                .isUnauthorized();
        verifyNoInteractions(service, tokens);
    }

    @Test
    void invalidBearerIsUnauthorized() {
        when(tokens.validateToken("invalid")).thenReturn(Mono.error(new IllegalStateException("Invalid")));
        request("invalid").expectStatus().isUnauthorized();
        verifyNoInteractions(service);
    }

    @Test
    void refreshTokenIsUnauthorized() {
        token("refresh", "REFRESH", 1L);
        request("refresh").expectStatus().isUnauthorized();
        verifyNoInteractions(service);
    }

    @Test
    void ordinaryAccessTokenIsForbidden() {
        token("ordinary", "ACCESS", 2L);
        request("ordinary").expectStatus().isForbidden();
        verifyNoInteractions(service);
    }

    @Test
    void readerUsesBearerTenantAcrossSecurityChain() {
        token("reader", "ACCESS", 1L);
        when(service.queryForReader("center", 1L, 99L))
                .thenReturn(Mono.just(new UnifiedAuthorizationQueryService.Authorization(
                        "center", 99L, List.of("user:read"), List.of())));
        request("reader")
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.tenantId")
                .isEqualTo("center")
                .jsonPath("$.data.userId")
                .isEqualTo(99);
        verify(service).queryForReader("center", 1L, 99L);
    }

    @Test
    void assignedRolesAreLoadedIntoAuthenticatedPrincipal() {
        token("assigned", "ACCESS", 3L);
        when(roles.getUserRoles(3L, "center"))
                .thenReturn(reactor.core.publisher.Flux.just(RoleEntity.builder()
                        .tenantId("center")
                        .enabled(true)
                        .code("ADMIN")
                        .build()));

        client.get()
                .uri("/auth/v1/security/authorities")
                .header("Authorization", "Bearer assigned")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .json("[\"ROLE_ADMIN\"]");
        verify(roles).getUserRoles(3L, "center");
    }

    @Test
    void missingTenantFailsClosed() {
        when(tokens.validateToken("missing-tenant"))
                .thenReturn(Mono.just(
                        TokenEntity.builder().userId(3L).tokenType("ACCESS").build()));

        client.get()
                .uri("/auth/v1/security/authorities")
                .header("Authorization", "Bearer missing-tenant")
                .exchange()
                .expectStatus()
                .isUnauthorized();
        verifyNoInteractions(roles);
    }

    @Test
    void blankTenantFailsClosed() {
        when(tokens.validateToken("blank-tenant"))
                .thenReturn(Mono.just(TokenEntity.builder()
                        .tenantId("   ")
                        .userId(3L)
                        .tokenType("ACCESS")
                        .build()));

        client.get()
                .uri("/auth/v1/security/authorities")
                .header("Authorization", "Bearer blank-tenant")
                .exchange()
                .expectStatus()
                .isUnauthorized();
        verifyNoInteractions(roles);
    }

    @Test
    void invalidOrForeignRolesAreNotGranted() {
        token("invalid-roles", "ACCESS", 3L);
        when(roles.getUserRoles(3L, "center"))
                .thenReturn(reactor.core.publisher.Flux.just(
                        RoleEntity.builder()
                                .tenantId("foreign")
                                .enabled(true)
                                .code("ADMIN")
                                .build(),
                        RoleEntity.builder()
                                .tenantId("center")
                                .enabled(false)
                                .code("ADMIN")
                                .build(),
                        RoleEntity.builder()
                                .tenantId("center")
                                .enabled(true)
                                .deleted(true)
                                .code("ADMIN")
                                .build()));

        client.get()
                .uri("/auth/v1/security/authorities")
                .header("Authorization", "Bearer invalid-roles")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .json("[]");
        verify(roles).getUserRoles(3L, "center");
    }

    @Test
    void roleLookupFailureFailsClosed() {
        token("role-error", "ACCESS", 3L);
        when(roles.getUserRoles(3L, "center"))
                .thenReturn(reactor.core.publisher.Flux.error(new IllegalStateException("unavailable")));

        client.get()
                .uri("/auth/v1/security/authorities")
                .header("Authorization", "Bearer role-error")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void rejectedReaderCannotReceiveSnapshot() {
        token("reader", "ACCESS", 1L);
        when(service.queryForReader("center", 1L, 99L))
                .thenReturn(Mono.error(new org.springframework.security.access.AccessDeniedException("Forbidden")));
        request("reader").expectStatus().isForbidden();
    }

    private WebTestClient.ResponseSpec request(String bearer) {
        return client.get()
                .uri("/auth/v1/authorization/users/99")
                .header("Authorization", "Bearer " + bearer)
                .header("X-Tenant-Id", "foreign")
                .exchange();
    }

    private void token(String value, String type, Long userId) {
        when(tokens.validateToken(value))
                .thenReturn(Mono.just(TokenEntity.builder()
                        .tenantId("center")
                        .userId(userId)
                        .tokenType(type)
                        .build()));
        when(roles.getUserRoles(userId, "center")).thenReturn(reactor.core.publisher.Flux.empty());
    }

    @Configuration
    @EnableWebFlux
    @EnableWebFluxSecurity
    static class TestConfiguration {
        @Bean
        TokenService tokens() {
            return mock(TokenService.class);
        }

        @Bean
        RoleService roles() {
            return mock(RoleService.class);
        }

        @Bean
        UnifiedAuthorizationQueryService service() {
            return mock(UnifiedAuthorizationQueryService.class);
        }

        @Bean
        SecurityWebFilterChain security(ServerHttpSecurity http, TokenService tokens, RoleService roles) {
            return new AuthCenterConfig()
                    .securityWebFilterChain(
                            http,
                            tokens,
                            roles,
                            new JwtProperties(),
                            new MockEnvironment().withProperty("auth-center.authorization-reader-user-id", "1"));
        }

        @Bean
        RouterFunction<ServerResponse> routes(UnifiedAuthorizationQueryService service) {
            UnifiedAuthorizationHandler handler = new UnifiedAuthorizationHandler(
                    service,
                    new MockEnvironment().withProperty("auth-center.authorization-reader-user-id", "1"),
                    new RequestHandlerUtil(
                            Validation.buildDefaultValidatorFactory().getValidator()));
            return RouterFunctions.route()
                    .GET("/auth/v1/authorization/users/{userId}", handler::query)
                    .GET("/auth/v1/security/authorities", request -> ReactiveSecurityContextHolder.getContext()
                            .map(context -> context.getAuthentication().getAuthorities().stream()
                                    .map(authority -> authority.getAuthority())
                                    .toList())
                            .flatMap(authorities -> ServerResponse.ok().bodyValue(authorities)))
                    .build();
        }
    }
}
