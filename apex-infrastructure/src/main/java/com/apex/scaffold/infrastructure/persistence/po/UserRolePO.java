package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户角色关联持久化对象。
 *
 * <p>对应数据库中的sys_user_role表，存储用户与角色的关联关系。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user_role")
public class UserRolePO extends BasePO {

    /**
     * 构造用户角色关联持久化对象。
     */
    public UserRolePO() {}

    /**
     * 用户ID。
     */
    private Long userId;

    /**
     * 角色ID。
     */
    private Long roleId;
}
