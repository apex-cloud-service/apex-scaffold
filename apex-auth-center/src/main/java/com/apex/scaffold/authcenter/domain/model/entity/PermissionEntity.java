package com.apex.scaffold.authcenter.domain.model.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 权限实体
 *
 * <p>权限是系统中最细粒度的授权单位，通常对应某个资源的某种操作。
 * 权限通过角色分配给用户，支持基于资源和操作的细粒度访问控制。
 *
 * <p>权限编码格式：{资源}:{操作}，例如：user:read、order:write
 *
 * @author Apex Scaffold Team
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionEntity {

    /**
     * 权限ID
     */
    private Long id;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 权限编码（唯一标识）
     */
    private String code;

    /**
     * 权限名称
     */
    private String name;

    /**
     * 权限描述
     */
    private String description;

    /**
     * 资源类型（API、MENU、BUTTON、DATA等）
     */
    private String resourceType;

    /**
     * 资源标识（资源路径、菜单路由等）
     */
    private String resourceIdentifier;

    /**
     * 操作类型（READ、WRITE、DELETE、EXECUTE等）
     */
    private String action;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 创建人ID
     */
    private Long createdBy;

    /**
     * 更新人ID
     */
    private Long updatedBy;

    /**
     * 是否删除
     */
    private Boolean deleted;

    /**
     * 乐观锁版本号
     */
    private Integer version;

    /**
     * 检查权限是否有效
     *
     * @return 如果权限已启用且未删除返回true
     */
    public boolean isValid() {
        return Boolean.TRUE.equals(enabled) && !Boolean.TRUE.equals(deleted);
    }

    /**
     * 启用权限
     */
    public void enable() {
        this.enabled = true;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 禁用权限
     */
    public void disable() {
        this.enabled = false;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 检查是否匹配指定的操作
     *
     * @param action 要检查的操作
     * @return 如果权限包含指定操作返回true
     */
    public boolean matchesAction(String action) {
        return this.action != null && this.action.equalsIgnoreCase(action);
    }

    /**
     * 检查是否匹配指定的资源
     *
     * @param resourceIdentifier 要检查的资源标识
     * @return 如果权限对应指定资源返回true
     */
    public boolean matchesResource(String resourceIdentifier) {
        return this.resourceIdentifier != null && this.resourceIdentifier.equals(resourceIdentifier);
    }
}
