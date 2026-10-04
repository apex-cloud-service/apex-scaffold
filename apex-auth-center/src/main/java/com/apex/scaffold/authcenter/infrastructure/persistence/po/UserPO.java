package com.apex.scaffold.authcenter.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.*;

/**
 * 认证中心用户数据表 {@code auth_user} 的持久化映射。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("auth_user")
public class UserPO {
    /** 主键，由 MyBatis-Plus 分配。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属认证中心租户标识。 */
    private String tenantId;

    /** 登录用户名，租户内唯一。 */
    private String username;

    /** 用户邮箱。 */
    private String email;

    /** 用户手机号。 */
    private String phone;

    /** 密码哈希值（BCrypt）。 */
    private String passwordHash;

    /** 第三方 OpenID（如微信、钉钉）。 */
    private String openId;

    /** 账号状态（0=禁用 1=启用 2=锁定）。 */
    private Integer status;

    /** 最后登录时间。 */
    private LocalDateTime lastLoginAt;

    /** 最后登录 IP 地址。 */
    private String lastLoginIp;

    /** 连续登录失败次数。 */
    private Integer failedLoginAttempts;

    /** 账号锁定截止时间，null 表示未锁定。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime lockedUntil;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 最后更新时间。 */
    private LocalDateTime updatedAt;

    /** 软删除标记。 */
    @TableLogic
    private Integer deleted;
}
