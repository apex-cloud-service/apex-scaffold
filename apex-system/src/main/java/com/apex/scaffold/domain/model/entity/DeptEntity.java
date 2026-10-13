package com.apex.scaffold.domain.model.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 部门实体（领域层）。
 *
 * <p>树形部门结构，支撑数据权限（C3）按部门层级过滤。与基础设施层
 * {@code DeptPO} 相互转换，领域层不感知持久化细节。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DeptEntity extends BaseEntity {

    private String tenantId;

    /** 父部门 ID（0 为根部门） */
    private Long parentId;

    /** 祖级列表（逗号分隔，如 0,100） */
    private String ancestors;

    /** 部门名称 */
    private String name;

    /** 显示顺序 */
    private Integer orderNum;

    /** 负责人 */
    private String leader;

    /** 联系电话 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 状态（0-停用，1-正常） */
    private Integer status;
}
