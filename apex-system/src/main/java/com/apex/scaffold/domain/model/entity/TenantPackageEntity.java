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
 * {@code menuIds}/{@code permissionCodes}/{@code features} 为空集合时表示全量（不裁剪），
 * 与种子 {@code platform-full} 的"数据驱动全量"语义一致。
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

    /** 平台全量套餐编码（种子内置，不裁剪） */
    public static final String PLATFORM_FULL_CODE = "platform-full";

    /** 平台默认租户编码（不裁剪） */
    public static final String PLATFORM_TENANT_CODE = "default";

    private Long id;

    private String packageCode;

    private String packageName;

    /** 可用菜单 ID 集合；空集合 = 全量 */
    private Set<Long> menuIds;

    /** 可用权限码集合；空集合 = 全量 */
    private Set<String> permissionCodes;

    /** 能力订阅集合（能力运营 D4 预留） */
    private Set<String> features;

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
            while (parent != null && parent > 0 && completed.add(parent) && guard++ < 32) {
                parent = parentIdResolver.apply(parent);
            }
        }
        return completed;
    }
}
