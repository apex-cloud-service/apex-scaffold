package com.apex.scaffold.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.auth.domain.model.entity.PermissionEntity;
import com.apex.scaffold.auth.domain.repository.PermissionRepository;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
 * 权限服务单元测试
 *
 * @author Apex Scaffold Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("权限服务单元测试")
class PermissionServiceTest {

    @Mock
    private PermissionRepository permissionRepository;

    @InjectMocks
    private PermissionService permissionService;

    private PermissionEntity mockPermission;

    @BeforeEach
    void setUp() {
        mockPermission = PermissionEntity.builder()
                .id(1L)
                .tenantId("test-tenant")
                .code("user:read")
                .name("查看用户")
                .description("查看用户信息的权限")
                .resourceType("API")
                .resourceIdentifier("/api/users")
                .action("READ")
                .enabled(true)
                .deleted(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("createPermission() 方法测试")
    class CreatePermissionTest {

        @Test
        @DisplayName("权限编码不存在时创建成功")
        void testCreatePermissionSuccess() {
            PermissionEntity newPermission = PermissionEntity.builder()
                    .code("user:write")
                    .name("编辑用户")
                    .tenantId("test-tenant")
                    .build();

            when(permissionRepository.existsByCode("user:write", "test-tenant")).thenReturn(Mono.just(false));
            when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(invocation -> {
                PermissionEntity permission = invocation.getArgument(0);
                permission.setId(2L);
                return Mono.just(permission);
            });

            Mono<PermissionEntity> result = permissionService.createPermission(newPermission);

            StepVerifier.create(result)
                    .assertNext(permission -> {
                        assertThat(permission.getId()).isEqualTo(2L);
                        assertThat(permission.getCode()).isEqualTo("user:write");
                        assertThat(permission.getName()).isEqualTo("编辑用户");
                        assertThat(permission.getCreatedAt()).isNotNull();
                        assertThat(permission.getUpdatedAt()).isNotNull();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("权限编码已存在时创建失败")
        void testCreatePermissionDuplicateCode() {
            PermissionEntity newPermission = PermissionEntity.builder()
                    .code("user:read")
                    .name("查看用户")
                    .tenantId("test-tenant")
                    .build();

            when(permissionRepository.existsByCode("user:read", "test-tenant")).thenReturn(Mono.just(true));

            Mono<PermissionEntity> result = permissionService.createPermission(newPermission);

            StepVerifier.create(result)
                    .expectErrorMatches(ex -> ex instanceof BusinessException
                            && ((BusinessException) ex).getCode().equals(ResultCode.PARAM_ERROR.getCode())
                            && ex.getMessage().contains("权限编码已存在"))
                    .verify();
        }
    }

    @Nested
    @DisplayName("getPermissionById() 方法测试")
    class GetPermissionByIdTest {

        @Test
        @DisplayName("根据ID查询权限成功")
        void testGetPermissionByIdSuccess() {
            when(permissionRepository.findById(1L)).thenReturn(Mono.just(mockPermission));

            Mono<PermissionEntity> result = permissionService.getPermissionById(1L);

            StepVerifier.create(result)
                    .assertNext(permission -> {
                        assertThat(permission.getId()).isEqualTo(1L);
                        assertThat(permission.getCode()).isEqualTo("user:read");
                        assertThat(permission.getName()).isEqualTo("查看用户");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("权限不存在时抛出异常")
        void testGetPermissionByIdNotFound() {
            when(permissionRepository.findById(999L)).thenReturn(Mono.empty());

            Mono<PermissionEntity> result = permissionService.getPermissionById(999L);

            StepVerifier.create(result)
                    .expectErrorMatches(ex -> ex instanceof BusinessException
                            && ((BusinessException) ex).getCode().equals(ResultCode.NOT_FOUND.getCode())
                            && ex.getMessage().contains("权限不存在"))
                    .verify();
        }
    }

    @Nested
    @DisplayName("getPermissionByCode() 方法测试")
    class GetPermissionByCodeTest {

        @Test
        @DisplayName("根据编码查询权限成功")
        void testGetPermissionByCodeSuccess() {
            when(permissionRepository.findByCode("user:read", "test-tenant")).thenReturn(Mono.just(mockPermission));

            Mono<PermissionEntity> result = permissionService.getPermissionByCode("user:read", "test-tenant");

            StepVerifier.create(result)
                    .assertNext(permission -> {
                        assertThat(permission.getCode()).isEqualTo("user:read");
                        assertThat(permission.getTenantId()).isEqualTo("test-tenant");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("权限编码不存在时抛出异常")
        void testGetPermissionByCodeNotFound() {
            when(permissionRepository.findByCode("non:existent", "test-tenant")).thenReturn(Mono.empty());

            Mono<PermissionEntity> result = permissionService.getPermissionByCode("non:existent", "test-tenant");

            StepVerifier.create(result)
                    .expectErrorMatches(ex -> ex instanceof BusinessException
                            && ((BusinessException) ex).getCode().equals(ResultCode.NOT_FOUND.getCode()))
                    .verify();
        }
    }

    @Nested
    @DisplayName("getPermissionsByTenantId() 方法测试")
    class GetPermissionsByTenantIdTest {

        @Test
        @DisplayName("获取租户权限列表成功")
        void testGetPermissionsByTenantIdSuccess() {
            PermissionEntity permission2 = PermissionEntity.builder()
                    .id(2L)
                    .code("user:write")
                    .name("编辑用户")
                    .tenantId("test-tenant")
                    .build();

            when(permissionRepository.findByTenantId("test-tenant")).thenReturn(Flux.just(mockPermission, permission2));

            Flux<PermissionEntity> result = permissionService.getPermissionsByTenantId("test-tenant");

            StepVerifier.create(result)
                    .expectNext(mockPermission)
                    .expectNext(permission2)
                    .verifyComplete();
        }

        @Test
        @DisplayName("租户无权限时返回空流")
        void testGetPermissionsByTenantIdEmpty() {
            when(permissionRepository.findByTenantId("empty-tenant")).thenReturn(Flux.empty());

            Flux<PermissionEntity> result = permissionService.getPermissionsByTenantId("empty-tenant");

            StepVerifier.create(result).verifyComplete();
        }
    }

    @Nested
    @DisplayName("getEnabledPermissions() 方法测试")
    class GetEnabledPermissionsTest {

        @Test
        @DisplayName("获取已启用权限列表成功")
        void testGetEnabledPermissionsSuccess() {
            when(permissionRepository.findEnabledByTenantId("test-tenant")).thenReturn(Flux.just(mockPermission));

            Flux<PermissionEntity> result = permissionService.getEnabledPermissions("test-tenant");

            StepVerifier.create(result).expectNext(mockPermission).verifyComplete();
        }
    }

    @Nested
    @DisplayName("getPermissionsByResourceType() 方法测试")
    class GetPermissionsByResourceTypeTest {

        @Test
        @DisplayName("根据资源类型获取权限成功")
        void testGetPermissionsByResourceTypeSuccess() {
            when(permissionRepository.findByResourceType("API", "test-tenant")).thenReturn(Flux.just(mockPermission));

            Flux<PermissionEntity> result = permissionService.getPermissionsByResourceType("API", "test-tenant");

            StepVerifier.create(result).expectNext(mockPermission).verifyComplete();
        }
    }

    @Nested
    @DisplayName("getPermissionsByRoleId() 方法测试")
    class GetPermissionsByRoleIdTest {

        @Test
        @DisplayName("根据角色ID获取权限成功")
        void testGetPermissionsByRoleIdSuccess() {
            when(permissionRepository.findByRoleId(1L)).thenReturn(Flux.just(mockPermission));

            Flux<PermissionEntity> result = permissionService.getPermissionsByRoleId(1L);

            StepVerifier.create(result).expectNext(mockPermission).verifyComplete();
        }
    }

    @Nested
    @DisplayName("getPermissionsByIds() 方法测试")
    class GetPermissionsByIdsTest {

        @Test
        @DisplayName("批量获取权限成功")
        void testGetPermissionsByIdsSuccess() {
            Set<Long> ids = new HashSet<>(Arrays.asList(1L, 2L));
            PermissionEntity permission2 =
                    PermissionEntity.builder().id(2L).code("user:write").build();

            when(permissionRepository.findByIds(ids)).thenReturn(Flux.just(mockPermission, permission2));

            Flux<PermissionEntity> result = permissionService.getPermissionsByIds(ids);

            StepVerifier.create(result)
                    .expectNext(mockPermission)
                    .expectNext(permission2)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("updatePermission() 方法测试")
    class UpdatePermissionTest {

        @Test
        @DisplayName("更新权限成功")
        void testUpdatePermissionSuccess() {
            PermissionEntity updatedPermission = PermissionEntity.builder()
                    .id(1L)
                    .code("user:read")
                    .name("查看用户（更新）")
                    .description("更新后的描述")
                    .build();

            when(permissionRepository.findById(1L)).thenReturn(Mono.just(mockPermission));
            when(permissionRepository.update(any(PermissionEntity.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

            Mono<PermissionEntity> result = permissionService.updatePermission(updatedPermission);

            StepVerifier.create(result)
                    .assertNext(permission -> {
                        assertThat(permission.getId()).isEqualTo(1L);
                        assertThat(permission.getName()).isEqualTo("查看用户（更新）");
                        assertThat(permission.getUpdatedAt()).isNotNull();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("更新不存在的权限时抛出异常")
        void testUpdatePermissionNotFound() {
            PermissionEntity updatedPermission =
                    PermissionEntity.builder().id(999L).name("不存在的权限").build();

            when(permissionRepository.findById(999L)).thenReturn(Mono.empty());

            Mono<PermissionEntity> result = permissionService.updatePermission(updatedPermission);

            StepVerifier.create(result)
                    .expectErrorMatches(ex -> ex instanceof BusinessException
                            && ((BusinessException) ex).getCode().equals(ResultCode.NOT_FOUND.getCode()))
                    .verify();
        }
    }

    @Nested
    @DisplayName("deletePermission() 方法测试")
    class DeletePermissionTest {

        @Test
        @DisplayName("删除权限成功")
        void testDeletePermissionSuccess() {
            when(permissionRepository.deleteById(1L)).thenReturn(Mono.just(true));

            Mono<Void> result = permissionService.deletePermission(1L);

            StepVerifier.create(result).verifyComplete();
        }

        @Test
        @DisplayName("删除失败时抛出异常")
        void testDeletePermissionFailure() {
            when(permissionRepository.deleteById(1L)).thenReturn(Mono.just(false));

            Mono<Void> result = permissionService.deletePermission(1L);

            StepVerifier.create(result)
                    .expectErrorMatches(ex -> ex instanceof BusinessException
                            && ((BusinessException) ex).getCode().equals(ResultCode.ERROR.getCode())
                            && ex.getMessage().contains("权限删除失败"))
                    .verify();
        }
    }

    @Nested
    @DisplayName("batchCreatePermissions() 方法测试")
    class BatchCreatePermissionsTest {

        @Test
        @DisplayName("批量创建权限成功")
        void testBatchCreatePermissionsSuccess() {
            PermissionEntity permission1 = PermissionEntity.builder()
                    .code("user:read")
                    .name("查看用户")
                    .tenantId("test-tenant")
                    .build();

            PermissionEntity permission2 = PermissionEntity.builder()
                    .code("user:write")
                    .name("编辑用户")
                    .tenantId("test-tenant")
                    .build();

            List<PermissionEntity> permissions = Arrays.asList(permission1, permission2);

            when(permissionRepository.existsByCode("user:read", "test-tenant")).thenReturn(Mono.just(false));
            when(permissionRepository.existsByCode("user:write", "test-tenant")).thenReturn(Mono.just(false));
            when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(invocation -> {
                PermissionEntity p = invocation.getArgument(0);
                p.setId(System.currentTimeMillis());
                return Mono.just(p);
            });

            Flux<PermissionEntity> result = permissionService.batchCreatePermissions(permissions);

            StepVerifier.create(result).expectNextCount(2).verifyComplete();
        }
    }

    @Nested
    @DisplayName("existsByCode() 方法测试")
    class ExistsByCodeTest {

        @Test
        @DisplayName("权限编码存在时返回true")
        void testExistsByCodeTrue() {
            when(permissionRepository.existsByCode("user:read", "test-tenant")).thenReturn(Mono.just(true));

            Mono<Boolean> result = permissionService.existsByCode("user:read", "test-tenant");

            StepVerifier.create(result).expectNext(true).verifyComplete();
        }

        @Test
        @DisplayName("权限编码不存在时返回false")
        void testExistsByCodeFalse() {
            when(permissionRepository.existsByCode("non:existent", "test-tenant"))
                    .thenReturn(Mono.just(false));

            Mono<Boolean> result = permissionService.existsByCode("non:existent", "test-tenant");

            StepVerifier.create(result).expectNext(false).verifyComplete();
        }
    }

    @Nested
    @DisplayName("listPermissions() 方法测试")
    class ListPermissionsTest {

        @Test
        @DisplayName("列出所有权限成功")
        void testListPermissionsSuccess() {
            PermissionEntity permission2 =
                    PermissionEntity.builder().id(2L).code("user:write").build();

            when(permissionRepository.findAll()).thenReturn(Flux.just(mockPermission, permission2));

            Flux<PermissionEntity> result = permissionService.listPermissions();

            StepVerifier.create(result)
                    .expectNext(mockPermission)
                    .expectNext(permission2)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("getPermissionsByResource() 方法测试")
    class GetPermissionsByResourceTest {

        @Test
        @DisplayName("根据资源获取权限成功")
        void testGetPermissionsByResourceSuccess() {
            when(permissionRepository.findByResource("/api/users")).thenReturn(Flux.just(mockPermission));

            Flux<PermissionEntity> result = permissionService.getPermissionsByResource("/api/users");

            StepVerifier.create(result).expectNext(mockPermission).verifyComplete();
        }
    }

    @Test
    void emptyBatchDoesNotAccessRepository() {
        StepVerifier.create(permissionService.batchCreatePermissions(List.of())).verifyComplete();
        org.mockito.Mockito.verifyNoInteractions(permissionRepository);
    }

    @Test
    void duplicateBatchEntryDoesNotSave() {
        when(permissionRepository.existsByCode(mockPermission.getCode(), "test-tenant"))
                .thenReturn(Mono.just(true));
        StepVerifier.create(permissionService.batchCreatePermissions(List.of(mockPermission)))
                .expectErrorSatisfies(error ->
                        assertThat(((BusinessException) error).getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode()))
                .verify();
        org.mockito.Mockito.verify(permissionRepository, org.mockito.Mockito.never())
                .save(any());
    }

    @Test
    void failedExistenceCheckDoesNotSave() {
        IllegalStateException failure = new IllegalStateException("repository unavailable");
        when(permissionRepository.existsByCode(mockPermission.getCode(), "test-tenant"))
                .thenReturn(Mono.error(failure));
        StepVerifier.create(permissionService.createPermission(mockPermission))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
                .verify();
        org.mockito.Mockito.verify(permissionRepository, org.mockito.Mockito.never())
                .save(any());
    }

    @Test
    void updateFailureIsPropagated() {
        IllegalStateException failure = new IllegalStateException("update failed");
        when(permissionRepository.findById(1L)).thenReturn(Mono.just(mockPermission));
        when(permissionRepository.update(mockPermission)).thenReturn(Mono.error(failure));
        StepVerifier.create(permissionService.updatePermission(mockPermission))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
                .verify();
    }

    @Test
    void saveFailureIsPropagatedWithoutRetry() {
        IllegalStateException failure = new IllegalStateException("save failed");
        when(permissionRepository.existsByCode("user:read", "test-tenant")).thenReturn(Mono.just(false));
        when(permissionRepository.save(mockPermission)).thenReturn(Mono.error(failure));

        StepVerifier.create(permissionService.createPermission(mockPermission))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
                .verify();

        verify(permissionRepository).save(mockPermission);
    }

    @Test
    void failedUpdateLookupDoesNotMutateOrPersistPermission() {
        LocalDateTime updatedAt = mockPermission.getUpdatedAt();
        IllegalStateException failure = new IllegalStateException("lookup failed");
        when(permissionRepository.findById(1L)).thenReturn(Mono.error(failure));

        StepVerifier.create(permissionService.updatePermission(mockPermission))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
                .verify();

        assertThat(mockPermission.getUpdatedAt()).isEqualTo(updatedAt);
        verify(permissionRepository, never()).update(any());
    }

    @Test
    void missingPermissionIsNotUpdated() {
        when(permissionRepository.findById(1L)).thenReturn(Mono.empty());

        StepVerifier.create(permissionService.updatePermission(mockPermission))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) error).getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode());
                })
                .verify();

        verify(permissionRepository, never()).update(any());
    }

    @Test
    void deleteRepositoryErrorIsNotReplacedWithBusinessError() {
        IllegalStateException failure = new IllegalStateException("delete failed");
        when(permissionRepository.deleteById(1L)).thenReturn(Mono.error(failure));

        StepVerifier.create(permissionService.deletePermission(1L))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
                .verify();
    }

    @Test
    void batchEmitsSavedPermissionThenPropagatesNextSaveFailure() {
        PermissionEntity second = PermissionEntity.builder()
                .code("user:write")
                .tenantId("test-tenant")
                .build();
        IllegalStateException failure = new IllegalStateException("second save failed");
        when(permissionRepository.existsByCode("user:read", "test-tenant")).thenReturn(Mono.just(false));
        when(permissionRepository.existsByCode("user:write", "test-tenant")).thenReturn(Mono.just(false));
        when(permissionRepository.save(mockPermission)).thenReturn(Mono.just(mockPermission));
        when(permissionRepository.save(second)).thenReturn(Mono.error(failure));

        StepVerifier.create(permissionService.batchCreatePermissions(List.of(mockPermission, second)))
                .expectNext(mockPermission)
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
                .verify();

        verify(permissionRepository).save(mockPermission);
        verify(permissionRepository).save(second);
    }
}
