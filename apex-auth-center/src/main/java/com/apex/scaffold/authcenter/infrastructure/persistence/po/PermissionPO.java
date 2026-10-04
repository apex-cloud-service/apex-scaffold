package com.apex.scaffold.authcenter.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.*;

/**
 * 认证中心权限数据表 {@code auth_permission} 的持久化映射。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("auth_permission")
public class PermissionPO {
    /** 主键，由 MyBatis-Plus 分配。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属认证中心租户标识。 */
    private String tenantId;

    /** 租户内唯一的权限编码。 */
    private String code;

    /** 权限展示名称。 */
    private String name;

    /** 权限用途说明。 */
    private String description;

    /** 权限资源类别。 */
    private String resourceType;

    /** 权限对应的资源标识，映射数据库 {@code resource} 列。 */
    @TableField("resource")
    private String resourceIdentifier;

    /** 权限动作标识。 */
    private String action;

    /** 启用状态。 */
    private Integer enabled;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 最后更新时间。 */
    private LocalDateTime updatedAt;

    /** 创建者中心用户标识。 */
    private Long createdBy;

    /** 最后更新者中心用户标识。 */
    private Long updatedBy;

    /** 软删除标记。 */
    @TableLogic
    private Integer deleted;

    /** 乐观锁版本号。 */
    private Integer version;
}
