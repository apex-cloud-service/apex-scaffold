package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 租户持久化对象。
 *
 * <p>对应数据库中的 {@code sys_tenant} 表，存储租户基础信息、状态生命周期与配额。
 * 租户表为平台级元数据，不参与租户行级隔离。</p>
 *
 * <p>配额值对象（{@code maxUsers} / {@code maxStorageMb}）在持久化层拍平为两个独立列，
 * 由 {@code TenantPoConverter} 在领域实体与 PO 之间转换。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("sys_tenant")
public class TenantPO extends BasePO {

    /** 租户编码（全局唯一） */
    private String tenantCode;

    /** 租户名称 */
    private String tenantName;

    /** 租户管理员用户 ID */
    private String adminUserId;

    /** 套餐标识 */
    private String packageCode;

    /** 有效期至 */
    private LocalDateTime expireTime;

    /** 租户状态（枚举名：ACTIVE/DISABLED/FROZEN/DEACTIVATED/ARCHIVED） */
    private String status;

    /** 最大用户数（NULL 表示不限制） */
    private Integer maxUsers;

    /** 最大存储容量 MB（NULL 表示不限制） */
    private Integer maxStorageMb;

    /** 备注 */
    private String remark;
}
