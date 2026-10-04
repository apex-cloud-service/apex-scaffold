package com.apex.scaffold.authcenter.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 认证中心配置数据表 {@code auth_config} 的持久化映射。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.2.0
 */
@Data
@TableName("auth_config")
public class AuthConfigPO {
    /** 主键，由 MyBatis-Plus 分配。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属认证中心租户标识。 */
    private String tenantId;

    /** 配置类型（PASSWORD_POLICY/SESSION_POLICY/OAUTH2_CLIENT 等）。 */
    private String configType;

    /** 配置键。 */
    private String configKey;

    /** 配置值（JSON 格式）。 */
    private String configValue;

    /** 配置描述。 */
    private String description;

    /** 是否启用。 */
    private Boolean enabled;

    /** 是否为默认配置。 */
    private Boolean isDefault;

    /** 乐观锁版本号。 */
    private Integer version;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 最后更新时间。 */
    private LocalDateTime updatedAt;

    /** 创建者用户标识。 */
    private Long createdBy;

    /** 最后更新者用户标识。 */
    private Long updatedBy;
}
