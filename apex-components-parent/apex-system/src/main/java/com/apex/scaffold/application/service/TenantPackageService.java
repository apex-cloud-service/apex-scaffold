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
     * 解析指定租户与套餐的裁剪范围。
     *
     * <p>只有平台默认租户可隐式获得全量范围。非平台租户缺少套餐、套餐不存在、套餐已停用
     * 或套餐 JSON 配置损坏时一律返回 {@link PackageScope#deniedScope()}，不得把配置缺失
     * 放大为全量授权。这是菜单、权限码与能力订阅共用的 fail-closed 边界。</p>
     *
     * @param tenantCode 当前租户编码
     * @param packageCode 租户绑定的套餐编码；非平台租户不得为空白
     * @return 裁剪范围；无法可靠解析时返回拒绝范围
     */
    public Mono<PackageScope> resolveScope(String tenantCode, String packageCode) {
        if (TenantPackageEntity.PLATFORM_TENANT_CODE.equals(tenantCode)) {
            return Mono.just(PackageScope.full());
        }
        if (packageCode == null || packageCode.isBlank()) {
            log.warn("租户未绑定套餐，拒绝其全部菜单、权限与能力: tenant={}", tenantCode);
            return Mono.just(PackageScope.deniedScope());
        }
        return tenantPackageRepository
                .findByCode(packageCode)
                .map(pkg -> {
                    if (!pkg.isActive()) {
                        log.warn("租户套餐已停用，拒绝其全部菜单、权限与能力: tenant={}, package={}", tenantCode, packageCode);
                        return PackageScope.deniedScope();
                    }
                    if (pkg.isConfigurationInvalid()) {
                        log.warn("租户套餐配置损坏，拒绝其全部菜单、权限与能力: tenant={}, package={}", tenantCode, packageCode);
                        return PackageScope.deniedScope();
                    }
                    return PackageScope.of(pkg);
                })
                .defaultIfEmpty(PackageScope.deniedScope())
                .doOnNext(scope -> {
                    if (!scope.unbounded() && !scope.denied()) {
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
     * <p>租户不存在时返回拒绝范围，避免未知租户获得平台默认租户权限。</p>
     *
     * @param tenantCode 当前租户编码
     * @return 裁剪范围；租户不存在时返回拒绝范围
     */
    public Mono<PackageScope> resolveScope(String tenantCode) {
        if (TenantPackageEntity.PLATFORM_TENANT_CODE.equals(tenantCode)) {
            return Mono.just(PackageScope.full());
        }
        return tenantRepository
                .findByCode(tenantCode)
                .flatMap(tenant -> resolveScope(tenantCode, tenant.getPackageCode()))
                .defaultIfEmpty(PackageScope.deniedScope());
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
     * 判定口径与 {@link PackageScope#allowsCapability(String)} 一致（{@code unbounded=true} 表示订阅全部能力；
     * {@code unbounded=false} 且 features 为空表示未订阅任何能力）。
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
     * @param features 已订阅特性编码（空集合表示未订阅任何能力；全量须 {@code unbounded=true} 表达）
     */
    @Schema(name = "CapabilitySnapshot", description = "租户能力订阅快照（内部端点契约）")
    public record CapabilitySnapshot(
            @Schema(description = "租户编码", example = "T001") String tenantCode,
            @Schema(description = "租户是否存在（未知租户 fail-closed 返回 false）") boolean exists,
            @Schema(description = "是否不限量租户（仅平台租户可为 true）") boolean unbounded,
            @Schema(description = "已订阅特性编码（空集合表示未订阅任何能力；全量须 unbounded=true 表达）") java.util.Set<String> features) {}

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
     * <p>{@code unbounded} 与 {@code denied} 互斥：全量范围只用于显式平台租户或有效全量套餐；
     * 拒绝范围用于套餐缺失、停用、未知或配置损坏，所有授权判定均返回 {@code false}。
     *
     * @param unbounded 是否不裁剪（仅显式全量范围）
     * @param denied 是否拒绝全部菜单、权限码与能力
     * @param menuIds 可用菜单 ID 集合（含父链补全由调用方按需执行）
     * @param permissionCodes 可用权限码集合（空集合 = 权限码全量）
     * @param features 已订阅能力编码集合（空集合 = 未订阅任何能力；全量须 {@code unbounded=true}）
     */
    public record PackageScope(
            boolean unbounded,
            boolean denied,
            java.util.Set<Long> menuIds,
            java.util.Set<String> permissionCodes,
            java.util.Set<String> features) {

        /**
         * 构造不裁剪的全量范围。
         *
         * <p>{@code unbounded=true} 会同时放行菜单、权限码与全部能力；此时
         * {@code features} 仍保持空集合，表示“无需逐项列举”，与普通套餐的空能力集合
         * 在 {@link #allowsCapability(String)} 中的判定不同。
         *
         * @return 全量范围
         */
        public static PackageScope full() {
            return new PackageScope(true, false, java.util.Set.of(), java.util.Set.of(), java.util.Set.of());
        }

        /**
         * 构造拒绝全部授权的范围。
         *
         * <p>用于套餐缺失、停用、未知、租户不存在或 JSON 配置损坏等无法可靠判定的场景。
         * 调用方不得把该范围解释为“未配置即全量”。</p>
         *
         * @return 拒绝范围
         */
        public static PackageScope deniedScope() {
            return new PackageScope(false, true, java.util.Set.of(), java.util.Set.of(), java.util.Set.of());
        }

        /**
         * 根据有效套餐实体构造受限范围。
         *
         * <p>菜单与权限码的空集合继续表示对应维度全量；能力集合为空表示未订阅任何能力。
         * 调用方必须先验证套餐存在、处于启用态且配置可解析，不得把未知或停用套餐传入本方法。</p>
         *
         * @param pkg 有效套餐实体
         * @return 套餐裁剪范围
         * @throws IllegalArgumentException 当 {@code pkg} 为 {@code null}、非启用态或配置损坏时抛出
         */
        public static PackageScope of(TenantPackageEntity pkg) {
            if (pkg == null) {
                throw new IllegalArgumentException("套餐不能为空");
            }
            if (!pkg.isActive()) {
                throw new IllegalArgumentException("套餐未启用，不能构造授权范围");
            }
            if (pkg.isConfigurationInvalid()) {
                throw new IllegalArgumentException("套餐配置损坏，不能构造授权范围");
            }
            return new PackageScope(
                    false,
                    false,
                    pkg.getMenuIds(),
                    pkg.getPermissionCodes(),
                    pkg.getFeatures() == null ? java.util.Set.of() : pkg.getFeatures());
        }

        /**
         * 能力是否已订阅（口径与 {@code CapabilityGate} 一致：{@code unbounded=true} 订阅全部能力；
         * 拒绝范围与空 features 均不授予任何能力）。
         *
         * @param capabilityCode 能力编码
         * @return 是否订阅
         */
        public boolean allowsCapability(String capabilityCode) {
            if (denied) {
                return false;
            }
            return unbounded || (features != null && features.contains(capabilityCode));
        }

        /**
         * 菜单是否在范围内（拒绝范围始终拒绝；unbounded 或空集合语义 = 全量放行）。
         *
         * @param menuId 菜单 ID
         * @return true 表示可用
         */
        public boolean allowsMenu(Long menuId) {
            if (denied) {
                return false;
            }
            return unbounded || menuIds == null || menuIds.isEmpty() || menuIds.contains(menuId);
        }

        /**
         * 权限码是否在范围内（拒绝范围始终拒绝；unbounded 或空集合语义 = 全量放行）。
         *
         * @param permissionCode 权限码
         * @return true 表示可用
         */
        public boolean allowsPermission(String permissionCode) {
            if (denied) {
                return false;
            }
            return unbounded
                    || permissionCodes == null
                    || permissionCodes.isEmpty()
                    || permissionCodes.contains(permissionCode);
        }
    }
}
