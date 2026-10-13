package com.apex.scaffold.auth.application.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.auth.domain.permission.ResourcePermission;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("ResourcePermissionService(application) 单元测试")
class ResourcePermissionServiceTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    private ResourcePermissionService service;

    @BeforeEach
    void setUp() {
        service = new ResourcePermissionService(redisTemplate);
    }

    @Nested
    @DisplayName("hasPermission() 方法测试")
    class HasPermissionTest {

        @Test
        @DisplayName("缓存命中且为true")
        void testHasPermission_CacheHitTrue() {
            when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(true));
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(Mono.just("true"));

            StepVerifier.create(service.hasPermission("user-1", "tenant-1", "ORDER", "1", "READ", Map.of()))
                    .expectNext(true)
                    .verifyComplete();
        }

        @Test
        @DisplayName("缓存命中且为false")
        void testHasPermission_CacheHitFalse() {
            when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(false));
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.set(anyString(), anyString(), any())).thenReturn(Mono.just(true));

            StepVerifier.create(service.hasPermission("user-1", "tenant-1", "UNKNOWN", "1", "READ", Map.of()))
                    .expectNext(false)
                    .verifyComplete();
        }

        @Test
        @DisplayName("无缓存，resourceType为通配符*时匹配全局权限")
        void testHasPermission_AdminGlobalPermission() {
            when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(false));
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.set(anyString(), anyString(), any())).thenReturn(Mono.just(true));

            StepVerifier.create(service.hasPermission("admin-1", "tenant-1", "*", "res-1", "*", Map.of()))
                    .expectNext(true)
                    .verifyComplete();
        }

        @Test
        @DisplayName("无缓存，条件评估异常时降级为不通过并继续检查后续权限")
        void testHasPermission_ConditionEvaluationError() {
            when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(false));
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.set(anyString(), anyString(), any())).thenReturn(Mono.just(true));

            // context为空Map时，SpEL表达式访问#resource.userId会抛出异常，被内部捕获后继续判断，最终返回false
            StepVerifier.create(service.hasPermission("user-1", "tenant-1", "ORDER", "1", "READ", Map.of()))
                    .expectNext(false)
                    .verifyComplete();
        }

        @Test
        @DisplayName("无缓存，无条件权限直接通过")
        void testHasPermission_NoConditionPermission() {
            when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(false));
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.set(anyString(), anyString(), any())).thenReturn(Mono.just(true));

            // resourceType/action均为*的全局权限condition为null，属于无条件权限，直接通过
            StepVerifier.create(service.hasPermission("admin-1", "tenant-1", "*", "res-1", "*", Map.of()))
                    .expectNext(true)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("getUserPermissions() 方法测试")
    class GetUserPermissionsTest {

        @Test
        @DisplayName("返回示例权限列表")
        void testGetUserPermissions() {
            StepVerifier.create(service.getUserPermissions("user-1", "tenant-1"))
                    .expectNextCount(4)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("addPermission() / removePermission() 方法测试")
    class AddRemovePermissionTest {

        @Test
        @DisplayName("添加权限规则返回空完成")
        void testAddPermission() {
            ResourcePermission permission = ResourcePermission.builder()
                    .resourceType("ORDER")
                    .action("READ")
                    .build();

            StepVerifier.create(service.addPermission(permission)).verifyComplete();
        }

        @Test
        @DisplayName("删除权限规则返回空完成")
        void testRemovePermission() {
            StepVerifier.create(service.removePermission("ORDER", "1", "READ")).verifyComplete();
        }
    }

    @Nested
    @DisplayName("clearUserPermissionCache() 方法测试")
    class ClearUserPermissionCacheTest {

        @Test
        @DisplayName("清除用户权限缓存")
        void testClearUserPermissionCache() {
            when(redisTemplate.keys(anyString())).thenReturn(Flux.just("key1", "key2"));
            when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));

            StepVerifier.create(service.clearUserPermissionCache("user-1")).verifyComplete();

            verify(redisTemplate).delete("key1");
            verify(redisTemplate).delete("key2");
        }

        @Test
        @DisplayName("无缓存Key时直接完成")
        void testClearUserPermissionCache_NoKeys() {
            when(redisTemplate.keys(anyString())).thenReturn(Flux.empty());

            StepVerifier.create(service.clearUserPermissionCache("user-1")).verifyComplete();

            verify(redisTemplate, never()).delete(anyString());
        }
    }

    @Nested
    @DisplayName("batchCheckPermissions() 方法测试")
    class BatchCheckPermissionsTest {

        @Test
        @DisplayName("批量检查多个权限")
        void testBatchCheckPermissions() {
            when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(false));
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.set(anyString(), anyString(), any())).thenReturn(Mono.just(true));

            List<ResourcePermissionService.PermissionCheck> checks = List.of(
                    new ResourcePermissionService.PermissionCheck("ORDER", "1", "READ"),
                    new ResourcePermissionService.PermissionCheck("ORDER", "2", "WRITE"));

            StepVerifier.create(service.batchCheckPermissions("admin-1", "tenant-1", checks, Map.of()))
                    .assertNext(result ->
                            org.assertj.core.api.Assertions.assertThat(result).hasSize(2))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("getAccessibleResources() 方法测试")
    class GetAccessibleResourcesTest {

        @Test
        @DisplayName("过滤出可访问的资源ID")
        void testGetAccessibleResources() {
            when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(false));
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.set(anyString(), anyString(), any())).thenReturn(Mono.just(true));

            StepVerifier.create(service.getAccessibleResources(
                            "admin-1", "tenant-1", "*", "*", List.of("1", "2", "3"), Map.of()))
                    .expectNextCount(3)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("PermissionCheck.getKey() 方法测试")
    class PermissionCheckTest {

        @Test
        @DisplayName("生成正确的Key格式")
        void testGetKey() {
            ResourcePermissionService.PermissionCheck check =
                    new ResourcePermissionService.PermissionCheck("ORDER", "1", "READ");

            org.assertj.core.api.Assertions.assertThat(check.getKey()).isEqualTo("ORDER:1:READ");
        }
    }
}
