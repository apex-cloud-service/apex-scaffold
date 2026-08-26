package com.apex.scaffold.common.annotation;

/**
 * 逻辑关系枚举
 *
 * <p>用于权限校验注解 {@code @RequiresPermissions} 中，定义多个权限条件之间的逻辑关系：
 * <ul>
 *   <li>AND - 必须满足所有权限条件</li>
 *   <li>OR - 满足任意一个权限条件即可</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public enum Logical {
    /** 与 - 必须满足所有条件 */
    AND,

    /** 或 - 满足任意一个条件即可 */
    OR
}
