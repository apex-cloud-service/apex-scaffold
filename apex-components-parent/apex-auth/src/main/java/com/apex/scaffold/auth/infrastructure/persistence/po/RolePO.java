package com.apex.scaffold.auth.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.*;

/**
 * 认证中心角色数据表 {@code auth_role} 的持久化映射。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("auth_role")
public class RolePO {
    /** 主键，由 MyBatis-Plus 分配。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属认证中心租户标识。 */
    private String tenantId;

    /** 租户内唯一的角色编码。 */
    private String code;

    /** 角色展示名称。 */
    private String name;

    /** 角色用途说明。 */
    private String description;

    /** 数据范围名称；应用层仅映射支持的范围，未知值拒绝授权。 */
    private String dataScope;

    /** 角色排序值。 */
    private Integer sortOrder;

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
