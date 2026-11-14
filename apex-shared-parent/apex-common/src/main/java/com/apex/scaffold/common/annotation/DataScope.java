package com.apex.scaffold.common.annotation;

import com.apex.scaffold.common.context.DataScopeConfig;
import com.apex.scaffold.common.context.DataScopeInfo;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据权限注解（自定义数据权限）。
 *
 * <p>标注在需要按数据权限隔离数据的 Service 方法上，由 {@code DataScopeAspect} 切面处理：
 * 从当前认证上下文解析用户的数据权限范围（{@link DataScopeInfo}）并装配到
 * {@code DataScopeContext}；{@code DataScopeInnerInterceptor}（MyBatis-Plus 拦截器）
 * 在查询 SQL 执行前自动注入 {@code dept_id / user_id} 过滤条件，业务代码无需手动拼接。</p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code * // 用户列表按部门过滤（MyBatis-Plus 自动生成的 FROM sys_user 无别名，可不指定 deptAlias）
 * @DataScope
 * public Mono<PageResponse<UserDTO>> pageUsers(...) { ... }
 *
 * // 多表 JOIN 查询：必须指定部门字段所在表的别名，避免列歧义
 * @DataScope(deptAlias = "u", userAlias = "u")
 * public Mono<PageResponse<UserDTO>> pageUserWithDept(...) { ... }
 * }</pre>
 *
 * <p>与 {@code RequirePermission} 的差异：{@code RequirePermission} 控制"能否访问
 * 接口"（功能权限），{@code DataScope} 控制"能看哪些数据"（数据权限）。</p>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 2.2.0
 * @see DataScopeInfo
 * @see DataScopeConfig
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataScope {

    /**
     * 部门字段所在表的别名（用于拼接 {@code dept_id} 过滤条件）。
     *
     * <p>多表 JOIN 场景必填（如 {@code @DataScope(deptAlias = "u")}），
     * 避免过滤列在多表中产生歧义；MyBatis-Plus 自动生成的无别名单表 SQL 可留空。</p>
     *
     * @return 表别名，如 {@code u}、{@code d}
     */
    String deptAlias() default "";

    /**
     * 用户字段所在表的别名（{@code 仅本人数据权限} 时拼接 {@code user_id} 条件）。
     *
     * @return 表别名，如 {@code u}；留空时按主表裸列名注入
     */
    String userAlias() default "";

    /**
     * 部门字段名（{@code DataScopeType.DEPT / DEPT_AND_CHILD / CUSTOM} 使用）。
     *
     * @return 部门字段名，默认 {@code dept_id}
     */
    String deptField() default "dept_id";

    /**
     * 用户字段名（{@code DataScopeType.SELF} 使用）。
     *
     * @return 用户字段名，默认 {@code user_id}
     */
    String userField() default "user_id";
}
