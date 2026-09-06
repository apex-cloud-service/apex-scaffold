package com.apex.scaffold.interfaces.governance.platform;

/**
 * 服务注册方式。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public enum RegistrationProvider {
    /** 自动选择。 */
    AUTO,
    /** 不使用注册中心。 */
    NONE,
    /** 使用 Nacos。 */
    NACOS,
    /** 使用部署平台自身的服务发现。 */
    PLATFORM
}
