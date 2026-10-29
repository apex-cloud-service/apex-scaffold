package com.apex.scaffold.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.domain.gateway.UnifiedAuthorizationGateway;
import com.apex.scaffold.domain.repository.PermissionRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class UnifiedAuthorizationServiceTest {
    @Test
    void trustedTenantIsRequired() {
        UnifiedAuthorizationGateway gateway = mock(UnifiedAuthorizationGateway.class);
        var service = new UnifiedAuthorizationService(gateway, new MockEnvironment(), null);
        StepVerifier.create(service.permissions(7L))
                .expectError(IllegalStateException.class)
                .verify();
        verifyNoInteractions(gateway);
    }

    @Test
    void enabledPermissionServiceDoesNotFallbackOnFailure() {
        UnifiedAuthorizationGateway gateway = mock(UnifiedAuthorizationGateway.class);
        PermissionRepository local = mock(PermissionRepository.class);
        when(gateway.query("main", 7L)).thenReturn(Mono.error(new IllegalStateException("Unavailable")));
        var unified = new UnifiedAuthorizationService(
                gateway, new MockEnvironment().withProperty("apex.features.auth-center-permission", "true"), null);
        StepVerifier.create(new PermissionService(unified, local, mock(TenantGuard.class))
                        .getPermissionsByUserId(7L)
                        .contextWrite(TenantContext.withTenant("main")))
                .expectError(IllegalStateException.class)
                .verify();
        verifyNoInteractions(local);
    }

    @Test
    void centralRoleNeverCopiesCentralIdentifier() {
        UnifiedAuthorizationGateway gateway = mock(UnifiedAuthorizationGateway.class);
        when(gateway.query("main", 7L))
                .thenReturn(Mono.just(new UnifiedAuthorizationGateway.Authorization(
                        "main", 7L, List.of(), List.of(new UnifiedAuthorizationGateway.Role("ADMIN", "SELF")))));
        var service = new UnifiedAuthorizationService(gateway, new MockEnvironment(), null);
        StepVerifier.create(service.roles(7L).contextWrite(TenantContext.withTenant("main")))
                .assertNext(roles -> {
                    assertThat(roles.get(0).getCode()).isEqualTo("ADMIN");
                    assertThat(roles.get(0).getId()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void unmappedDepartmentScopeMustDeny() {
        UnifiedAuthorizationGateway gateway = mock(UnifiedAuthorizationGateway.class);
        when(gateway.query("main", 7L))
                .thenReturn(Mono.just(new UnifiedAuthorizationGateway.Authorization(
                        "main", 7L, List.of(), List.of(new UnifiedAuthorizationGateway.Role("ADMIN", "DEPT")))));
        var service = new UnifiedAuthorizationService(gateway, new MockEnvironment(), null);
        StepVerifier.create(service.roles(7L).contextWrite(TenantContext.withTenant("main")))
                .assertNext(roles -> assertThat(roles.get(0).getDataScope())
                        .isEqualTo(com.apex.scaffold.common.enums.DataScopeType.DEPT))
                .verifyComplete();
    }

    @Test
    void customAndUnknownScopesMustDeny() {
        for (String scope : List.of("CUSTOM", "UNRECOGNIZED")) {
            UnifiedAuthorizationGateway gateway = mock(UnifiedAuthorizationGateway.class);
            when(gateway.query("main", 7L))
                    .thenReturn(Mono.just(new UnifiedAuthorizationGateway.Authorization(
                            "main", 7L, List.of(), List.of(new UnifiedAuthorizationGateway.Role("ADMIN", scope)))));
            var service = new UnifiedAuthorizationService(gateway, new MockEnvironment(), null);
            StepVerifier.create(service.roles(7L).contextWrite(TenantContext.withTenant("main")))
                    .expectError(IllegalStateException.class)
                    .verify();
        }
    }

    @Test
    void departmentAndChildrenScopesHaveExplicitMappings() {
        for (var item : java.util.Map.of(
                        "DEPT", com.apex.scaffold.common.enums.DataScopeType.DEPT,
                        "DEPT_AND_CHILD", com.apex.scaffold.common.enums.DataScopeType.DEPT_AND_CHILD)
                .entrySet()) {
            UnifiedAuthorizationGateway gateway = mock(UnifiedAuthorizationGateway.class);
            when(gateway.query("main", 7L))
                    .thenReturn(Mono.just(new UnifiedAuthorizationGateway.Authorization(
                            "main",
                            7L,
                            List.of(),
                            List.of(new UnifiedAuthorizationGateway.Role("ADMIN", item.getKey())))));
            var service = new UnifiedAuthorizationService(gateway, new MockEnvironment(), null);
            StepVerifier.create(service.roles(7L).contextWrite(TenantContext.withTenant("main")))
                    .assertNext(roles -> assertThat(roles.get(0).getDataScope()).isEqualTo(item.getValue()))
                    .verifyComplete();
        }
    }

    @Test
    void expiredL1EntryCannotGrantAuthorization() {
        UnifiedAuthorizationGateway gateway = mock(UnifiedAuthorizationGateway.class);
        var cache = mock(com.apex.scaffold.domain.gateway.AuthorizationCache.class);
        var old = new UnifiedAuthorizationGateway.Authorization("main", 7L, List.of("write"), List.of());
        when(cache.get(anyString(), eq(UnifiedAuthorizationService.CachedAuthorization.class)))
                .thenReturn(Mono.just(new UnifiedAuthorizationService.CachedAuthorization(old, 0)));
        when(gateway.query("main", 7L)).thenReturn(Mono.error(new IllegalStateException("Unavailable")));
        var service = new UnifiedAuthorizationService(
                gateway, new MockEnvironment().withProperty("apex.auth-center-permission.cache-seconds", "5"), cache);
        StepVerifier.create(service.permissions(7L).contextWrite(TenantContext.withTenant("main")))
                .expectError(IllegalStateException.class)
                .verify();
        verify(gateway).query("main", 7L);
    }

    @Test
    void foreignCachedIdentityCannotGrantAuthorization() {
        UnifiedAuthorizationGateway gateway = mock(UnifiedAuthorizationGateway.class);
        var cache = mock(com.apex.scaffold.domain.gateway.AuthorizationCache.class);
        var old = new UnifiedAuthorizationGateway.Authorization("foreign", 7L, List.of("write"), List.of());
        when(cache.get(anyString(), eq(UnifiedAuthorizationService.CachedAuthorization.class)))
                .thenReturn(Mono.just(new UnifiedAuthorizationService.CachedAuthorization(old, Long.MAX_VALUE)));
        when(gateway.query("main", 7L)).thenReturn(Mono.error(new IllegalStateException("Unavailable")));
        var service = new UnifiedAuthorizationService(
                gateway, new MockEnvironment().withProperty("apex.auth-center-permission.cache-seconds", "5"), cache);
        StepVerifier.create(service.permissions(7L).contextWrite(TenantContext.withTenant("main")))
                .expectError(IllegalStateException.class)
                .verify();
        verify(cache)
                .get(
                        eq("auth-center:authorization:v1:bWFpbg:7"),
                        eq(UnifiedAuthorizationService.CachedAuthorization.class));
    }

    @Test
    void freshSnapshotIsUsedWithoutRemoteLookup() {
        UnifiedAuthorizationGateway gateway = mock(UnifiedAuthorizationGateway.class);
        var cache = mock(com.apex.scaffold.domain.gateway.AuthorizationCache.class);
        var data = new UnifiedAuthorizationGateway.Authorization("main", 7L, List.of("user:read"), List.of());
        when(cache.get(anyString(), eq(UnifiedAuthorizationService.CachedAuthorization.class)))
                .thenReturn(Mono.just(new UnifiedAuthorizationService.CachedAuthorization(data, Long.MAX_VALUE)));
        var service = new UnifiedAuthorizationService(
                gateway, new MockEnvironment().withProperty("apex.auth-center-permission.cache-seconds", "5"), cache);
        StepVerifier.create(service.permissions(7L).contextWrite(TenantContext.withTenant("main")))
                .assertNext(values -> assertThat(values.get(0).getCode()).isEqualTo("user:read"))
                .verifyComplete();
        verifyNoInteractions(gateway);
    }

    @Test
    void cacheMissWritesConfiguredTtl() {
        UnifiedAuthorizationGateway gateway = mock(UnifiedAuthorizationGateway.class);
        var cache = mock(com.apex.scaffold.domain.gateway.AuthorizationCache.class);
        var data = new UnifiedAuthorizationGateway.Authorization("main", 7L, List.of(), List.of());
        when(cache.get(anyString(), eq(UnifiedAuthorizationService.CachedAuthorization.class)))
                .thenReturn(Mono.empty());
        when(gateway.query("main", 7L)).thenReturn(Mono.just(data));
        when(cache.set(anyString(), any(), eq(java.time.Duration.ofSeconds(5)))).thenReturn(Mono.just(true));
        var service = new UnifiedAuthorizationService(
                gateway, new MockEnvironment().withProperty("apex.auth-center-permission.cache-seconds", "5"), cache);
        StepVerifier.create(service.permissions(7L).contextWrite(TenantContext.withTenant("main")))
                .expectNext(List.of())
                .verifyComplete();
        verify(cache)
                .set(
                        eq("auth-center:authorization:v1:bWFpbg:7"),
                        argThat((UnifiedAuthorizationService.CachedAuthorization value) ->
                                value.authorization().equals(data) && value.expiresAt() > System.currentTimeMillis()),
                        eq(java.time.Duration.ofSeconds(5)));
    }

    @Test
    void invalidCacheTtlIsRejected() {
        var gateway = mock(UnifiedAuthorizationGateway.class);
        for (String value : List.of("-1", "31")) {
            org.junit.jupiter.api.Assertions.assertThrows(
                    IllegalArgumentException.class,
                    () -> new UnifiedAuthorizationService(
                            gateway,
                            new MockEnvironment().withProperty("apex.auth-center-permission.cache-seconds", value),
                            null));
        }
    }

    @Test
    void remoteIdentityMismatchIsRejectedBeforeCaching() {
        for (long ttl : List.of(0L, 5L)) {
            for (var data : List.of(
                    new UnifiedAuthorizationGateway.Authorization("foreign", 7L, List.of("write"), List.of()),
                    new UnifiedAuthorizationGateway.Authorization("main", 8L, List.of("write"), List.of()))) {
                var gateway = mock(UnifiedAuthorizationGateway.class);
                var cache = mock(com.apex.scaffold.domain.gateway.AuthorizationCache.class);
                when(gateway.query("main", 7L)).thenReturn(Mono.just(data));
                if (ttl > 0) {
                    when(cache.get(anyString(), eq(UnifiedAuthorizationService.CachedAuthorization.class)))
                            .thenReturn(Mono.empty());
                    when(cache.set(anyString(), any(), any())).thenReturn(Mono.just(true));
                }
                var service = new UnifiedAuthorizationService(
                        gateway,
                        new MockEnvironment()
                                .withProperty("apex.auth-center-permission.cache-seconds", String.valueOf(ttl)),
                        cache);
                StepVerifier.create(service.permissions(7L).contextWrite(TenantContext.withTenant("main")))
                        .expectError(IllegalStateException.class)
                        .verify();
                verify(cache, never()).set(anyString(), any(), any());
            }
        }
    }

    @Test
    void emptyRemoteResponseIsRejectedWithAndWithoutCache() {
        for (long ttl : List.of(0L, 5L)) {
            var gateway = mock(UnifiedAuthorizationGateway.class);
            var cache = mock(com.apex.scaffold.domain.gateway.AuthorizationCache.class);
            when(gateway.query("main", 7L)).thenReturn(Mono.empty());
            if (ttl > 0) {
                when(cache.get(anyString(), eq(UnifiedAuthorizationService.CachedAuthorization.class)))
                        .thenReturn(Mono.empty());
            }
            var service = new UnifiedAuthorizationService(
                    gateway,
                    new MockEnvironment()
                            .withProperty("apex.auth-center-permission.cache-seconds", String.valueOf(ttl)),
                    cache);
            StepVerifier.create(service.roles(7L).contextWrite(TenantContext.withTenant("main")))
                    .expectError(IllegalStateException.class)
                    .verify();
            verify(cache, never()).set(anyString(), any(), any());
        }
    }

    @Test
    void malformedCachedSnapshotReloadsRemoteAuthorization() {
        for (var snapshot : List.of(
                new UnifiedAuthorizationService.CachedAuthorization(null, Long.MAX_VALUE),
                new UnifiedAuthorizationService.CachedAuthorization(
                        new UnifiedAuthorizationGateway.Authorization("main", 7L, null, List.of()), Long.MAX_VALUE),
                new UnifiedAuthorizationService.CachedAuthorization(
                        new UnifiedAuthorizationGateway.Authorization("main", 7L, List.of(" "), List.of()),
                        Long.MAX_VALUE))) {
            var gateway = mock(UnifiedAuthorizationGateway.class);
            var cache = mock(com.apex.scaffold.domain.gateway.AuthorizationCache.class);
            when(cache.get(anyString(), eq(UnifiedAuthorizationService.CachedAuthorization.class)))
                    .thenReturn(Mono.just(snapshot));
            when(gateway.query("main", 7L))
                    .thenReturn(Mono.just(new UnifiedAuthorizationGateway.Authorization(
                            "main", 7L, List.of("user:read"), List.of())));
            when(cache.set(anyString(), any(), any())).thenReturn(Mono.just(true));
            var service = new UnifiedAuthorizationService(
                    gateway,
                    new MockEnvironment().withProperty("apex.auth-center-permission.cache-seconds", "5"),
                    cache);
            StepVerifier.create(service.permissions(7L).contextWrite(TenantContext.withTenant("main")))
                    .assertNext(values -> assertThat(values)
                            .extracting(value -> value.getCode())
                            .containsExactly("user:read"))
                    .verifyComplete();
            verify(gateway).query("main", 7L);
        }
    }

    @Test
    void invalidCacheCannotFallbackWhenRemoteFails() {
        var gateway = mock(UnifiedAuthorizationGateway.class);
        var cache = mock(com.apex.scaffold.domain.gateway.AuthorizationCache.class);
        when(cache.get(anyString(), eq(UnifiedAuthorizationService.CachedAuthorization.class)))
                .thenReturn(Mono.just(new UnifiedAuthorizationService.CachedAuthorization(null, Long.MAX_VALUE)));
        when(gateway.query("main", 7L)).thenReturn(Mono.error(new IllegalStateException("Unavailable")));
        var service = new UnifiedAuthorizationService(
                gateway, new MockEnvironment().withProperty("apex.auth-center-permission.cache-seconds", "5"), cache);
        StepVerifier.create(service.permissions(7L).contextWrite(TenantContext.withTenant("main")))
                .expectError(IllegalStateException.class)
                .verify();
        verify(gateway).query("main", 7L);
        verify(cache, never()).set(anyString(), any(), any());
    }

    @Test
    void expiredCacheIsReplacedByFreshRemoteSnapshot() {
        var gateway = mock(UnifiedAuthorizationGateway.class);
        var cache = mock(com.apex.scaffold.domain.gateway.AuthorizationCache.class);
        var old = new UnifiedAuthorizationGateway.Authorization("main", 7L, List.of("write"), List.of());
        when(cache.get(anyString(), eq(UnifiedAuthorizationService.CachedAuthorization.class)))
                .thenReturn(Mono.just(new UnifiedAuthorizationService.CachedAuthorization(old, 0)));
        when(gateway.query("main", 7L))
                .thenReturn(Mono.just(new UnifiedAuthorizationGateway.Authorization("main", 7L, List.of(), List.of())));
        when(cache.set(anyString(), any(), any())).thenReturn(Mono.just(true));
        var service = new UnifiedAuthorizationService(
                gateway, new MockEnvironment().withProperty("apex.auth-center-permission.cache-seconds", "5"), cache);
        StepVerifier.create(service.permissions(7L).contextWrite(TenantContext.withTenant("main")))
                .expectNext(List.of())
                .verifyComplete();
        verify(cache)
                .set(
                        anyString(),
                        argThat((UnifiedAuthorizationService.CachedAuthorization value) ->
                                value.authorization().permissions().isEmpty()),
                        eq(java.time.Duration.ofSeconds(5)));
    }
}
