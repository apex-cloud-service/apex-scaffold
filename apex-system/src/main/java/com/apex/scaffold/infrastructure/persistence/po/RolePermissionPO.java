package com.apex.scaffold.infrastructure.persistence.po;

import com.apex.scaffold.common.persistence.po.BasePO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 角色权限关联持久化对象。
 *
 * <p>对应数据库中的sys_role_permission表，存储角色与权限的关联关系。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role_permission")
public class RolePermissionPO extends BasePO {

    /**
     * 角色ID。
     */
    private Long roleId;

    /**
     * 权限ID。
     */
    private Long permissionId;
}
