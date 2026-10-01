package com.apex.scaffold.authcenter.domain.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("资源权限服务测试")
class ResourcePermissionServiceTest {

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    @Mock
    private ReactiveHashOperations<String, Object, Object> hashOperations;

    private ResourcePermissionService service;

    private static final String TEST_USER_ID = "user-123";
    private static final String TEST_RESOURCE_TYPE = "document";
    private static final String TEST_RESOURCE_ID = "doc-456";
    private static final String OWNER_ID = "owner-789";

    @BeforeEach
    void setUp() {
        service = new ResourcePermissionService(redisTemplate);
    }

    @Test
    @DisplayName("检查权限 - 超级管理员直接通过")
    void testHasPermission_SuperAdmin() {
        ResourcePermissionService.PermissionContext context =
                new ResourcePermissionService.PermissionContext(true, Set.of(), "192.168.1.1", "device-1");

        StepVerifier.create(service.hasPermission(TEST_USER_ID, TEST_RESOURCE_TYPE, TEST_RESOURCE_ID, "READ", context))
                .expectNext(true)
                .verifyComplete();

        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("检查权限 - 资源所有者直接通过")
    void testHasPermission_ResourceOwner() {
        ResourcePermissionService.PermissionContext context =
                new ResourcePermissionService.PermissionContext(false, Set.of(), "192.168.1.1", "device-1");

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("permission:resource:owner:" + TEST_RESOURCE_TYPE + ":" + TEST_RESOURCE_ID))
                .thenReturn(Mono.just(TEST_USER_ID));

        StepVerifier.create(service.hasPermission(TEST_USER_ID, TEST_RESOURCE_TYPE, TEST_RESOURCE_ID, "WRITE", context))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("检查权限 - 显式授权通过")
    void testHasPermission_ExplicitGrant() {
        ResourcePermissionService.PermissionContext context =
                new ResourcePermissionService.PermissionContext(false, Set.of(), "192.168.1.1", "device-1");

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(valueOperations.get("permission:resource:owner:" + TEST_RESOURCE_TYPE + ":" + TEST_RESOURCE_ID))
                .thenReturn(Mono.just(OWNER_ID));
        when(hashOperations.get(
                        "permission:resource:share:" + TEST_RESOURCE_TYPE + ":" + TEST_RESOURCE_ID, TEST_USER_ID))
                .thenReturn(Mono.just("READ,WRITE"));

        StepVerifier.create(service.hasPermission(TEST_USER_ID, TEST_RESOURCE_TYPE, TEST_RESOURCE_ID, "READ", context))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("检查权限 - 临时权限通过")
    void testHasPermission_TemporaryGrant() {
        ResourcePermissionService.PermissionContext context =
                new ResourcePermissionService.PermissionContext(false, Set.of(), "192.168.1.1", "device-1");

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(valueOperations.get("permission:resource:owner:" + TEST_RESOURCE_TYPE + ":" + TEST_RESOURCE_ID))
                .thenReturn(Mono.just(OWNER_ID));
        when(hashOperations.get(
                        "permission:resource:share:" + TEST_RESOURCE_TYPE + ":" + TEST_RESOURCE_ID, TEST_USER_ID))
                .thenReturn(Mono.empty());
        when(valueOperations.get("permission:temp:" + TEST_RESOURCE_TYPE + ":" + TEST_RESOURCE_ID + ":" + TEST_USER_ID))
                .thenReturn(Mono.just("READ"));

        StepVerifier.create(service.hasPermission(TEST_USER_ID, TEST_RESOURCE_TYPE, TEST_RESOURCE_ID, "READ", context))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("检查权限 - 敏感资源外网IP拒绝")
    void testHasPermission_SensitiveDataExternalIpDenied() {
        ResourcePermissionService.PermissionContext context =
                new ResourcePermissionService.PermissionContext(false, Set.of(), "8.8.8.8", "device-1");

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(valueOperations.get("permission:resource:owner:sensitive-data:" + TEST_RESOURCE_ID))
                .thenReturn(Mono.just(OWNER_ID));
        when(hashOperations.get("permission:resource:share:sensitive-data:" + TEST_RESOURCE_ID, TEST_USER_ID))
                .thenReturn(Mono.just("READ"));

        StepVerifier.create(service.hasPermission(TEST_USER_ID, "sensitive-data", TEST_RESOURCE_ID, "READ", context))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("检查权限 - 无权限拒绝")
    void testHasPermission_Denied() {
        ResourcePermissionService.PermissionContext context =
                new ResourcePermissionService.PermissionContext(false, Set.of(), "192.168.1.1", "device-1");

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(valueOperations.get("permission:resource:owner:" + TEST_RESOURCE_TYPE + ":" + TEST_RESOURCE_ID))
                .thenReturn(Mono.just(OWNER_ID));
        when(hashOperations.get(
                        "permission:resource:share:" + TEST_RESOURCE_TYPE + ":" + TEST_RESOURCE_ID, TEST_USER_ID))
                .thenReturn(Mono.empty());
        when(valueOperations.get("permission:temp:" + TEST_RESOURCE_TYPE + ":" + TEST_RESOURCE_ID + ":" + TEST_USER_ID))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.hasPermission(TEST_USER_ID, TEST_RESOURCE_TYPE, TEST_RESOURCE_ID, "WRITE", context))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("检查是否为资源所有者 - 是")
    void testIsResourceOwner_True() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("permission:resource:owner:" + TEST_RESOURCE_TYPE + ":" + TEST_RESOURCE_ID))
                .thenReturn(Mono.just(TEST_USER_ID));

        StepVerifier.create(service.isResourceOwner(TEST_USER_ID, TEST_RESOURCE_TYPE, TEST_RESOURCE_ID))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("检查是否为资源所有者 - 否")
    void testIsResourceOwner_False() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("permission:resource:owner:" + TEST_RESOURCE_TYPE + ":" + TEST_RESOURCE_ID))
                .thenReturn(Mono.just(OWNER_ID));

        StepVerifier.create(service.isResourceOwner(TEST_USER_ID, TEST_RESOURCE_TYPE, TEST_RESOURCE_ID))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("检查是否为资源所有者 - 资源不存在")
    void testIsResourceOwner_NotExists() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("permission:resource:owner:" + TEST_RESOURCE_TYPE + ":" + TEST_RESOURCE_ID))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.isResourceOwner(TEST_USER_ID, TEST_RESOURCE_TYPE, TEST_RESOURCE_ID))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("设置资源所有者")
    void testSetResourceOwner() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.set("permission:resource:owner:" + TEST_RESOURCE_TYPE + ":" + TEST_RESOURCE_ID, OWNER_ID))
                .thenReturn(Mono.just(true));

        StepVerifier.create(service.setResourceOwner(TEST_RESOURCE_TYPE, TEST_RESOURCE_ID, OWNER_ID))
                .verifyComplete();

        verify(valueOperations)
                .set("permission:resource:owner:" + TEST_RESOURCE_TYPE + ":" + TEST_RESOURCE_ID, OWNER_ID);
    }

    @Test
    @DisplayName("授予资源权限")
    void testGrantPermission() {
        Set<String> permissions = Set.of("READ", "WRITE");
        String targetUserId = "user-999";

        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.put(
                        eq("permission:resource:share:" + TEST_RESOURCE_TYPE + ":" + TEST_RESOURCE_ID),
                        eq(targetUserId),
                        anyString()))
                .thenReturn(Mono.just(true));

        StepVerifier.create(service.grantPermission(TEST_RESOURCE_TYPE, TEST_RESOURCE_ID, targetUserId, permissions))
                .verifyComplete();

        verify(hashOperations)
                .put(
                        eq("permission:resource:share:" + TEST_RESOURCE_TYPE + ":" + TEST_RESOURCE_ID),
                        eq(targetUserId),
                        argThat(permStr -> {
                            String str = (String) permStr;
                            return str.contains("READ") && str.contains("WRITE");
                        }));
    }

    @Test
    @DisplayName("撤销资源权限")
    void testRevokePermission() {
        String targetUserId = "user-999";

        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.remove(
                        "permission:resource:share:" + TEST_RESOURCE_TYPE + ":" + TEST_RESOURCE_ID, targetUserId))
                .thenReturn(Mono.just(1L));

        StepVerifier.create(service.revokePermission(TEST_RESOURCE_TYPE, TEST_RESOURCE_ID, targetUserId))
                .verifyComplete();

        verify(hashOperations)
                .remove("permission:resource:share:" + TEST_RESOURCE_TYPE + ":" + TEST_RESOURCE_ID, targetUserId);
    }

    @Test
    @DisplayName("获取资源的所有授权用户")
    void testGetResourcePermissions() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries("permission:resource:share:" + TEST_RESOURCE_TYPE + ":" + TEST_RESOURCE_ID))
                .thenReturn(Flux.just(
                        Map.entry("user-1", "READ"), Map.entry("user-2", "READ,WRITE"), Map.entry("user-3", "MANAGE")));

        StepVerifier.create(service.getResourcePermissions(TEST_RESOURCE_TYPE, TEST_RESOURCE_ID))
                .assertNext(grant -> {
                    assertEquals("user-1", grant.getUserId());
                    assertTrue(grant.getPermissions().contains("READ"));
                    assertEquals(1, grant.getPermissions().size());
                })
                .assertNext(grant -> {
                    assertEquals("user-2", grant.getUserId());
                    assertTrue(grant.getPermissions().contains("READ"));
                    assertTrue(grant.getPermissions().contains("WRITE"));
                    assertEquals(2, grant.getPermissions().size());
                })
                .assertNext(grant -> {
                    assertEquals("user-3", grant.getUserId());
                    assertTrue(grant.getPermissions().contains("MANAGE"));
                    assertEquals(1, grant.getPermissions().size());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("授予临时权限")
    void testGrantTemporaryPermission() {
        Set<String> permissions = Set.of("READ");
        String targetUserId = "user-temp";
        long durationSeconds = 3600;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.set(
                        eq("permission:temp:" + TEST_RESOURCE_TYPE + ":" + TEST_RESOURCE_ID + ":" + targetUserId),
                        anyString(),
                        eq(Duration.ofSeconds(durationSeconds))))
                .thenReturn(Mono.just(true));

        StepVerifier.create(service.grantTemporaryPermission(
                        TEST_RESOURCE_TYPE, TEST_RESOURCE_ID, targetUserId, permissions, durationSeconds))
                .verifyComplete();

        verify(valueOperations)
                .set(
                        eq("permission:temp:" + TEST_RESOURCE_TYPE + ":" + TEST_RESOURCE_ID + ":" + targetUserId),
                        eq("READ"),
                        eq(Duration.ofSeconds(durationSeconds)));
    }

    @Test
    @DisplayName("权限层级 - MANAGE包含所有权限")
    void testPermissionHierarchy_ManageIncludesAll() {
        ResourcePermissionService.PermissionContext context =
                new ResourcePermissionService.PermissionContext(false, Set.of(), "192.168.1.1", "device-1");

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(valueOperations.get("permission:resource:owner:" + TEST_RESOURCE_TYPE + ":" + TEST_RESOURCE_ID))
                .thenReturn(Mono.just(OWNER_ID));
        when(hashOperations.get(
                        "permission:resource:share:" + TEST_RESOURCE_TYPE + ":" + TEST_RESOURCE_ID, TEST_USER_ID))
                .thenReturn(Mono.just("MANAGE"));

        StepVerifier.create(service.hasPermission(TEST_USER_ID, TEST_RESOURCE_TYPE, TEST_RESOURCE_ID, "READ", context))
                .expectNext(true)
                .verifyComplete();

        StepVerifier.create(service.hasPermission(TEST_USER_ID, TEST_RESOURCE_TYPE, TEST_RESOURCE_ID, "WRITE", context))
                .expectNext(true)
                .verifyComplete();

        StepVerifier.create(
                        service.hasPermission(TEST_USER_ID, TEST_RESOURCE_TYPE, TEST_RESOURCE_ID, "DELETE", context))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("权限层级 - WRITE包含READ")
    void testPermissionHierarchy_WriteIncludesRead() {
        ResourcePermissionService.PermissionContext context =
                new ResourcePermissionService.PermissionContext(false, Set.of(), "192.168.1.1", "device-1");

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(valueOperations.get("permission:resource:owner:" + TEST_RESOURCE_TYPE + ":" + TEST_RESOURCE_ID))
                .thenReturn(Mono.just(OWNER_ID));
        when(hashOperations.get(
                        "permission:resource:share:" + TEST_RESOURCE_TYPE + ":" + TEST_RESOURCE_ID, TEST_USER_ID))
                .thenReturn(Mono.just("WRITE"));

        StepVerifier.create(service.hasPermission(TEST_USER_ID, TEST_RESOURCE_TYPE, TEST_RESOURCE_ID, "READ", context))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("权限层级 - READ不包含WRITE")
    void testPermissionHierarchy_ReadDoesNotIncludeWrite() {
        ResourcePermissionService.PermissionContext context =
                new ResourcePermissionService.PermissionContext(false, Set.of(), "192.168.1.1", "device-1");

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(valueOperations.get("permission:resource:owner:" + TEST_RESOURCE_TYPE + ":" + TEST_RESOURCE_ID))
                .thenReturn(Mono.just(OWNER_ID));
        when(hashOperations.get(
                        "permission:resource:share:" + TEST_RESOURCE_TYPE + ":" + TEST_RESOURCE_ID, TEST_USER_ID))
                .thenReturn(Mono.just("READ"));
        when(valueOperations.get("permission:temp:" + TEST_RESOURCE_TYPE + ":" + TEST_RESOURCE_ID + ":" + TEST_USER_ID))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.hasPermission(TEST_USER_ID, TEST_RESOURCE_TYPE, TEST_RESOURCE_ID, "WRITE", context))
                .expectNext(false)
                .verifyComplete();
    }
}
