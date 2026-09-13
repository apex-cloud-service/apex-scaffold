package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 角色-部门关联持久化对象。
 *
 * <p>映射数据表 {@code sys_role_dept}，记录角色与部门之间的数据权限归属关系，
 * 用于实现按部门维度的数据范围控制。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see com.apex.scaffold.infrastructure.persistence.mapper.RoleDeptMapper
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role_dept")
/**n * RoleDeptPO - 持久化对象n *n * <p>对应数据库表：TODO: 补充表名n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public class RoleDeptPO extends BasePO {

    /** 租户 ID */
    private String tenantId;

    /** 角色 ID */
    private Long roleId;

    /** 部门 ID */
    private Long deptId;
}
