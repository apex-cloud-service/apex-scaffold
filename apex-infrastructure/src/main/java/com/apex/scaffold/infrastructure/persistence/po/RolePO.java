package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色持久化对象。
 *
 * <p>对应数据库中的sys_role表，存储角色信息。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class RolePO extends BasePO {

    /**
     * 构造角色持久化对象。
     */
    public RolePO() {}

    /**
     * 角色名称。
     */
    private String name;

    /**
     * 角色编码。
     */
    private String code;

    /**
     * 角色描述。
     */
    private String description;

    /**
     * 状态（0-禁用，1-正常）。
     */
    private Integer status;

    /**
     * 更新角色的核心信息。
     *
     * @param name        角色名称
     * @param description 角色描述
     */
    public void updateInfo(String name, String description) {
        setName(name);
        setDescription(description);
    }
}
