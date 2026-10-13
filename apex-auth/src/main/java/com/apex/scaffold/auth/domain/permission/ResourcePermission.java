package com.apex.scaffold.auth.domain.permission;

import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 资源级权限定义
 *
 * 支持细粒度的资源访问控制，例如：
 * - 用户只能访问自己的订单
 * - 部门经理可以访问本部门的数据
 * - 数据所有者可以编辑和删除数据
 * @author Apex Team
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourcePermission {

    /**
     * 资源类型（例如：ORDER, USER, DOCUMENT）
     */
    private String resourceType;

    /**
     * 资源ID
     */
    private String resourceId;

    /**
     * 操作类型（例如：READ, WRITE, DELETE, APPROVE）
     */
    private String action;

    /**
     * 权限条件（SpEL表达式）
     * 例如：
     * - "#userId == authentication.userId" (只能访问自己的资源)
     * - "#departmentId == authentication.departmentId" (只能访问本部门资源)
     * - "#resource.ownerId == authentication.userId" (只能访问自己创建的资源)
     */
    private String condition;

    /**
     * 权限范围
     */
    private PermissionScope scope;

    /**
     * 允许的角色
     */
    private Set<String> allowedRoles;

    /**
     * 允许的用户ID
     */
    private Set<String> allowedUserIds;

    /**
     * 权限优先级（数字越大优先级越高）
     */
    private Integer priority;

    /**
     * 权限范围枚举
     */
    public enum PermissionScope {
        /**
         * 全局范围
         */
        GLOBAL,

        /**
         * 租户范围
         */
        TENANT,

        /**
         * 部门范围
         */
        DEPARTMENT,

        /**
         * 个人范围
         */
        PERSONAL,

        /**
         * 自定义范围
         */
        CUSTOM
    }

    /**
     * 检查权限是否匹配
     */
    public boolean matches(String resourceType, String resourceId, String action) {
        if (this.resourceType != null && !this.resourceType.equals(resourceType)) {
            return false;
        }

        if (this.resourceId != null && !this.resourceId.equals(resourceId)) {
            return false;
        }

        if (this.action != null && !this.action.equals(action)) {
            return false;
        }

        return true;
    }
}
