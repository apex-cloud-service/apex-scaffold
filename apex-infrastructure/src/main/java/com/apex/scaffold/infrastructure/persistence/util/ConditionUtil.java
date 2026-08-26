package com.apex.scaffold.infrastructure.persistence.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.function.Consumer;

/**
 * 条件构建工具类
 *
 * <p>提供ifPresent条件查询机制，只有当值存在时才添加查询条件
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public final class ConditionUtil {

    /** 私有构造方法，防止实例化 */
    private ConditionUtil() {}

    /**
     * 创建增强型 Lambda 查询包装器
     *
     * @param <T> 实体类型
     * @return ApexLambdaQueryWrapper
     */
    public static <T> ApexLambdaQueryWrapper<T> lambda() {
        return new ApexLambdaQueryWrapper<>();
    }

    /**
     * 创建增强型 Lambda 查询包装器（带实体类）
     *
     * @param entityClass 实体类类型
     * @param <T> 实体类型
     * @return ApexLambdaQueryWrapper
     */
    public static <T> ApexLambdaQueryWrapper<T> lambda(Class<T> entityClass) {
        ApexLambdaQueryWrapper<T> wrapper = new ApexLambdaQueryWrapper<>();
        wrapper.setEntityClass(entityClass);
        return wrapper;
    }

    /**
     * 如果值不为null，则添加条件
     *
     * @param wrapper 查询包装器
     * @param value 条件值
     * @param consumer 条件消费者
     * @param <T> 值类型
     * @param <R> 实体类型
     * @return 查询包装器
     */
    public static <T, R> LambdaQueryWrapper<R> ifPresent(LambdaQueryWrapper<R> wrapper, T value, Consumer<T> consumer) {
        if (value != null) {
            consumer.accept(value);
        }
        return wrapper;
    }

    /**
     * 如果字符串不为空，则添加条件
     *
     * @param wrapper 查询包装器
     * @param value 字符串值
     * @param consumer 条件消费者
     * @param <R> 实体类型
     * @return 查询包装器
     */
    public static <R> LambdaQueryWrapper<R> ifPresentStr(
            LambdaQueryWrapper<R> wrapper, String value, Consumer<String> consumer) {
        if (value != null && !value.trim().isEmpty()) {
            consumer.accept(value);
        }
        return wrapper;
    }

    /**
     * 如果条件为true，则添加条件
     *
     * @param wrapper 查询包装器
     * @param condition 条件
     * @param consumer 条件消费者
     * @param <R> 实体类型
     * @return 查询包装器
     */
    public static <R> LambdaQueryWrapper<R> ifTrue(
            LambdaQueryWrapper<R> wrapper, Boolean condition, Runnable consumer) {
        if (Boolean.TRUE.equals(condition)) {
            consumer.run();
        }
        return wrapper;
    }

    /**
     * 创建条件构建器
     *
     * @param entityClass 实体类
     * @param <T> 实体类型
     * @return 条件构建器
     */
    public static <T> ConditionBuilder<T> builder(Class<T> entityClass) {
        return new ConditionBuilder<>(entityClass);
    }

    /**
     * 条件构建器
     *
     * <p>链式构建查询条件
     *
     * @param <T> 实体类型
     */
    public static class ConditionBuilder<T> {

        /** MyBatis-Plus Lambda 查询包装器 */
        private final LambdaQueryWrapper<T> wrapper;

        /**
         * 构造条件构建器
         *
         * @param entityClass 实体类类型
         */
        public ConditionBuilder(Class<T> entityClass) {
            this.wrapper = new LambdaQueryWrapper<>();
        }

        /**
         * 如果值不为null，则添加等于条件
         *
         * @param value 条件值
         * @param column 字段
         * @return 条件构建器
         */
        public ConditionBuilder<T> eqIfPresent(
                Object value, com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> column) {
            if (value != null) {
                wrapper.eq(column, value);
            }
            return this;
        }

        /**
         * 如果字符串不为空，则添加等于条件
         *
         * @param value 字符串值
         * @param column 字段
         * @return 条件构建器
         */
        public ConditionBuilder<T> eqIfPresentStr(
                String value, com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> column) {
            if (value != null && !value.trim().isEmpty()) {
                wrapper.eq(column, value);
            }
            return this;
        }

        /**
         * 如果字符串不为空，则添加模糊查询条件
         *
         * @param value 字符串值
         * @param column 字段
         * @return 条件构建器
         */
        public ConditionBuilder<T> likeIfPresent(
                String value, com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> column) {
            if (value != null && !value.trim().isEmpty()) {
                wrapper.like(column, value);
            }
            return this;
        }

        /**
         * 如果字符串不为空，则添加右模糊查询条件
         *
         * @param value 字符串值
         * @param column 字段
         * @return 条件构建器
         */
        public ConditionBuilder<T> likeRightIfPresent(
                String value, com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> column) {
            if (value != null && !value.trim().isEmpty()) {
                wrapper.likeRight(column, value);
            }
            return this;
        }

        /**
         * 如果值不为null，则添加IN条件
         *
         * @param values 条件值集合
         * @param column 字段
         * @return 条件构建器
         */
        public ConditionBuilder<T> inIfPresent(
                java.util.Collection<?> values, com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> column) {
            if (values != null && !values.isEmpty()) {
                wrapper.in(column, values);
            }
            return this;
        }

        /**
         * 如果值不为null，则添加范围条件
         *
         * @param start 开始值
         * @param end 结束值
         * @param column 字段
         * @return 条件构建器
         */
        public ConditionBuilder<T> betweenIfPresent(
                Object start, Object end, com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> column) {
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
         * 如果值不为null，则添加大于等于条件
         *
         * @param value 条件值
         * @param column 字段
         * @return 条件构建器
         */
        public ConditionBuilder<T> geIfPresent(
                Object value, com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> column) {
            if (value != null) {
                wrapper.ge(column, value);
            }
            return this;
        }

        /**
         * 如果值不为null，则添加小于等于条件
         *
         * @param value 条件值
         * @param column 字段
         * @return 条件构建器
         */
        public ConditionBuilder<T> leIfPresent(
                Object value, com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> column) {
            if (value != null) {
                wrapper.le(column, value);
            }
            return this;
        }

        /**
         * 如果条件为true，则添加条件
         *
         * @param condition 条件
         * @param consumer 条件消费者
         * @return 条件构建器
         */
        public ConditionBuilder<T> ifTrue(
                boolean condition, java.util.function.Consumer<LambdaQueryWrapper<T>> consumer) {
            if (condition) {
                consumer.accept(wrapper);
            }
            return this;
        }

        /**
         * 构建查询包装器
         *
         * @return LambdaQueryWrapper
         */
        public LambdaQueryWrapper<T> build() {
            return wrapper;
        }
    }
}
