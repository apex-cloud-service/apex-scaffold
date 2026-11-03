package com.apex.scaffold.application.service;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.model.entity.TenantPackageEntity;
import com.apex.scaffold.domain.repository.TenantPackageRepository;
import com.apex.scaffold.domain.repository.TenantRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 租户套餐服务（统一认证收口 / 方案批次 B3）。
 *
 * <p>核心职责：为菜单树裁剪与有效权限码交集提供<b>当前租户的套餐范围</b>；
 * 平台默认租户（{@code default}）不裁剪。套餐 CRUD 供 B6 管理接口消费
 * （权限码 {@code tenant:package:*} 随 B6 种子批交付）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantPackageService {

    private final TenantPackageRepository tenantPackageRepository;

    private final TenantRepository tenantRepository;

    /**
     * 当前租户的套餐裁剪范围。
     *
     * @param tenantCode        当前租户编码
     * @param packageCode       租户绑定的套餐编码（可空 = 无裁剪）
     * @return 裁剪范围（unbounded=true 表示不裁剪）
     */
    public Mono<PackageScope> resolveScope(String tenantCode, String packageCode) {
        // 平台默认租户不裁剪
        if (TenantPackageEntity.PLATFORM_TENANT_CODE.equals(tenantCode)) {
            return Mono.just(PackageScope.full());
        }
        if (packageCode == null || packageCode.isBlank()) {
            // 未绑定套餐的租户：无裁剪依据，保持全量（B4 开通引导强制绑定后收紧）
            return Mono.just(PackageScope.full());
        }
        return tenantPackageRepository
                .findByCode(packageCode)
                .map(pkg -> pkg.isActive() ? PackageScope.of(pkg) : PackageScope.full())
                .defaultIfEmpty(PackageScope.full())
                .doOnNext(scope -> {
                    if (!scope.unbounded()) {
                        log.debug(
                                "租户套餐范围生效: tenant={}, package={}, menus={}, permissions={}",
                                tenantCode,
                                packageCode,
                                scope.menuIds().size(),
                                scope.permissionCodes().size());
                    }
                });
    }

    /**
     * 解析当前租户的套餐范围（先查租户绑定再取套餐）。
     *
     * @param tenantCode 当前租户编码
     * @return 裁剪范围
     */
    public Mono<PackageScope> resolveScope(String tenantCode) {
        if (TenantPackageEntity.PLATFORM_TENANT_CODE.equals(tenantCode)) {
            return Mono.just(PackageScope.full());
        }
        return tenantRepository
                .findByCode(tenantCode)
                .flatMap(tenant -> resolveScope(tenantCode, tenant.getPackageCode()))
                .defaultIfEmpty(PackageScope.full());
    }

    /**
     * 查询全部有效套餐（B6 管理接口消费）。
     *
     * @return 套餐列表
     */
    public Flux<TenantPackageEntity> listPackages() {
        return tenantPackageRepository.findAll();
    }

    /**
     * 查询租户能力订阅快照（D4，内部端点 {@code GET /internal/v1/tenants/{code}/capabilities} 消费）。
     *
     * <p>未知租户以 {@code exists=false} 数据化返回（fail-closed，消费方按未订阅处理并缓存负结果）；
     * 判定口径与 {@link PackageScope#allowsCapability(String)} 一致（unbounded 或空 features = 全量订阅）。
     *
     * @param tenantCode 租户编码
     * @return 能力订阅快照
     */
    public Mono<CapabilitySnapshot> getTenantCapabilities(String tenantCode) {
        return tenantRepository
                .findByCode(tenantCode)
                .flatMap(tenant -> resolveScope(tenantCode, tenant.getPackageCode())
                        .map(scope -> new CapabilitySnapshot(tenantCode, true, scope.unbounded(), scope.features())))
                .defaultIfEmpty(new CapabilitySnapshot(tenantCode, false, false, java.util.Set.of()));
    }

    /**
     * 租户能力订阅快照（D4）。
     *
     * @param tenantCode 租户编码
     * @param exists 租户是否存在（未知租户 fail-closed）
     * @param unbounded 是否不限量租户（平台租户/未绑定套餐）
     * @param features 已订阅特性编码（空集合 = 全量订阅语义，与套餐语义一致）
     */
    @Schema(name = "CapabilitySnapshot", description = "租户能力订阅快照（内部端点契约）")
    public record CapabilitySnapshot(
            @Schema(description = "租户编码", example = "T001") String tenantCode,
            @Schema(description = "租户是否存在（未知租户 fail-closed 返回 false）") boolean exists,
            @Schema(description = "是否不限量租户（平台租户/未绑定套餐）") boolean unbounded,
            @Schema(description = "已订阅特性编码（空集合 = 全量订阅语义）") java.util.Set<String> features) {}

    /**
     * 按编码查询套餐。
     *
     * @param packageCode 套餐编码
     * @return 套餐实体
     */
    public Mono<TenantPackageEntity> getByCode(String packageCode) {
        return tenantPackageRepository
                .findByCode(packageCode)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "套餐不存在")));
    }

    /**
     * 创建套餐（编码唯一冲突时拒绝）。
     *
     * @param entity 套餐实体
     * @return 已创建实体
     */
    public Mono<TenantPackageEntity> createPackage(TenantPackageEntity entity) {
        return tenantPackageRepository
                .findByCode(entity.getPackageCode())
                .flatMap(existing ->
                        Mono.<TenantPackageEntity>error(new BusinessException(ResultCode.PARAM_ERROR, "套餐编码已存在")))
                .switchIfEmpty(Mono.defer(() -> tenantPackageRepository.save(entity)));
    }

    /**
     * 更新套餐（按编码定位；换套餐后既有用户权限随 ≤30s 快照/缓存口径收敛）。
     *
     * @param entity 套餐实体（含编码）
     * @return 已更新实体
     */
    public Mono<TenantPackageEntity> updatePackage(TenantPackageEntity entity) {
        return tenantPackageRepository
                .findByCode(entity.getPackageCode())
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "套餐不存在")))
                .flatMap(existing -> {
                    entity.setId(existing.getId());
                    entity.setVersion(existing.getVersion());
                    return tenantPackageRepository.save(entity);
                });
    }

    /**
     * 套餐裁剪范围值对象。
     *
     * @param unbounded       是否不裁剪（平台租户/未绑定/套餐停用）
     * @param menuIds         可用菜单 ID 集合（含父链补全由调用方按需执行）
     * @param permissionCodes 可用权限码集合
     */
    public record PackageScope(
            boolean unbounded,
            java.util.Set<Long> menuIds,
            java.util.Set<String> permissionCodes,
            java.util.Set<String> features) {

        /** 全量范围（不裁剪、订阅全部能力）。 */
        public static PackageScope full() {
            return new PackageScope(true, java.util.Set.of(), java.util.Set.of(), java.util.Set.of());
        }

        /** 依套餐实体构造范围。 */
        public static PackageScope of(TenantPackageEntity pkg) {
            return new PackageScope(
                    false,
                    pkg.getMenuIds(),
                    pkg.getPermissionCodes(),
                    pkg.getFeatures() == null ? java.util.Set.of() : pkg.getFeatures());
        }

        /**
         * 能力是否已订阅（空 features = 全量订阅，与套餐语义一致）。
         *
         * @param capabilityCode 能力编码
         * @return 是否订阅
         */
        public boolean allowsCapability(String capabilityCode) {
            return unbounded || features == null || features.isEmpty() || features.contains(capabilityCode);
        }

        /**
         * 菜单是否在范围内（unbounded 或空集合语义 = 全量放行，与实体口径一致）。
         *
         * @param menuId 菜单 ID
         * @return true 表示可用
         */
        public boolean allowsMenu(Long menuId) {
            return unbounded || menuIds == null || menuIds.isEmpty() || menuIds.contains(menuId);
        }

        /**
         * 权限码是否在范围内（unbounded 或空集合语义 = 全量放行）。
         *
         * @param permissionCode 权限码
         * @return true 表示可用
         */
        public boolean allowsPermission(String permissionCode) {
            return unbounded
                    || permissionCodes == null
                    || permissionCodes.isEmpty()
                    || permissionCodes.contains(permissionCode);
        }
    }
}
