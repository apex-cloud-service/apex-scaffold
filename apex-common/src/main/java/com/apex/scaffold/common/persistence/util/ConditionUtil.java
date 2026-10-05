package com.apex.scaffold.common.persistence.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import java.util.Collection;
import java.util.function.Consumer;

/**
 * MyBatis-Plus 查询条件构建工具。
 *
 * <p>提供统一的条件包装器创建方法，以及仅在筛选值有效时执行条件追加逻辑的辅助方法。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public final class ConditionUtil {

    private ConditionUtil() {}

    /**
     * 创建增强型 Lambda 查询包装器。
     *
     * @param <T> 实体类型
     * @return 新建的查询包装器
     */
    public static <T> ApexLambdaQueryWrapper<T> lambda() {
        return new ApexLambdaQueryWrapper<>();
    }

    /**
     * 创建绑定实体类型的增强型 Lambda 查询包装器。
     *
     * @param entityClass 实体类型
     * @param <T> 实体类型
     * @return 新建的查询包装器
     */
    public static <T> ApexLambdaQueryWrapper<T> lambda(Class<T> entityClass) {
        ApexLambdaQueryWrapper<T> wrapper = new ApexLambdaQueryWrapper<>();
        wrapper.setEntityClass(entityClass);
        return wrapper;
    }

    /**
     * 仅当值非 null 时执行条件追加逻辑。
     *
     * @param wrapper 查询包装器
     * @param value 条件值
     * @param consumer 条件追加逻辑
     * @param <T> 条件值类型
     * @param <R> 实体类型
     * @return 原查询包装器
     */
    public static <T, R> LambdaQueryWrapper<R> ifPresent(
            LambdaQueryWrapper<R> wrapper, T value, Consumer<T> consumer) {
        if (value != null) {
            consumer.accept(value);
        }
        return wrapper;
    }

    /**
     * 仅当字符串非 null 且非空白时执行条件追加逻辑。
     *
     * @param wrapper 查询包装器
     * @param value 字符串条件值
     * @param consumer 条件追加逻辑
     * @param <R> 实体类型
     * @return 原查询包装器
     */
    public static <R> LambdaQueryWrapper<R> ifPresentStr(
            LambdaQueryWrapper<R> wrapper, String value, Consumer<String> consumer) {
        if (value != null && !value.trim().isEmpty()) {
            consumer.accept(value);
        }
        return wrapper;
    }

    /**
     * 仅当条件为 true 时执行条件追加逻辑。
     *
     * @param wrapper 查询包装器
     * @param condition 条件标记
     * @param consumer 条件追加逻辑
     * @param <R> 实体类型
     * @return 原查询包装器
     */
    public static <R> LambdaQueryWrapper<R> ifTrue(
            LambdaQueryWrapper<R> wrapper, Boolean condition, Runnable consumer) {
        if (Boolean.TRUE.equals(condition)) {
            consumer.run();
        }
        return wrapper;
    }

    /**
     * 创建链式条件构建器。
     *
     * @param entityClass 实体类型
     * @param <T> 实体类型
     * @return 条件构建器
     */
    public static <T> ConditionBuilder<T> builder(Class<T> entityClass) {
        return new ConditionBuilder<>(entityClass);
    }

    /**
     * 使用链式 API 按需构造 Lambda 查询条件。
     *
     * @param <T> 实体类型
     * @author Apex Team
     * @version 1.0.0
     * @since 1.0.0
     */
    public static class ConditionBuilder<T> {

        private final LambdaQueryWrapper<T> wrapper;

        /**
         * 创建条件构建器并绑定实体类型。
         *
         * @param entityClass 实体类型
         */
        public ConditionBuilder(Class<T> entityClass) {
            this.wrapper = new LambdaQueryWrapper<>();
            this.wrapper.setEntityClass(entityClass);
        }

        /**
         * 条件值非 null 时追加等于条件。
         *
         * @param value 条件值
         * @param column 数据库列
         * @return 当前构建器
         */
        public ConditionBuilder<T> eqIfPresent(Object value, SFunction<T, ?> column) {
            if (value != null) {
                wrapper.eq(column, value);
            }
            return this;
        }

        /**
         * 字符串非空白时追加等于条件。
         *
         * @param value 字符串条件值
         * @param column 数据库列
         * @return 当前构建器
         */
        public ConditionBuilder<T> eqIfPresentStr(String value, SFunction<T, ?> column) {
            if (value != null && !value.trim().isEmpty()) {
                wrapper.eq(column, value);
            }
            return this;
        }

        /**
         * 字符串非空白时追加模糊查询条件。
         *
         * @param value 字符串条件值
         * @param column 数据库列
         * @return 当前构建器
         */
        public ConditionBuilder<T> likeIfPresent(String value, SFunction<T, ?> column) {
            if (value != null && !value.trim().isEmpty()) {
                wrapper.like(column, value);
            }
            return this;
        }

        /**
         * 字符串非空白时追加右模糊查询条件。
         *
         * @param value 字符串条件值
         * @param column 数据库列
         * @return 当前构建器
         */
        public ConditionBuilder<T> likeRightIfPresent(String value, SFunction<T, ?> column) {
            if (value != null && !value.trim().isEmpty()) {
                wrapper.likeRight(column, value);
            }
            return this;
        }

        /**
         * 集合非空时追加 IN 条件。
         *
         * @param values 条件值集合
         * @param column 数据库列
         * @return 当前构建器
         */
        public ConditionBuilder<T> inIfPresent(Collection<?> values, SFunction<T, ?> column) {
            if (values != null && !values.isEmpty()) {
                wrapper.in(column, values);
            }
            return this;
        }

        /**
         * 按可用端点追加 BETWEEN、GE 或 LE 条件。
         *
         * @param start 起始值
         * @param end 结束值
         * @param column 数据库列
         * @return 当前构建器
         */
        public ConditionBuilder<T> betweenIfPresent(Object start, Object end, SFunction<T, ?> column) {
            if (start != null && end != null) {
                wrapper.between(column, start, end);
            } else if (start != null) {
                wrapper.ge(column, start);
            } else if (end != null) {
                wrapper.le(column, end);
            }
            return this;
        }

        /**
         * 条件值非 null 时追加大于等于条件。
         *
         * @param value 条件值
         * @param column 数据库列
         * @return 当前构建器
         */
        public ConditionBuilder<T> geIfPresent(Object value, SFunction<T, ?> column) {
            if (value != null) {
                wrapper.ge(column, value);
            }
            return this;
        }

        /**
         * 条件值非 null 时追加小于等于条件。
         *
         * @param value 条件值
         * @param column 数据库列
         * @return 当前构建器
         */
        public ConditionBuilder<T> leIfPresent(Object value, SFunction<T, ?> column) {
            if (value != null) {
                wrapper.le(column, value);
            }
            return this;
        }

        /**
         * 条件标记为 true 时执行自定义条件追加逻辑。
         *
         * @param condition 条件标记
         * @param consumer 条件追加逻辑
         * @return 当前构建器
         */
        public ConditionBuilder<T> ifTrue(boolean condition, Consumer<LambdaQueryWrapper<T>> consumer) {
            if (condition) {
                consumer.accept(wrapper);
            }
            return this;
        }

        /**
         * 获取已构造的查询包装器。
         *
         * @return 当前查询包装器
         */
        public LambdaQueryWrapper<T> build() {
            return wrapper;
        }
    }
}
