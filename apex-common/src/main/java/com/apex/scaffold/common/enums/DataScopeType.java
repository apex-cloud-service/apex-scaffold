package com.apex.scaffold.common.enums;

import java.util.Arrays;
import java.util.Locale;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 数据权限类型（MyBatis-Plus 自定义数据权限）。
 *
 * <p>声明"当前用户能看哪些数据"，由 {@code DataScopeInnerInterceptor} 在 SELECT 执行前
 * 依据该类型自动拼接对应的 SQL 过滤条件：</p>
 *
 * <ul>
 *   <li>{@link #ALL}：全部数据权限，不注入任何条件；</li>
 *   <li>{@link #DEPT}：本部门数据权限，注入 {@code dept_field = deptId}；</li>
 *   <li>{@link #DEPT_AND_CHILD}：本部门及以下数据权限，注入
 *       {@code dept_field IN (deptId, 子部门...)}（子部门集合由装配端预解析到
 *       {@code DataScopeInfo.deptIds}）；</li>
 *   <li>{@link #SELF}：仅本人数据权限，注入 {@code user_field = userId}；</li>
 *   <li>{@link #CUSTOM}：自定义数据权限，注入 {@code dept_field IN (指定部门集合)}。</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.3.0
 */
@Getter
@RequiredArgsConstructor
public enum DataScopeType {

    /** 全部数据权限 */
    ALL(1, "全部数据权限"),

    /** 本部门数据权限 */
    DEPT(2, "本部门数据权限"),

    /** 本部门及以下数据权限 */
    DEPT_AND_CHILD(3, "本部门及以下数据权限"),

    /** 仅本人数据权限 */
    SELF(4, "仅本人数据权限"),

    /** 自定义数据权限（指定部门集合） */
    CUSTOM(5, "自定义数据权限");

    /** 类型编码 */
    private final Integer code;

    /** 类型描述 */
    private final String desc;

    /**
     * 按编码解析类型。
     *
     * @param code 编码（可为 {@code null}）
     * @return 匹配的类型；无匹配时返回 {@code null}
     */
    public static DataScopeType fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(type -> type.code.equals(code))
                .findFirst()
                .orElse(null);
    }

    /**
     * 按编码解析类型，未匹配时回退默认值。
     *
     * @param code         编码（可为 {@code null}）
     * @param defaultValue 回退默认值
     * @return 匹配的类型或默认值
     */
    public static DataScopeType fromCodeOrDefault(Integer code, DataScopeType defaultValue) {
        DataScopeType type = fromCode(code);
        return type == null ? defaultValue : type;
    }

    /**
     * 按枚举名解析类型（忽略大小写）。
     *
     * @param name 枚举名（如 {@code dept}、{@code DEPT_AND_CHILD}）
     * @return 匹配的类型；无匹配或输入为 {@code null} 时返回 {@code null}
     */
    public static DataScopeType fromName(String name) {
        if (name == null) {
            return null;
        }
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
