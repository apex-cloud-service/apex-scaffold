package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 用户角色关联持久化对象。
 *
 * <p>对应数据库中的sys_user_role表，存储用户与角色的关联关系。
 *
 * <p>{@code tenant_id} 列由 V14/V15 迁移补齐，且未在
 * {@code DefaultTenantLineHandler} 的忽略表名单中，MyBatis-Plus
 * {@code TenantLineInnerInterceptor} 启用时会为本表注入租户过滤条件，
 * 因此必须显式声明 {@link #tenantId} 字段，否则插入时该列始终为
 * {@code NULL}，导致写入的关联数据无法被租户过滤的查询检索到。
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
@TableName("sys_user_role")
public class UserRolePO extends BasePO {

    /**
     * 租户ID（多租户隔离）。
     *
     * <p>由 {@code TenantLineInnerInterceptor} 在启用租户隔离时自动注入
     * 过滤条件与写入值；单租户场景下可为 {@code null}。
     */
    @TableField("tenant_id")
    private String tenantId;

    /**
     * 用户ID。
     */
    private Long userId;

    /**
     * 角色ID。
     */
    private Long roleId;
}
