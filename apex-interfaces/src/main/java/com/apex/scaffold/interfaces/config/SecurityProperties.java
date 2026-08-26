package com.apex.scaffold.interfaces.config;

import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 安全模块配置属性类
 *
 * <p>绑定 {@code apex.security} 前缀下的所有安全相关配置项，支持通过 Nacos 配置中心动态刷新。
 * 将管理端路径和角色等安全参数外部化，避免硬编码，提升配置的灵活性和可维护性。
 *
 * <h2>使用示例</h2>
 * <pre>{@code * # application.yml
 * apex:
 *   security:
 *     admin-paths:
 *       - /api/v1/auth/roles/**
 *       - /actuator/**
 *     admin-role: ADMIN
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "apex.security")
public class SecurityProperties {

    /**
     * 管理端路径列表
     *
     * <p>定义需要管理员角色才能访问的 Spring WebFlux {@code PathPattern} 路径模式。
     * 这些路径在 {@link SecurityConfig} 中使用 {@code hasRole} 进行权限校验。
     * 默认包含角色与权限管理、用户角色分配、文件删除和 Actuator 管理端点，
     * 避免普通登录用户取得管理能力或删除任意存储对象。
     * Actuator 的 liveness/readiness 由公开路径精确放行，其余管理端点继续要求管理员角色，
     * 从而同时满足容器编排探针与管理数据保护要求。
     * {@code **} 只能位于模式末尾；带路径变量的接口使用 {@code {variable}} 精确匹配。
     *
     */
    private List<String> adminPaths = List.of(
            "/api/v1/auth/roles/**",
            "/api/v1/auth/permissions/**",
            "/api/v1/auth/users/{userId}/roles/{roleId}",
            "/api/v1/file/delete",
            "/api/v1/admin/photo-batch-upload",
            "/api/v1/admin/photo-batch-upload/**",
            "/api/v1/admin/vote-simulation/**",
            "/actuator/**");

    /**
     * 管理员角色名称
     *
     * <p>拥有该角色的用户才能访问 {@link #adminPaths} 中定义的路径。
     * 默认值为 {@code "ADMIN"}，与 Spring Security 的角色前缀机制配合使用。
     */
    private String adminRole = "ADMIN";

    /** Roles allowed to enter the dedicated administration API security boundary. */
    private List<String> adminRoles = List.of(
            "ADMIN",
            "SUPER_ADMIN",
            "REVIEW_MANAGER",
            "PHOTO_REVIEWER",
            "INTEREST_REVIEWER",
            "INTEREST_PUBLISHER",
            "AUDITOR");
}
