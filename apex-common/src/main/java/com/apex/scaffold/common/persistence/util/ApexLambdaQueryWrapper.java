package com.apex.scaffold.common.persistence.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import java.util.Collection;
import java.util.function.BiConsumer;
import org.springframework.util.StringUtils;

/**
 * 增强型 LambdaQueryWrapper。
 *
 * <p>扩展 MyBatis-Plus 的 {@link LambdaQueryWrapper}，提供按值存在性追加查询条件的能力，
 * 统一处理 null、空白字符串和空集合等常见筛选参数。
 *
 * @param <T> 实体类型
 * @author Apex Team
 * @version 1.1.0
 * @since 1.0.0
 */
public class ApexLambdaQueryWrapper<T> extends LambdaQueryWrapper<T> {

    /**
     * 如果值不为 null，则追加等于条件。
     *
     * @param column 数据库列
     * @param val 查询值
     * @return 当前 wrapper 实例
     */
    public ApexLambdaQueryWrapper<T> eqIfPresent(SFunction<T, ?> column, Object val) {
        return (ApexLambdaQueryWrapper<T>) super.eq(val != null, column, val);
    }

    /**
     * 如果值不为 null，则追加不等于条件。
     *
     * @param column 数据库列
     * @param val 查询值
     * @return 当前 wrapper 实例
     */
    public ApexLambdaQueryWrapper<T> neIfPresent(SFunction<T, ?> column, Object val) {
        return (ApexLambdaQueryWrapper<T>) super.ne(val != null, column, val);
    }

    /**
     * 如果字符串包含有效文本，则追加等于条件。
     *
     * @param column 数据库列
     * @param val 查询字符串
     * @return 当前 wrapper 实例
     */
    public ApexLambdaQueryWrapper<T> eqIfPresent(SFunction<T, ?> column, String val) {
        return (ApexLambdaQueryWrapper<T>) super.eq(StringUtils.hasText(val), column, val);
    }

    /**
     * 如果字符串包含有效文本，则追加模糊查询条件。
     *
     * @param column 数据库列
     * @param val 查询字符串
     * @return 当前 wrapper 实例
     */
    public ApexLambdaQueryWrapper<T> likeIfPresent(SFunction<T, ?> column, String val) {
        return (ApexLambdaQueryWrapper<T>) super.like(StringUtils.hasText(val), column, val);
    }

    /**
     * 如果字符串包含有效文本，则追加右模糊查询条件。
     *
     * @param column 数据库列
     * @param val 查询字符串
     * @return 当前 wrapper 实例
     */
    public ApexLambdaQueryWrapper<T> likeRightIfPresent(SFunction<T, ?> column, String val) {
        return (ApexLambdaQueryWrapper<T>) super.likeRight(StringUtils.hasText(val), column, val);
    }

    /**
     * 如果集合非空，则追加 IN 条件。
     *
     * @param column 数据库列
     * @param coll 查询集合
     * @return 当前 wrapper 实例
     */
    public ApexLambdaQueryWrapper<T> inIfPresent(SFunction<T, ?> column, Collection<?> coll) {
        return (ApexLambdaQueryWrapper<T>) super.in(coll != null && !coll.isEmpty(), column, coll);
    }

    /**
     * 如果起止值均存在，则追加 BETWEEN 条件。
     *
     * @param column 数据库列
     * @param val1 起始值
     * @param val2 结束值
     * @return 当前 wrapper 实例
     */
    public ApexLambdaQueryWrapper<T> betweenIfPresent(SFunction<T, ?> column, Object val1, Object val2) {
        return (ApexLambdaQueryWrapper<T>) super.between(val1 != null && val2 != null, column, val1, val2);
    }

    /**
     * 根据值类型判断是否执行自定义条件逻辑。
     *
     * @param <V> 值类型
     * @param val 待检查的值
     * @param consumer 值存在时执行的条件逻辑
     * @return 当前 wrapper 实例
     */
    public <V> ApexLambdaQueryWrapper<T> ifPresent(V val, BiConsumer<ApexLambdaQueryWrapper<T>, V> consumer) {
        if (val != null) {
            if (val instanceof String s) {
                if (StringUtils.hasText(s)) {
                    consumer.accept(this, val);
                }
            } else if (val instanceof Collection<?> c) {
                if (!c.isEmpty()) {
                    consumer.accept(this, val);
                }
            } else {
                consumer.accept(this, val);
            }
        }
        return this;
    }
}
