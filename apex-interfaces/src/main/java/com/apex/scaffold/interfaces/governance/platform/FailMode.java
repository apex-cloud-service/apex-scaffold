package com.apex.scaffold.interfaces.governance.platform;

/**
 * 外部治理依赖故障处理方式。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public enum FailMode {
    /** 允许本地服务继续提供能力。 */
    OPEN,
    /** 外部治理依赖失败时拒绝服务。 */
    CLOSED,
    /** 根据部署模式决定。 */
    AUTO
}
