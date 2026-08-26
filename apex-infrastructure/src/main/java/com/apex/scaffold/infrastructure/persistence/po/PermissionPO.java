package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 权限持久化对象。
 *
 * <p>对应数据库中的sys_permission表，存储权限信息。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_permission")
public class PermissionPO extends BasePO {

    /**
     * 构造权限持久化对象。
     */
    public PermissionPO() {}

    /**
     * 权限名称。
     */
    private String name;

    /**
     * 权限编码。
     */
    private String code;

    /**
     * 权限描述。
     */
    private String description;

    /**
     * 权限类型。
     */
    private Integer type;

    /**
     * 状态（0-禁用，1-正常）。
     */
    private Integer status;

    /**
     * 更新权限的核心信息。
     *
     * @param name        权限名称
     * @param description 权限描述
     * @param status      状态（0-禁用，1-正常）
     */
    public void updateInfo(String name, String description, Integer status) {
        setName(name);
        setDescription(description);
        setStatus(status);
    }
}
