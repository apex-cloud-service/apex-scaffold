package com.apex.scaffold.common.annotation;

import java.lang.annotation.*;

/**
 * 操作审计日志注解
 *
 * <p>用于标记需要记录审计日志的方法,配合AOP切面自动记录操作信息。
 * 支持记录操作人、操作时间、操作模块、操作类型、操作描述、请求参数、响应结果等。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    /**
     * 操作模块
     * <p>例如: "用户管理"、"订单管理"、"权限管理"
     */
    String module() default "";

    /**
     * 操作类型
     * <p>例如: CREATE(新增)、UPDATE(修改)、DELETE(删除)、QUERY(查询)、EXPORT(导出)
     */
    OperationType type() default OperationType.OTHER;

    /**
     * 操作描述
     * <p>支持SpEL表达式,例如: "创建用户: #{#user.username}"
     */
    String description() default "";

    /**
     * 是否记录请求参数
     * <p>默认true,敏感接口可设置为false
     */
    boolean recordParams() default true;

    /**
     * 是否记录响应结果
     * <p>默认true,大数据量接口可设置为false
     */
    boolean recordResult() default false;

    /**
     * 忽略的参数名
     * <p>用于排除密码、token等敏感字段
     */
    String[] ignoreParams() default {};
}
