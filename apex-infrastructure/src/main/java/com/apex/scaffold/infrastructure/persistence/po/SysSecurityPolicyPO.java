package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 安全策略持久化对象
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("sys_security_policy")
public class SysSecurityPolicyPO extends BasePO {

    private Integer passwordMinLength;

    private Integer passwordRequireUpper;

    private Integer passwordRequireLower;

    private Integer passwordRequireDigit;

    private Integer passwordRequireSpecial;

    private Integer passwordExpiryDays;

    private Integer maxLoginAttempts;

    private Integer lockoutDuration;

    private String ipWhitelist;

    private String ipBlacklist;

    private Integer enabled;

    private String tenantId;
}
