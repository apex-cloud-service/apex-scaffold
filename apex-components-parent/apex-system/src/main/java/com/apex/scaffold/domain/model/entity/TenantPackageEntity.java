package com.apex.scaffold.domain.model.entity;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 租户套餐实体（平台级定义，统一认证收口 / 方案批次 B3）。
 *
 * <p>非默认租户按套餐裁剪可见菜单与有效权限码；{@code default} 平台租户不裁剪。
 * 菜单与权限码的空集合沿用既有数据驱动口径，表示对应维度不裁剪；能力订阅则不同，
 * {@code features} 为空集合表示未订阅任何能力。套餐不存在、停用或 JSON 配置损坏时，
 * 授权链路必须 fail-closed（拒绝全部），禁止把配置缺失静默放大为全量授权。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantPackageEntity {

    private static final int MAX_PARENT_CHAIN_DEPTH = 32;

    /**
     * 平台全量套餐编码（种子内置）。
     *
     * <p>菜单与权限码由 V78 在迁移时按存量数据聚合为全量；能力编码由 V90 回填为
     * 当前已登记能力，后续新增能力仍需通过套餐配置显式授予。
     */
    public static final String PLATFORM_FULL_CODE = "platform-full";

    /** 平台默认租户编码（不裁剪） */
    public static final String PLATFORM_TENANT_CODE = "default";

    private Long id;

    private String packageCode;

    private String packageName;

    /** 可用菜单 ID 集合；空集合 = 全量（仅对有效且配置可解析的套餐生效） */
    private Set<Long> menuIds;

    /** 可用权限码集合；空集合 = 全量（仅对有效且配置可解析的套餐生效） */
    private Set<String> permissionCodes;

    /**
     * 已订阅能力编码集合。
     *
     * <p>空集合表示未订阅任何能力，不表示全量；全量订阅由套餐范围中的
     * {@code unbounded=true} 表达。编码取值须与能力目录登记一致。
     */
    private Set<String> features;

    /**
     * 配置是否损坏。
     *
     * <p>持久化 JSON 中任一数组无法解析时由仓储置为 {@code true}；授权链路据此拒绝
     * 该套餐的全部菜单、权限码与能力，避免空集合被误解释为全量。</p>
     */
    private boolean configurationInvalid;

    /** 状态（0-停用，1-正常） */
    private Integer status;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer version;

    private Integer deleted;

    /** 套餐是否处于启用态。 */
    public boolean isActive() {
        return status != null && status == 1;
    }

    /**
     * 判定菜单是否在套餐范围内（空集合 = 全量放行）。
     *
     * @param menuId 菜单 ID
     * @return true 表示可用
     */
    public boolean allowsMenu(Long menuId) {
        return menuIds == null || menuIds.isEmpty() || (menuId != null && menuIds.contains(menuId));
    }

    /**
     * 判定权限码是否在套餐范围内（空集合 = 全量放行）。
     *
     * @param permissionCode 权限码
     * @return true 表示可用
     */
    public boolean allowsPermission(String permissionCode) {
        return permissionCodes == null
                || permissionCodes.isEmpty()
                || (permissionCode != null && permissionCodes.contains(permissionCode));
    }

    /**
     * 补全菜单 ID 集合的父链（套餐勾选子菜单时其祖先必须可见）。
     *
     * @param parentIdResolver 菜单 ID → 父 ID 解析器（根/未知返回空）
     * @return 含父链的完整菜单 ID 集合（新集合，不改原集合）
     */
    public Set<Long> withParentChain(java.util.function.LongFunction<Long> parentIdResolver) {
        if (menuIds == null || menuIds.isEmpty()) {
            return menuIds == null ? new HashSet<>() : new HashSet<>(menuIds);
        }
        Set<Long> completed = new HashSet<>(menuIds);
        for (Long menuId : menuIds) {
            Long parent = parentIdResolver.apply(menuId);
            int guard = 0;
            while (parent != null && parent > 0 && completed.add(parent) && guard++ < MAX_PARENT_CHAIN_DEPTH) {
                parent = parentIdResolver.apply(parent);
            }
        }
        return completed;
    }
}
