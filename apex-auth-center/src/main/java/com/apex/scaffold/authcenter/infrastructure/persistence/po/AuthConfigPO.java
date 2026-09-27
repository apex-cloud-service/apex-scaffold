package com.apex.scaffold.authcenter.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 认证配置持久化对象。
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Data
@TableName("auth_config")
public class AuthConfigPO {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String tenantId;
    private String configType;
    private String configKey;
    private String configValue;
    private String description;
    private Boolean enabled;
    private Boolean isDefault;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;
}
