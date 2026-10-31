package com.apex.scaffold.domain.model.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 租户级参数配置实体（方案批次 B6）。
 *
 * <p>随租户行级隔离（sys_tenant_config 不进拦截器白名单）；读取优先级为
 * 租户配置 → 全局配置（由 {@code ConfigService} 组合）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantConfigEntity {

    private Long id;

    /** 租户编码 */
    private String tenantId;

    /** 配置键 */
    private String configKey;

    /** 配置值 */
    private String configValue;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer version;

    private Integer deleted;
}
