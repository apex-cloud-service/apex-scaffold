package com.apex.scaffold.common.annotation;

import java.lang.annotation.*;

/**
 * 管理员角色校验注解
 *
 * <p>用于方法级别的管理员权限门禁检查。与 {@link RequireResourcePermission} 不同，
 * 本注解不关心具体的资源实例，只校验当前登录用户是否具备指定的管理员角色，
 * 适用于后台管理类操作（如解锁账户、终止会话、清理日志、分配权限等高危操作）。</p>
 *
 * <p>使用示例：</p>
 * <pre>
 * // 仅超级管理员可访问
 * {@code @RequireAdminRole}
 * public Mono&lt;ServerResponse&gt; unlockMfa(ServerRequest request) { ... }
 *
 * // 指定允许的角色列表（任意一个匹配即可）
 * {@code @RequireAdminRole(roles = {"ROLE_ADMIN", "ROLE_SUPER_ADMIN"})}
 * public Mono&lt;ServerResponse&gt; cleanupExpiredLogs(ServerRequest request) { ... }
 * </pre>
 *
 * @author Apex Security Team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireAdminRole {

    /**
     * 允许访问的角色列表，多个角色之间为"或"关系（具备任意一个即可通过）
     *
     * @return 角色标识数组（对应 {@code Authentication#getAuthorities()} 中的权限字符串）
     */
    String[] roles() default {"ROLE_SUPER_ADMIN", "ROLE_ADMIN"};

    /**
     * 权限检查失败时的错误消息
     *
     * @return 错误消息
     */
    String errorMessage() default "需要管理员权限";
}
