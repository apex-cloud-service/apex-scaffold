package com.apex.scaffold.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 操作日志注解
 *
 * <p>用于记录接口操作日志，支持自动记录请求参数、响应结果、执行时间等信息
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {

    /**
     * 操作模块
     *
     * @return 模块名称
     */
    String module() default "";

    /**
     * 操作类型
     *
     * @return 操作类型
     */
    OperationType type() default OperationType.OTHER;

    /**
     * 操作描述
     *
     * @return 操作描述
     */
    String description() default "";

    /**
     * 是否保存请求参数
     *
     * @return 是否保存
     */
    boolean saveRequest() default true;

    /**
     * 是否保存响应结果
     *
     * @return 是否保存
     */
    boolean saveResponse() default true;

    /**
     * 排除的请求参数字段
     *
     * @return 字段名数组
     */
    String[] excludeRequestFields() default {"password", "oldPassword", "newPassword", "token"};

    /**
     * 排除的响应字段
     *
     * @return 字段名数组
     */
    String[] excludeResponseFields() default {};

    /**
     * 操作类型枚举
     *
     * <p>定义系统支持的所有操作类型。每个枚举值包含中文名称，
     * 编码（code）与枚举名（如 INSERT、UPDATE）保持一致，
     * 调用方可使用 {@link #getCode()} 或 {@link #name()} 获取。
     */
    @Getter
    @RequiredArgsConstructor
    enum OperationType {
        /** 新增 */
        INSERT("新增"),
        /** 修改 */
        UPDATE("修改"),
        /** 删除 */
        DELETE("删除"),
        /** 查询 */
        SELECT("查询"),
        /** 登录 */
        LOGIN("登录"),
        /** 登出 */
        LOGOUT("登出"),
        /** 上传 */
        UPLOAD("上传"),
        /** 下载 */
        DOWNLOAD("下载"),
        /** 导出 */
        EXPORT("导出"),
        /** 导入 */
        IMPORT("导入"),
        /** 其他 */
        OTHER("其他");

        /** 操作类型中文名称 */
        private final String name;

        /**
         * 获取操作类型编码（等同枚举名，如 INSERT、UPDATE）
         *
         * @return 操作类型编码，与 {@link #name()} 相同
         */
        public String getCode() {
            return this.name();
        }
    }
}
