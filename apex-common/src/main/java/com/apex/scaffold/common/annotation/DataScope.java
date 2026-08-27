package com.apex.scaffold.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据权限注解（部门级）。
 *
 * <p>标注在需要按部门隔离数据的 Service 方法上（C3 数据权限/部门体系），
 * 由 {@code DataScopeAspect} 切面处理：从当前认证上下文解析用户所属部门，
 * 对超出数据权限范围的访问进行拦截或注入部门过滤条件。</p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code * @DataScope(deptAlias = "u")
 * public Mono<PageResponse<UserDTO>> pageUsers(...) {
 *     // 查询 SQL 需按 @DataScope 注入的 dept_id 条件过滤
 * }
 * }</pre>
 *
 * <p>与 {@code RequirePermission} 的差异：{@code RequirePermission} 控制"能否访问
 * 接口"（功能权限），{@code DataScope} 控制"能看哪些数据"（数据权限）。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataScope {

    /**
     * 部门字段所在表的别名（用于拼接 {@code dept_id} 过滤条件）。
     *
     * @return 表别名，如 {@code u}、{@code d}
     */
    String deptAlias() default "";
}
