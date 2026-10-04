package com.apex.scaffold.authcenter.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.authcenter.application.assembler.AuditLogAssembler;
import com.apex.scaffold.authcenter.application.assembler.RoleAssembler;
import com.apex.scaffold.authcenter.application.assembler.SessionAssembler;
import com.apex.scaffold.authcenter.application.dto.AssignPermissionsRequest;
import com.apex.scaffold.authcenter.application.aspect.AdminRoleAspect;
import com.apex.scaffold.authcenter.application.aspect.ResourcePermissionAspect;
import com.apex.scaffold.authcenter.application.service.AuditLogService;
import com.apex.scaffold.authcenter.application.service.RoleService;
import com.apex.scaffold.authcenter.application.service.SessionService;
import com.apex.scaffold.authcenter.application.service.UnifiedAuthorizationQueryService;
import com.apex.scaffold.authcenter.domain.service.IpAccessControlService;
import com.apex.scaffold.authcenter.domain.model.MfaConfig;
import com.apex.scaffold.authcenter.domain.service.MfaService;
import com.apex.scaffold.authcenter.interfaces.handler.AuditLogHandler;
import com.apex.scaffold.authcenter.interfaces.handler.MfaManagementHandler;
import com.apex.scaffold.authcenter.interfaces.handler.RoleHandler;
import com.apex.scaffold.authcenter.interfaces.handler.SessionHandler;
import com.apex.scaffold.authcenter.domain.service.ResourcePermissionService;
import com.apex.scaffold.authcenter.domain.service.TokenService;
import com.apex.scaffold.common.annotation.RequireResourcePermission;
import com.apex.scaffold.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ResourcePermissionWiringTest {

    @Test
    void authCenterRegistersDomainResourcePermissionService() {
        new ApplicationContextRunner()
                .withUserConfiguration(AuthCenterConfig.class)
                .withBean(IpAccessControlService.class, () -> mock(IpAccessControlService.class))
                .withBean(TokenService.class, () -> mock(TokenService.class))
                .withBean(RoleService.class, () -> mock(RoleService.class))
                .withBean(UnifiedAuthorizationQueryService.class, () -> mock(UnifiedAuthorizationQueryService.class))
                .withBean(ServerHttpSecurity.class, ServerHttpSecurity::http)
                .withBean(ReactiveStringRedisTemplate.class, () -> mock(ReactiveStringRedisTemplate.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ResourcePermissionService.class);
                });
    }

    @Test
    void highRiskHandlersDeclareResourcePermissions() throws Exception {
        assertThat(AnnotationUtils.findAnnotation(
                        MfaManagementHandler.class.getDeclaredMethod("unlockMfa", org.springframework.web.reactive.function.server.ServerRequest.class),
                        RequireResourcePermission.class))
                .isNotNull();
        assertThat(AnnotationUtils.findAnnotation(
                        SessionHandler.class.getDeclaredMethod("terminateSession", org.springframework.web.reactive.function.server.ServerRequest.class),
                        RequireResourcePermission.class))
                .isNotNull();
        assertThat(AnnotationUtils.findAnnotation(
                        SessionHandler.class.getDeclaredMethod("terminateUserSessions", org.springframework.web.reactive.function.server.ServerRequest.class),
                        RequireResourcePermission.class))
                .isNotNull();
        assertThat(AnnotationUtils.findAnnotation(
                        AuditLogHandler.class.getDeclaredMethod("cleanupExpiredLogs", org.springframework.web.reactive.function.server.ServerRequest.class),
                        RequireResourcePermission.class))
                .isNotNull();
        assertThat(AnnotationUtils.findAnnotation(
                        RoleHandler.class.getDeclaredMethod("assignPermissions", AssignPermissionsRequest.class),
                        RequireResourcePermission.class))
                .isNotNull();
    }

    @Test
    void realHighRiskHandlerProxiesDenyResourceAccessBeforeBusinessServices() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(HighRiskHandlerConfiguration.class);
            context.refresh();

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            "user-1", "", java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
            ResourcePermissionService permissionService = context.getBean(ResourcePermissionService.class);
            when(permissionService.hasPermission(eq("user-1"), anyString(), anyString(), eq("MANAGE"), any()))
                    .thenReturn(Mono.just(false));

            ServerRequest mfaRequest = mock(ServerRequest.class);
            when(mfaRequest.queryParam("userId")).thenReturn(java.util.Optional.of("target-user"));
            when(mfaRequest.queryParam("type")).thenReturn(java.util.Optional.of("TOTP"));
            ServerRequest sessionRequest = mock(ServerRequest.class);
            when(sessionRequest.pathVariable("sessionId")).thenReturn("session-1");
            ServerRequest userSessionsRequest = mock(ServerRequest.class);
            when(userSessionsRequest.pathVariable("userId")).thenReturn("1001");
            ServerRequest auditRequest = mock(ServerRequest.class);
            when(auditRequest.queryParam("tenantId")).thenReturn(java.util.Optional.of("tenant-1"));
            when(auditRequest.queryParam("beforeTime")).thenReturn(java.util.Optional.of("2026-01-01T00:00:00"));
            AssignPermissionsRequest permissionsRequest = AssignPermissionsRequest.builder()
                    .roleId(42L)
                    .permissionIds(java.util.Set.of(7L))
                    .build();
            

            StepVerifier.create(context.getBean(MfaManagementHandler.class)
                            .unlockMfa(mfaRequest)
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)))
                    .expectErrorSatisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(403))
                    .verify();
            StepVerifier.create(context.getBean(SessionHandler.class)
                            .terminateSession(sessionRequest)
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)))
                    .expectErrorSatisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(403))
                    .verify();
            StepVerifier.create(context.getBean(SessionHandler.class)
                            .terminateUserSessions(userSessionsRequest)
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)))
                    .expectErrorSatisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(403))
                    .verify();
            StepVerifier.create(context.getBean(AuditLogHandler.class)
                            .cleanupExpiredLogs(auditRequest)
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)))
                    .expectErrorSatisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(403))
                    .verify();
            StepVerifier.create(context.getBean(RoleHandler.class)
                            .assignPermissions(permissionsRequest)
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)))
                    .expectErrorSatisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(403))
                    .verify();

            verify(context.getBean(MfaService.class), org.mockito.Mockito.never()).unlockMfa(any(), any(), any(), any());
            verify(context.getBean(SessionService.class), org.mockito.Mockito.never()).terminateSession(any());
            verify(context.getBean(SessionService.class), org.mockito.Mockito.never())
                    .terminateUserSessions(anyLong(), anyString());
            verify(context.getBean(AuditLogService.class), org.mockito.Mockito.never())
                    .cleanupExpiredLogs(any(), anyString());
            verify(context.getBean(RoleService.class), org.mockito.Mockito.never())
                    .assignPermissions(anyLong(), any());
        }
    }

    @Test
    void aspectRegisteredBeforeServiceConfigurationStillDeniesAccess() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(ResourcePermissionAspect.class, PermissionConfiguration.class);
            context.refresh();
            assertThat(context.getBeansOfType(ResourcePermissionAspect.class)).hasSize(1);
            ProtectedResource resource = context.getBean(ProtectedResource.class);
            assertThat(AopUtils.isAopProxy(resource)).isTrue();
            ResourcePermissionService service = context.getBean(ResourcePermissionService.class);
            when(service.hasPermission(eq("user-1"), eq("document"), eq("doc-1"), eq("READ"), any()))
                    .thenReturn(Mono.just(false));

            StepVerifier.create(resource.read("doc-1")
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                    new UsernamePasswordAuthenticationToken("user-1", "", java.util.List.of()))))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(BusinessException.class);
                        assertThat(((BusinessException) error).getCode()).isEqualTo(403);
                    })
                    .verify();
            assertThat(resource.invocations).isZero();
        }
    }

    @Test
    void highRiskHandlersReject401WhenUnauthenticated() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(HighRiskHandlerConfiguration.class);
            context.refresh();

            ServerRequest mfaRequest = mock(ServerRequest.class);
            when(mfaRequest.queryParam("userId")).thenReturn(java.util.Optional.of("target-user"));
            when(mfaRequest.queryParam("type")).thenReturn(java.util.Optional.of("TOTP"));
            when(mfaRequest.attribute("userId")).thenReturn(java.util.Optional.of("admin-1"));
            when(mfaRequest.attribute("tenantId")).thenReturn(java.util.Optional.of("tenant-1"));
            ServerRequest sessionRequest = mock(ServerRequest.class);
            when(sessionRequest.pathVariable("sessionId")).thenReturn("session-1");
            ServerRequest userSessionsRequest = mock(ServerRequest.class);
            when(userSessionsRequest.pathVariable("userId")).thenReturn("1001");
            ServerRequest auditRequest = mock(ServerRequest.class);
            when(auditRequest.queryParam("tenantId")).thenReturn(java.util.Optional.of("tenant-1"));
            when(auditRequest.queryParam("beforeTime")).thenReturn(java.util.Optional.of("2026-01-01T00:00:00"));
            AssignPermissionsRequest permissionsRequest = AssignPermissionsRequest.builder()
                    .roleId(42L)
                    .permissionIds(java.util.Set.of(7L))
                    .build();

            StepVerifier.create(context.getBean(MfaManagementHandler.class).unlockMfa(mfaRequest))
                    .expectErrorSatisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(401))
                    .verify();
            StepVerifier.create(context.getBean(SessionHandler.class).terminateSession(sessionRequest))
                    .expectErrorSatisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(401))
                    .verify();
            StepVerifier.create(context.getBean(SessionHandler.class).terminateUserSessions(userSessionsRequest))
                    .expectErrorSatisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(401))
                    .verify();
            StepVerifier.create(context.getBean(AuditLogHandler.class).cleanupExpiredLogs(auditRequest))
                    .expectErrorSatisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(401))
                    .verify();
            StepVerifier.create(context.getBean(RoleHandler.class).assignPermissions(permissionsRequest))
                    .expectErrorSatisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(401))
                    .verify();

            verify(context.getBean(MfaService.class), org.mockito.Mockito.never()).unlockMfa(any(), any(), any(), any());
            verify(context.getBean(SessionService.class), org.mockito.Mockito.never()).terminateSession(any());
            verify(context.getBean(SessionService.class), org.mockito.Mockito.never())
                    .terminateUserSessions(anyLong(), anyString());
            verify(context.getBean(AuditLogService.class), org.mockito.Mockito.never())
                    .cleanupExpiredLogs(any(), anyString());
            verify(context.getBean(RoleService.class), org.mockito.Mockito.never())
                    .assignPermissions(anyLong(), any());
        }
    }

    @Test
    void highRiskHandlersAllowAccessWhenRbacGrantsManagementPermission() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(HighRiskHandlerConfiguration.class);
            context.refresh();

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            "admin-1", "", java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
            authentication.setDetails("tenant-1");
            ResourcePermissionService permissionService = context.getBean(ResourcePermissionService.class);
            when(permissionService.hasPermission(eq("admin-1"), anyString(), anyString(), eq("MANAGE"), any()))
                    .thenReturn(Mono.just(true));

            ServerRequest mfaRequest = mock(ServerRequest.class);
            when(mfaRequest.queryParam("userId")).thenReturn(java.util.Optional.of("target-user"));
            when(mfaRequest.queryParam("type")).thenReturn(java.util.Optional.of("TOTP"));
            when(mfaRequest.attribute("userId")).thenReturn(java.util.Optional.of("admin-1"));
            when(mfaRequest.attribute("tenantId")).thenReturn(java.util.Optional.of("tenant-1"));
            ServerRequest sessionRequest = mock(ServerRequest.class);
            when(sessionRequest.pathVariable("sessionId")).thenReturn("session-1");
            ServerRequest userSessionsRequest = mock(ServerRequest.class);
            when(userSessionsRequest.pathVariable("userId")).thenReturn("1001");
            ServerRequest auditRequest = mock(ServerRequest.class);
            when(auditRequest.queryParam("tenantId")).thenReturn(java.util.Optional.of("tenant-1"));
            when(auditRequest.queryParam("beforeTime")).thenReturn(java.util.Optional.of("2026-01-01T00:00:00"));
            AssignPermissionsRequest permissionsRequest = AssignPermissionsRequest.builder()
                    .roleId(42L)
                    .permissionIds(java.util.Set.of(7L))
                    .build();

            MfaService mfaService = context.getBean(MfaService.class);
            when(mfaService.unlockMfa(anyString(), any(MfaConfig.MfaType.class), anyString(), anyString())).thenReturn(Mono.empty());
            SessionService sessionService = context.getBean(SessionService.class);
            when(sessionService.terminateSession(anyString())).thenReturn(Mono.empty());
            when(sessionService.terminateUserSessions(anyLong(), anyString())).thenReturn(Mono.empty());
            AuditLogService auditLogService = context.getBean(AuditLogService.class);
            when(auditLogService.cleanupExpiredLogs(any(), anyString())).thenReturn(Mono.just(0L));
            RoleService roleService = context.getBean(RoleService.class);
            when(roleService.assignPermissions(anyLong(), any())).thenReturn(Mono.empty());

            StepVerifier.create(context.getBean(MfaManagementHandler.class)
                            .unlockMfa(mfaRequest)
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)))
                    .expectNextCount(1)
                    .verifyComplete();
            StepVerifier.create(context.getBean(SessionHandler.class)
                            .terminateSession(sessionRequest)
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)))
                    .expectNextCount(1)
                    .verifyComplete();
            StepVerifier.create(context.getBean(SessionHandler.class)
                            .terminateUserSessions(userSessionsRequest)
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)))
                    .expectNextCount(1)
                    .verifyComplete();
            StepVerifier.create(context.getBean(AuditLogHandler.class)
                            .cleanupExpiredLogs(auditRequest)
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)))
                    .expectNextCount(1)
                    .verifyComplete();
            StepVerifier.create(context.getBean(RoleHandler.class)
                            .assignPermissions(permissionsRequest)
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)))
                    .expectNextCount(1)
                    .verifyComplete();

            verify(mfaService).unlockMfa(anyString(), any(MfaConfig.MfaType.class), anyString(), anyString());
            verify(sessionService).terminateSession(anyString());
            verify(sessionService).terminateUserSessions(anyLong(), anyString());
            verify(auditLogService).cleanupExpiredLogs(any(), anyString());
            verify(roleService).assignPermissions(anyLong(), any());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class HighRiskHandlerConfiguration {

        @Bean
        ResourcePermissionAspect resourcePermissionAspect(ResourcePermissionService service) {
            return new ResourcePermissionAspect(service);
        }

        @Bean
        AdminRoleAspect adminRoleAspect() {
            return new AdminRoleAspect();
        }

        @Bean
        ResourcePermissionService resourcePermissionService() {
            return mock(ResourcePermissionService.class);
        }

        @Bean
        MfaService mfaService() {
            return mock(MfaService.class);
        }

        @Bean
        SessionService sessionService() {
            return mock(SessionService.class);
        }

        @Bean
        SessionAssembler sessionAssembler() {
            return mock(SessionAssembler.class);
        }

        @Bean
        AuditLogService auditLogService() {
            return mock(AuditLogService.class);
        }

        @Bean
        AuditLogAssembler auditLogAssembler() {
            return mock(AuditLogAssembler.class);
        }

        @Bean
        RoleService roleService() {
            return mock(RoleService.class);
        }

        @Bean
        RoleAssembler roleAssembler() {
            return mock(RoleAssembler.class);
        }

        @Bean
        MfaManagementHandler mfaManagementHandler(MfaService service) {
            return new MfaManagementHandler(service);
        }

        @Bean
        SessionHandler sessionHandler(SessionService service, SessionAssembler assembler) {
            return new SessionHandler(service, assembler);
        }

        @Bean
        AuditLogHandler auditLogHandler(AuditLogService service, AuditLogAssembler assembler) {
            return new AuditLogHandler(service, assembler);
        }

        @Bean
        RoleHandler roleHandler(RoleService service, RoleAssembler assembler) {
            return new RoleHandler(service, assembler);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class PermissionConfiguration {

        @Bean
        ResourcePermissionService domainResourcePermissionService() {
            return mock(ResourcePermissionService.class);
        }

        @Bean
        ProtectedResource protectedResource() {
            return new ProtectedResource();
        }
    }

    static class ProtectedResource {
        private int invocations;

        @RequireResourcePermission(resourceType = "document", permission = "READ", resourceIdExpression = "#resourceId")
        public Mono<String> read(String resourceId) {
            invocations++;
            return Mono.just("allowed");
        }
    }
}
