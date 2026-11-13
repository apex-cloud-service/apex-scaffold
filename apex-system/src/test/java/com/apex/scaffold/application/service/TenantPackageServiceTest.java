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
 * <p>覆盖平台租户显式全量、非平台租户套餐缺失、未知、停用与配置损坏时的 fail-closed
 * 行为，以及 {@code PackageScope} 的菜单、权限码和能力判定语义。</p>
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.1
 */
@DisplayName("租户套餐服务测试")
class TenantPackageServiceTest {

    private final TenantPackageRepository packageRepository = mock(TenantPackageRepository.class);
    private final TenantRepository tenantRepository = mock(TenantRepository.class);

    private final TenantPackageService service = new TenantPackageService(packageRepository, tenantRepository);

    /**
     * 构造启用的有效套餐。
     *
     * @return 基础套餐实体
     */
    private TenantPackageEntity activePackage() {
        return TenantPackageEntity.builder()
                .id(1L)
                .packageCode("basic")
                .packageName("基础套餐")
                .menuIds(Set.of(10L, 11L))
                .permissionCodes(Set.of("user:read"))
                .features(Set.of("workflow"))
                .status(1)
                .build();
    }

    /**
     * 构造绑定指定套餐的租户。
     *
     * @param code 租户编码
     * @param packageCode 套餐编码
     * @return 租户实体
     */
    private TenantEntity tenantWithPackage(String code, String packageCode) {
        TenantEntity tenant = new TenantEntity();
        tenant.setTenantCode(code);
        tenant.setPackageCode(packageCode);
        return tenant;
    }

    /**
     * 验证平台默认租户显式获得全量范围。
     */
    @Test
    @DisplayName("平台默认租户显式全量")
    void platformTenantUnbounded() {
        StepVerifier.create(service.resolveScope("default", "basic"))
                .assertNext(scope -> {
                    assertThat(scope.unbounded()).isTrue();
                    assertThat(scope.denied()).isFalse();
                })
                .verifyComplete();
    }

    /**
     * 验证非平台租户缺少套餐时拒绝全部授权。
     */
    @Test
    @DisplayName("非平台租户未绑定套餐时拒绝全部授权")
    void unboundTenantDenied() {
        StepVerifier.create(service.resolveScope("tenant-a", null))
                .assertNext(scope -> {
                    assertThat(scope.unbounded()).isFalse();
                    assertThat(scope.denied()).isTrue();
                })
                .verifyComplete();
    }

    /**
     * 验证启用套餐按菜单、权限码和能力集合裁剪。
     */
    @Test
    @DisplayName("启用套餐按 menu_ids/permission_codes/features 裁剪")
    void activePackageScopes() {
        when(tenantRepository.findByCode("tenant-a")).thenReturn(Mono.just(tenantWithPackage("tenant-a", "basic")));
        when(packageRepository.findByCode("basic")).thenReturn(Mono.just(activePackage()));

        StepVerifier.create(service.resolveScope("tenant-a"))
                .assertNext(scope -> {
                    assertThat(scope.unbounded()).isFalse();
                    assertThat(scope.denied()).isFalse();
                    assertThat(scope.allowsMenu(10L)).isTrue();
                    assertThat(scope.allowsMenu(99L)).isFalse();
                    assertThat(scope.allowsPermission("user:read")).isTrue();
                    assertThat(scope.allowsPermission("user:write")).isFalse();
                    assertThat(scope.allowsCapability("workflow")).isTrue();
                    assertThat(scope.allowsCapability("iot-device")).isFalse();
                })
                .verifyComplete();
    }

    /**
     * 验证停用套餐拒绝全部授权。
     */
    @Test
    @DisplayName("停用套餐拒绝全部授权")
    void inactivePackageDenied() {
        when(tenantRepository.findByCode("tenant-a")).thenReturn(Mono.just(tenantWithPackage("tenant-a", "basic")));
        TenantPackageEntity disabled = activePackage();
        disabled.setStatus(0);
        when(packageRepository.findByCode("basic")).thenReturn(Mono.just(disabled));

        StepVerifier.create(service.resolveScope("tenant-a"))
                .assertNext(scope -> assertThat(scope.denied()).isTrue())
                .verifyComplete();
    }

    /**
     * 验证未知套餐拒绝全部授权。
     */
    @Test
    @DisplayName("未知套餐拒绝全部授权")
    void unknownPackageDenied() {
        when(packageRepository.findByCode("ghost")).thenReturn(Mono.empty());

        StepVerifier.create(service.resolveScope("tenant-b", "ghost"))
                .assertNext(scope -> assertThat(scope.denied()).isTrue())
                .verifyComplete();
    }

    /**
     * 验证租户不存在时拒绝全部授权。
     */
    @Test
    @DisplayName("租户不存在时拒绝全部授权")
    void missingTenantDenied() {
        when(tenantRepository.findByCode("tenant-x")).thenReturn(Mono.empty());

        StepVerifier.create(service.resolveScope("tenant-x"))
                .assertNext(scope -> assertThat(scope.denied()).isTrue())
                .verifyComplete();
    }

    /**
     * 验证配置损坏的套餐拒绝全部授权。
     */
    @Test
    @DisplayName("套餐配置损坏时拒绝全部授权")
    void invalidConfigurationDenied() {
        when(tenantRepository.findByCode("tenant-a")).thenReturn(Mono.just(tenantWithPackage("tenant-a", "basic")));
        TenantPackageEntity invalid = activePackage();
        invalid.setConfigurationInvalid(true);
        when(packageRepository.findByCode("basic")).thenReturn(Mono.just(invalid));

        StepVerifier.create(service.resolveScope("tenant-a"))
                .assertNext(scope -> assertThat(scope.denied()).isTrue())
                .verifyComplete();
    }

    /**
     * 验证空菜单与权限码集合在有效套餐内保持全量语义。
     */
    @Test
    @DisplayName("PackageScope：空集合语义为对应维度全量放行")
    void emptySetMeansUnboundedWithinScope() {
        var scope = new TenantPackageService.PackageScope(false, false, Set.of(), Set.of(), Set.of());
        assertThat(scope.allowsMenu(7L)).isTrue();
        assertThat(scope.allowsPermission("any:code")).isTrue();
    }

    /**
     * 验证勾选子菜单时自动补全祖先链。
     */
    @Test
    @DisplayName("withParentChain：勾选子菜单自动补全祖先链")
    void parentChainCompletion() {
        var pkg = TenantPackageEntity.builder().menuIds(Set.of(30L)).build();
        Set<Long> completed = pkg.withParentChain(id -> switch ((int) id) {
            case 30 -> 20L;
            case 20 -> 10L;
            default -> null;
        });
        assertThat(completed).containsExactlyInAnyOrder(30L, 20L, 10L);
    }
}
