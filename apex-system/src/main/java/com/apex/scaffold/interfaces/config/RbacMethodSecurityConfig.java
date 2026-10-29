package com.apex.scaffold.interfaces.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;

/**
 * RBAC 方法级安全条件配置。
 *
 * <p>当且仅当 {@code apex.features.rbac=true}（默认 true）时启用
 * {@link EnableReactiveMethodSecurity}，使 {@code @PreAuthorize} /
 * {@code @PostAuthorize} 等方法级权限注解生效。basic 版本该属性为 false，
 * 跳过方法级安全装配，避免对非 RBAC 环境产生不必要的代理开销。</p>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>URL 级授权规则始终由 {@link SecurityConfig} 统一管理</li>
 *   <li>方法级注解（如接口层 {@code @PreAuthorize("hasRole('ADMIN')")}）仅在
 *       standard / enterprise 版本生效</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 * @see SecurityConfig
 */
@Configuration
@ConditionalOnProperty(prefix = "apex.features", name = "rbac", havingValue = "true", matchIfMissing = true)
@EnableReactiveMethodSecurity
public class RbacMethodSecurityConfig {}
