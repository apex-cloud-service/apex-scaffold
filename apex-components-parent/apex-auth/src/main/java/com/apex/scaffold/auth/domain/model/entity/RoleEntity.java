package com.apex.scaffold.auth.domain.model.entity;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 角色实体
 *
 * <p>角色是权限的集合，用于将一组权限批量授予用户。支持基于角色的访问控制（RBAC）。
 * 角色可以关联多个权限，一个用户也可以拥有多个角色。
 *
 * <p>数据权限范围：
 * <ul>
 *   <li>ALL - 全部数据权限</li>
 *   <li>DEPT_AND_SUB - 部门及子部门数据权限</li>
 *   <li>DEPT - 仅部门数据权限</li>
 *   <li>SELF - 仅本人数据权限</li>
 * </ul>
 *
 * @author Apex Scaffold Team
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleEntity {

    /**
     * 角色ID
     */
    private Long id;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 角色编码（唯一标识）
     */
    private String code;

    /**
     * 角色名称
     */
    private String name;

    /**
     * 角色描述
     */
    private String description;

    /**
     * 数据权限范围（ALL、DEPT_AND_SUB、DEPT、SELF）
     */
    private String dataScope;

    /**
     * 排序顺序
     */
    private Integer sortOrder;

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
     * 角色关联的权限ID集合
     */
    @Builder.Default
    private Set<Long> permissionIds = new HashSet<>();

    /**
     * 检查角色是否有效
     *
     * @return 如果角色已启用且未删除返回true
     */
    public boolean isValid() {
        return Boolean.TRUE.equals(enabled) && !Boolean.TRUE.equals(deleted);
    }

    /**
     * 启用角色
     */
    public void enable() {
        this.enabled = true;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 禁用角色
     */
    public void disable() {
        this.enabled = false;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 为角色添加权限
     *
     * @param permissionId 权限ID
     */
    public void addPermission(Long permissionId) {
        if (this.permissionIds == null) {
            this.permissionIds = new HashSet<>();
        }
        this.permissionIds.add(permissionId);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 移除角色的权限
     *
     * @param permissionId 权限ID
     */
    public void removePermission(Long permissionId) {
        if (this.permissionIds != null) {
            this.permissionIds.remove(permissionId);
            this.updatedAt = LocalDateTime.now();
        }
    }

    /**
     * 检查角色是否拥有指定权限
     *
     * @param permissionId 权限ID
     * @return 如果角色拥有该权限返回true
     */
    public boolean hasPermission(Long permissionId) {
        return this.permissionIds != null && this.permissionIds.contains(permissionId);
    }

    /**
     * 检查是否为超级管理员角色
     *
     * @return 如果是超级管理员或系统管理员角色返回true
     */
    public boolean isSuperRole() {
        return "SUPER_ADMIN".equals(this.code) || "SYSTEM_ADMIN".equals(this.code);
    }
}
