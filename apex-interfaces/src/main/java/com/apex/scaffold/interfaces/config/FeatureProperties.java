package com.apex.scaffold.interfaces.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 商业版本功能开关配置。
 *
 * <p>对应 Maven Profile 中声明的 {@code features.*} 属性，由对应版本的
 * {@code application-{basic|standard|enterprise}.yml} 配置驱动，
 * 实现三档版本在运行时的真实功能差异。</p>
 *
 * <h2>主要功能</h2>
 * <ul>
 *   <li>通过 {@code @ConfigurationProperties(prefix = "apex.features")} 集中绑定功能开关</li>
 *   <li>为 basic / standard / enterprise 三档版本提供差异化的运行时能力</li>
 *   <li>供各配置类与业务组件按开关条件装配能力（如 RBAC、缓存、消息队列、监控、链路追踪）</li>
 * </ul>
 *
 * <h2>配置属性</h2>
 * <ul>
 *   <li>{@code apex.features.jwt-auth} - JWT 认证（三档均启用，默认 true）</li>
 *   <li>{@code apex.features.rbac} - RBAC 权限（标准版+企业版，默认 false）</li>
 *   <li>{@code apex.features.cache} - 多级缓存（标准版+企业版，默认 false）</li>
 *   <li>{@code apex.features.message-queue} - 消息队列（企业版，默认 false）</li>
 *   <li>{@code apex.features.monitoring} - 监控告警（标准版+企业版，默认 false）</li>
 *   <li>{@code apex.features.tracing} - 链路追踪（企业版，默认 false）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see org.springframework.boot.context.properties.ConfigurationProperties
 */
@Data
@Component
@ConfigurationProperties(prefix = "apex.features")
public class FeatureProperties {

    /** JWT 认证（基础版起）。 */
    private boolean jwtAuth = true;

    /** RBAC 权限（标准版起）。 */
    private boolean rbac = false;

    /** 多级缓存（标准版起）。 */
    private boolean cache = false;

    /** 消息队列（企业版）。 */
    private boolean messageQueue = false;

    /** 监控告警（标准版起）。 */
    private boolean monitoring = false;

    /** 链路追踪（企业版）。 */
    private boolean tracing = false;
}
