package com.apex.scaffold.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.apex.scaffold.domain.model.entity.TenantEntity;
import com.apex.scaffold.domain.model.entity.TenantPackageEntity;
import com.apex.scaffold.domain.repository.TenantPackageRepository;
import com.apex.scaffold.domain.repository.TenantRepository;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 租户套餐服务测试（方案批次 B3）。
 *
 * <p>覆盖：平台租户不裁剪、未绑定套餐不裁剪、激活套餐裁剪、停用/未知套餐回退全量、
 * PackageScope 纯语义（菜单/权限码判定 + 父链补全）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("租户套餐服务测试")
class TenantPackageServiceTest {

    private final TenantPackageRepository packageRepository = mock(TenantPackageRepository.class);
    private final TenantRepository tenantRepository = mock(TenantRepository.class);

    private final TenantPackageService service = new TenantPackageService(packageRepository, tenantRepository);

    private TenantPackageEntity activePackage() {
        return TenantPackageEntity.builder()
                .id(1L)
                .packageCode("basic")
                .packageName("基础套餐")
                .menuIds(Set.of(10L, 11L))
                .permissionCodes(Set.of("user:read"))
                .status(1)
                .build();
    }

    private TenantEntity tenantWithPackage(String code, String packageCode) {
        TenantEntity tenant = new TenantEntity();
        tenant.setTenantCode(code);
        tenant.setPackageCode(packageCode);
        return tenant;
    }

    @Test
    @DisplayName("平台默认租户不裁剪")
    void platformTenantUnbounded() {
        StepVerifier.create(service.resolveScope("default", "basic"))
                .assertNext(scope -> assertThat(scope.unbounded()).isTrue())
                .verifyComplete();
    }

    @Test
    @DisplayName("未绑定套餐的租户不裁剪（B4 开通引导强制绑定后收紧）")
    void unboundTenantUnbounded() {
        StepVerifier.create(service.resolveScope("tenant-a", null))
                .assertNext(scope -> assertThat(scope.unbounded()).isTrue())
                .verifyComplete();
    }

    @Test
    @DisplayName("激活套餐按 menu_ids/permission_codes 裁剪")
    void activePackageScopes() {
        when(tenantRepository.findByCode("tenant-a")).thenReturn(Mono.just(tenantWithPackage("tenant-a", "basic")));
        when(packageRepository.findByCode("basic")).thenReturn(Mono.just(activePackage()));

        StepVerifier.create(service.resolveScope("tenant-a"))
                .assertNext(scope -> {
                    assertThat(scope.unbounded()).isFalse();
                    assertThat(scope.allowsMenu(10L)).isTrue();
                    assertThat(scope.allowsMenu(99L)).isFalse();
                    assertThat(scope.allowsPermission("user:read")).isTrue();
                    assertThat(scope.allowsPermission("user:write")).isFalse();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("停用或未知套餐回退全量（fail-open 依据：无裁剪依据不裁剪）")
    void inactiveOrUnknownPackageFallsBackToUnbounded() {
        when(tenantRepository.findByCode("tenant-a")).thenReturn(Mono.just(tenantWithPackage("tenant-a", "basic")));
        TenantPackageEntity disabled = activePackage();
        disabled.setStatus(0);
        when(packageRepository.findByCode("basic")).thenReturn(Mono.just(disabled));
        StepVerifier.create(service.resolveScope("tenant-a"))
                .assertNext(scope -> assertThat(scope.unbounded()).isTrue())
                .verifyComplete();

        when(packageRepository.findByCode("ghost")).thenReturn(Mono.empty());
        StepVerifier.create(service.resolveScope("tenant-b", "ghost"))
                .assertNext(scope -> assertThat(scope.unbounded()).isTrue())
                .verifyComplete();
    }

    @Test
    @DisplayName("PackageScope：空集合语义为全量放行")
    void emptySetMeansUnboundedWithinScope() {
        var scope = new TenantPackageService.PackageScope(false, Set.of(), Set.of(), Set.of());
        assertThat(scope.allowsMenu(7L)).isTrue();
        assertThat(scope.allowsPermission("any:code")).isTrue();
    }

    @Test
    @DisplayName("withParentChain：勾选子菜单自动补全祖先链")
    void parentChainCompletion() {
        var pkg = TenantPackageEntity.builder().menuIds(Set.of(30L)).build();
        // 30 → 20 → 10（根）
        Set<Long> completed = pkg.withParentChain(id -> switch ((int) id) {
            case 30 -> 20L;
            case 20 -> 10L;
            default -> null;
        });
        assertThat(completed).containsExactlyInAnyOrder(30L, 20L, 10L);
    }
}
