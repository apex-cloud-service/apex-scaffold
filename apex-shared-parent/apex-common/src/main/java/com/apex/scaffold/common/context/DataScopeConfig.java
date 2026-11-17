package com.apex.scaffold.common.context;

import com.apex.scaffold.common.annotation.DataScope;

/**
 * 数据权限注入配置（过滤条件字段定位）。
 *
 * <p>声明 {@code DataScopeInnerInterceptor} 注入过滤条件时使用哪张表（别名）、哪个字段：</p>
 *
 * <ul>
 *   <li>{@code deptAlias}：部门字段所在表的别名（多表 JOIN 场景必填，避免列歧义）；
 *       为空时按主表裸列名注入（MyBatis-Plus 自动生成的无别名 SQL 适用）；</li>
 *   <li>{@code userAlias}：用户字段所在表的别名（{@link com.apex.scaffold.common.enums.DataScopeType#SELF} 类型使用）；</li>
 *   <li>{@code deptField}：部门字段名，默认 {@code dept_id}；</li>
 *   <li>{@code userField}：用户字段名，默认 {@code user_id}。</li>
 * </ul>
 *
 * @param deptAlias 部门字段所在表的别名（可为空）
 * @param userAlias 用户字段所在表的别名（可为空）
 * @param deptField 部门字段名
 * @param userField 用户字段名
 * @author Apex Team
 * @version 1.0.0
 * @since 2.3.0
 */
public record DataScopeConfig(String deptAlias, String userAlias, String deptField, String userField) {

    /** 默认部门字段名 */
    private static final String DEFAULT_DEPT_FIELD = "dept_id";

    /** 默认用户字段名 */
    private static final String DEFAULT_USER_FIELD = "user_id";

    /**
     * 默认注入配置（无别名，{@code dept_id} / {@code user_id}）。
     *
     * @return 默认配置
     */
    public static DataScopeConfig defaults() {
        return new DataScopeConfig("", "", DEFAULT_DEPT_FIELD, DEFAULT_USER_FIELD);
    }

    /**
     * 从 {@link DataScope} 注解转换注入配置。
     *
     * @param annotation 注解（可为 {@code null}，此时返回默认配置）
     * @return 注入配置
     */
    public static DataScopeConfig from(DataScope annotation) {
        if (annotation == null) {
            return defaults();
        }
        return new DataScopeConfig(
                nullToEmpty(annotation.deptAlias()),
                nullToEmpty(annotation.userAlias()),
                defaultIfBlank(annotation.deptField(), DEFAULT_DEPT_FIELD),
                defaultIfBlank(annotation.userField(), DEFAULT_USER_FIELD));
    }

    /**
     * 是否指定了部门表别名。
     *
     * @return {@code true} 表示指定了别名
     */
    public boolean hasDeptAlias() {
        return deptAlias != null && !deptAlias.isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
