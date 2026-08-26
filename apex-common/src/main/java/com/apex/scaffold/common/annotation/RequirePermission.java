package com.apex.scaffold.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限校验注解
 *
 * <p>用于方法级别，标注需要特定权限才能访问的方法。
 *
 * <h2>设计说明</h2>
 * <p>当前 {@code value()} 返回单个权限编码（{@code String}），
 * 配合 {@code logical} 字段预留 AND/OR 逻辑组合能力。
 * 若需同时校验多个权限，请在方法上叠加多个 {@code @RequirePermission} 注解。
 * 未来版本可扩展为 {@code String[]} 以支持单注解多权限。</p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code * // 单个权限校验
 * @RequirePermission("user:read")
 * public UserVO getUser(Long id) { ... }
 *
 * // 多个权限（OR关系，满足任一即可）
 * @RequirePermission("admin:read")
 * @RequirePermission("user:read")
 * public List<UserVO> listUsers() { ... }
 * }</pre>
 *
 * @author Apex Team
 * @version 1.1.0
 * @since 1.0.0
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {

    /**
     * 权限编码（单个权限标识）
     *
     * <p>需与系统中定义的权限编码一致。当前为单个权限，
     * 多个权限请叠加多个注解。
     *
     * @return 权限编码
     */
    String value();

    /**
     * 逻辑关系（AND/OR），预留字段
     *
     * <p>当叠加多个 @RequirePermission 注解时使用。
     * OR：满足任一权限即可；AND：需同时满足所有权限。
     *
     * @return 逻辑关系，默认为 OR
     */
    Logical logical() default Logical.OR;
}
