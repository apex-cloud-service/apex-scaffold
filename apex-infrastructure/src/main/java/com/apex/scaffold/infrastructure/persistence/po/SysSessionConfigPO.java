package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 会话配置持久化对象
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("sys_session_config")
public class SysSessionConfigPO extends BasePO {

    private Integer maxSessions;

    private Integer sessionTimeout;

    private Integer concurrentControl;

    private Integer rememberMeEnabled;

    private Integer rememberMeTtl;

    private Integer enabled;

    private String tenantId;
}
