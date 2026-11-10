package com.apex.scaffold.auth.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.apex.scaffold.auth.domain.repository.AuthorizationQueryPort;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("资源权限服务测试")
class ResourcePermissionServiceTest {

    private static final String USER_ID = "123";
    private static final Long NUMERIC_USER_ID = 123L;
    private static final String RESOURCE_TYPE = "document";
    private static final String RESOURCE_ID = "doc-456";
    private static final String OWNER_ID = "789";
    private static final String TENANT_ID = "tenant-1";

    @Mock
    private ResourcePermissionStore permissionStore;

    @Mock
    private AuthorizationQueryPort authorizationQueryPort;

    private ResourcePermissionService service;

    @BeforeEach
    void setUp() {
        service = new ResourcePermissionService(permissionStore, authorizationQueryPort);
    }

    @Test
    @DisplayName("超级管理员必须具有租户作用域才允许通过")
    void superAdminRequiresTenantScope() {
        StepVerifier.create(
                        service.hasPermission(USER_ID, RESOURCE_TYPE, RESOURCE_ID, "READ", context(true, TENANT_ID)))
                .expectNext(true)
                .verifyComplete();
        StepVerifier.create(service.hasPermission(USER_ID, RESOURCE_TYPE, RESOURCE_ID, "READ", context(true, null)))
                .expectNext(false)
                .verifyComplete();
        verifyNoInteractions(permissionStore, authorizationQueryPort);
    }

    @Test
    @DisplayName("缺少租户作用域时默认拒绝且不查询端口")
    void missingTenantFailsClosed() {
        StepVerifier.create(service.hasPermission(USER_ID, RESOURCE_TYPE, RESOURCE_ID, "READ", context(false, "")))
                .expectNext(false)
                .verifyComplete();
        verifyNoInteractions(permissionStore, authorizationQueryPort);
    }

    @Test
    @DisplayName("非数字用户标识默认拒绝且不查询授权快照")
    void invalidUserIdFailsClosed() {
        StepVerifier.create(service.hasPermission(
                        "user-123", RESOURCE_TYPE, RESOURCE_ID, "READ", context(false, TENANT_ID)))
                .expectNext(false)
                .verifyComplete();
        verifyNoInteractions(permissionStore, authorizationQueryPort);
    }

    @Test
    @DisplayName("授权快照错误不降级为放行")
    void authorizationQueryErrorIsNotSwallowed() {
        when(authorizationQueryPort.queryPermissions(TENANT_ID, NUMERIC_USER_ID))
                .thenReturn(Mono.error(new IllegalStateException("authorization unavailable")));

        StepVerifier.create(
                        service.hasPermission(USER_ID, RESOURCE_TYPE, RESOURCE_ID, "READ", context(false, TENANT_ID)))
                .expectError(IllegalStateException.class)
                .verify();
        verifyNoInteractions(permissionStore);
    }

    @Test
    @DisplayName("资源所有者在同租户下直接通过")
    void ownerIsAllowedWithinTenant() {
        allowResourcePermission("document:read");
        when(permissionStore.findOwner(RESOURCE_TYPE, RESOURCE_ID, TENANT_ID)).thenReturn(Mono.just(USER_ID));

        StepVerifier.create(
                        service.hasPermission(USER_ID, RESOURCE_TYPE, RESOURCE_ID, "READ", context(false, TENANT_ID)))
                .expectNext(true)
                .verifyComplete();
        verify(permissionStore).findOwner(RESOURCE_TYPE, RESOURCE_ID, TENANT_ID);
    }

    @Test
    @DisplayName("资源所有者查询使用租户作用域，跨租户数据不放行")
    void ownerIsDeniedWhenTenantDoesNotMatch() {
        allowResourcePermission("document:read");
        when(permissionStore.findOwner(RESOURCE_TYPE, RESOURCE_ID, TENANT_ID)).thenReturn(Mono.empty());
        when(permissionStore.findExplicitPermission(RESOURCE_TYPE, RESOURCE_ID, USER_ID, TENANT_ID))
                .thenReturn(Mono.empty());
        when(permissionStore.findTemporaryPermission(RESOURCE_TYPE, RESOURCE_ID, USER_ID, TENANT_ID))
                .thenReturn(Mono.empty());

        StepVerifier.create(
                        service.hasPermission(USER_ID, RESOURCE_TYPE, RESOURCE_ID, "READ", context(false, TENANT_ID)))
                .expectNext(false)
                .verifyComplete();
        verify(permissionStore).findOwner(RESOURCE_TYPE, RESOURCE_ID, TENANT_ID);
    }

    @Test
    @DisplayName("显式授权按权限层级通过")
    void explicitGrantHonorsPermissionHierarchy() {
        allowResourcePermission("document:read");
        when(permissionStore.findOwner(RESOURCE_TYPE, RESOURCE_ID, TENANT_ID)).thenReturn(Mono.just(OWNER_ID));
        when(permissionStore.findExplicitPermission(RESOURCE_TYPE, RESOURCE_ID, USER_ID, TENANT_ID))
                .thenReturn(Mono.just("WRITE"));

        StepVerifier.create(
                        service.hasPermission(USER_ID, RESOURCE_TYPE, RESOURCE_ID, "READ", context(false, TENANT_ID)))
                .expectNext(true)
                .verifyComplete();
        verify(permissionStore).findExplicitPermission(RESOURCE_TYPE, RESOURCE_ID, USER_ID, TENANT_ID);
    }

    @Test
    @DisplayName("临时授权按租户作用域和权限层级通过")
    void temporaryGrantUsesTenantScope() {
        allowResourcePermission("document:read");
        when(permissionStore.findOwner(RESOURCE_TYPE, RESOURCE_ID, TENANT_ID)).thenReturn(Mono.just(OWNER_ID));
        when(permissionStore.findExplicitPermission(RESOURCE_TYPE, RESOURCE_ID, USER_ID, TENANT_ID))
                .thenReturn(Mono.empty());
        when(permissionStore.findTemporaryPermission(RESOURCE_TYPE, RESOURCE_ID, USER_ID, TENANT_ID))
                .thenReturn(Mono.just("READ"));

        StepVerifier.create(
                        service.hasPermission(USER_ID, RESOURCE_TYPE, RESOURCE_ID, "READ", context(false, TENANT_ID)))
                .expectNext(true)
                .verifyComplete();
        verify(permissionStore).findTemporaryPermission(RESOURCE_TYPE, RESOURCE_ID, USER_ID, TENANT_ID);
    }

    @Test
    @DisplayName("敏感资源外网访问拒绝")
    void sensitiveResourceRejectsExternalIp() {
        allowResourcePermission("sensitive-data:read");
        when(permissionStore.findOwner("sensitive-data", RESOURCE_ID, TENANT_ID))
                .thenReturn(Mono.just(USER_ID));

        StepVerifier.create(service.hasPermission(
                        USER_ID, "sensitive-data", RESOURCE_ID, "READ", context(false, TENANT_ID, "8.8.8.8")))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("RBAC 管理权限直接通过且不读取资源 ACL")
    void rbacManagementPermissionDoesNotReadResourceStore() {
        when(authorizationQueryPort.queryPermissions(TENANT_ID, NUMERIC_USER_ID))
                .thenReturn(Mono.just(Set.of("session:terminate")));

        StepVerifier.create(service.hasPermission(USER_ID, "session", "session-1", "MANAGE", context(false, TENANT_ID)))
                .expectNext(true)
                .verifyComplete();
        verifyNoInteractions(permissionStore);
    }

    @Test
    @DisplayName("审计日志必须匹配租户资源标识")
    void auditLogsRequireMatchingTenantResource() {
        StepVerifier.create(
                        service.hasPermission(USER_ID, "audit-logs", "tenant-2", "MANAGE", context(false, TENANT_ID)))
                .expectNext(false)
                .verifyComplete();
        verifyNoInteractions(permissionStore, authorizationQueryPort);
    }

    @Test
    @DisplayName("资源授权写入和读取透传租户作用域")
    void resourceStoreOperationsKeepTenantScope() {
        when(permissionStore.saveOwner(RESOURCE_TYPE, RESOURCE_ID, OWNER_ID, TENANT_ID))
                .thenReturn(Mono.empty());
        when(permissionStore.saveExplicitPermission(
                        eq(RESOURCE_TYPE),
                        eq(RESOURCE_ID),
                        eq(USER_ID),
                        argThat(value -> Set.of("READ", "WRITE").equals(Set.of(value.split(",")))),
                        eq(TENANT_ID)))
                .thenReturn(Mono.empty());
        when(permissionStore.deleteExplicitPermission(RESOURCE_TYPE, RESOURCE_ID, USER_ID, TENANT_ID))
                .thenReturn(Mono.empty());
        when(permissionStore.findExplicitPermissions(RESOURCE_TYPE, RESOURCE_ID, TENANT_ID))
                .thenReturn(Flux.just(Map.entry(USER_ID, "READ,WRITE")));
        when(permissionStore.saveTemporaryPermission(
                        RESOURCE_TYPE, RESOURCE_ID, USER_ID, "READ", Duration.ofSeconds(60), TENANT_ID))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.setResourceOwner(RESOURCE_TYPE, RESOURCE_ID, OWNER_ID, TENANT_ID))
                .verifyComplete();
        StepVerifier.create(service.grantPermission(
                        RESOURCE_TYPE, RESOURCE_ID, USER_ID, Set.of("READ", "WRITE"), TENANT_ID))
                .verifyComplete();
        StepVerifier.create(service.revokePermission(RESOURCE_TYPE, RESOURCE_ID, USER_ID, TENANT_ID))
                .verifyComplete();
        StepVerifier.create(service.getResourcePermissions(RESOURCE_TYPE, RESOURCE_ID, TENANT_ID))
                .assertNext(grant -> {
                    assertEquals(USER_ID, grant.getUserId());
                    assertTrue(grant.getPermissions().containsAll(Set.of("READ", "WRITE")));
                })
                .verifyComplete();
        StepVerifier.create(service.grantTemporaryPermission(
                        RESOURCE_TYPE, RESOURCE_ID, USER_ID, Set.of("READ"), 60, TENANT_ID))
                .verifyComplete();

        verify(permissionStore).saveOwner(RESOURCE_TYPE, RESOURCE_ID, OWNER_ID, TENANT_ID);
        verify(permissionStore).saveExplicitPermission(RESOURCE_TYPE, RESOURCE_ID, USER_ID, "READ,WRITE", TENANT_ID);
        verify(permissionStore).deleteExplicitPermission(RESOURCE_TYPE, RESOURCE_ID, USER_ID, TENANT_ID);
        verify(permissionStore).findExplicitPermissions(RESOURCE_TYPE, RESOURCE_ID, TENANT_ID);
        verify(permissionStore)
                .saveTemporaryPermission(
                        RESOURCE_TYPE, RESOURCE_ID, USER_ID, "READ", Duration.ofSeconds(60), TENANT_ID);
    }

    private void allowResourcePermission(String permission) {
        when(authorizationQueryPort.queryPermissions(TENANT_ID, NUMERIC_USER_ID))
                .thenReturn(Mono.just(Set.of(permission)));
    }

    private ResourcePermissionService.PermissionContext context(boolean superAdmin, String tenantId) {
        return context(superAdmin, tenantId, "192.168.1.1");
    }

    private ResourcePermissionService.PermissionContext context(boolean superAdmin, String tenantId, String clientIp) {
        return new ResourcePermissionService.PermissionContext(superAdmin, Set.of(), clientIp, "device-1", tenantId);
    }
}
