package com.apex.scaffold.infrastructure.persistence.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import java.util.Collection;
import java.util.function.BiConsumer;
import org.springframework.util.StringUtils;

/**
 * 增强型 LambdaQueryWrapper。
 *
 * <p>扩展 MyBatis-Plus 的 {@link LambdaQueryWrapper}，提供 {@code ifPresent} 系列条件方法，
 * 仅在值存在（非 null、非空白字符串、非空集合）时追加查询条件，使链式调用更优雅，
 * 避免业务层手写大量 {@code if (val != null)} 样板代码。
 *
 * <h2>典型用法</h2>
 * <pre>{@code
 *   ApexLambdaQueryWrapper<UserPO> wrapper = ConditionUtil.lambda(UserPO.class)
 *       .eqIfPresent(UserPO::getId, id)
 *       .likeIfPresent(UserPO::getNickname, keyword)
 *       .betweenIfPresent(UserPO::getCreatedAt, start, end);
 * }</pre>
 *
 * @param <T> 实体类型
 * @author Apex Team
 * @version 1.1.0
 * @since 1.0.0
 */
public class ApexLambdaQueryWrapper<T> extends LambdaQueryWrapper<T> {

    /**
     * 如果值不为 null，则执行等于查询。
     *
     * @param column 数据库列（SFunction 形式）
     * @param val    查询值；为 null 时不追加条件
     * @return 当前 wrapper 实例，便于链式调用
     */
    public ApexLambdaQueryWrapper<T> eqIfPresent(SFunction<T, ?> column, Object val) {
        return (ApexLambdaQueryWrapper<T>) super.eq(val != null, column, val);
    }

    /**
     * 如果值不为 null，则执行不等于查询。
     *
     * <p>用于"排除自身"的唯一性校验场景，例如更新时校验编码唯一性
     * 需排除当前记录自身。</p>
     *
     * @param column 数据库列（SFunction 形式）
     * @param val    查询值；为 null 时不追加条件
     * @return 当前 wrapper 实例，便于链式调用
     */
    public ApexLambdaQueryWrapper<T> neIfPresent(SFunction<T, ?> column, Object val) {
        return (ApexLambdaQueryWrapper<T>) super.ne(val != null, column, val);
    }

    /**
     * 如果字符串不为空（非 null 且长度大于 0），则执行等于查询。
     *
     * @param column 数据库列
     * @param val    查询字符串；空白时不追加条件
     * @return 当前 wrapper 实例
     */
    public ApexLambdaQueryWrapper<T> eqIfPresent(SFunction<T, ?> column, String val) {
        return (ApexLambdaQueryWrapper<T>) super.eq(StringUtils.hasText(val), column, val);
    }

    /**
     * 如果字符串不为空，则执行模糊查询（左右模糊）。
     *
     * @param column 数据库列
     * @param val    查询字符串；空白时不追加条件
     * @return 当前 wrapper 实例
     */
    public ApexLambdaQueryWrapper<T> likeIfPresent(SFunction<T, ?> column, String val) {
        return (ApexLambdaQueryWrapper<T>) super.like(StringUtils.hasText(val), column, val);
    }

    /**
     * 如果字符串不为空，则执行右模糊查询（前缀匹配）。
     *
     * @param column 数据库列
     * @param val    查询字符串；空白时不追加条件
     * @return 当前 wrapper 实例
     */
    public ApexLambdaQueryWrapper<T> likeRightIfPresent(SFunction<T, ?> column, String val) {
        return (ApexLambdaQueryWrapper<T>) super.likeRight(StringUtils.hasText(val), column, val);
    }

    /**
     * 如果集合不为空（非 null 且 size > 0），则执行 IN 查询。
     *
     * @param column 数据库列
     * @param coll   查询集合；为 null 或空时不追加条件
     * @return 当前 wrapper 实例
     */
    public ApexLambdaQueryWrapper<T> inIfPresent(SFunction<T, ?> column, Collection<?> coll) {
        return (ApexLambdaQueryWrapper<T>) super.in(coll != null && !coll.isEmpty(), column, coll);
    }

    /**
     * 如果两个端点都不为 null，则执行 BETWEEN 查询。
     *
     * @param column 数据库列
     * @param val1   起始值
     * @param val2   结束值
     * @return 当前 wrapper 实例
     */
    public ApexLambdaQueryWrapper<T> betweenIfPresent(SFunction<T, ?> column, Object val1, Object val2) {
        return (ApexLambdaQueryWrapper<T>) super.between(val1 != null && val2 != null, column, val1, val2);
    }

    /**
     * 泛型 ifPresent 支持，允许自定义条件追加逻辑。
     *
     * <p>根据值类型智能判定是否触发：
     * <ul>
     *   <li>{@link CharSequence}：{@link StringUtils#hasText} 为真才触发；</li>
     *   <li>{@link Collection}：非空才触发；</li>
     *   <li>其他类型：非 null 即触发。</li>
     * </ul>
     *
     * @param <V>      值类型
     * @param val      待检查的值
     * @param consumer 当值存在时执行的逻辑；第一个参数为当前 wrapper，第二个参数为存在的值
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
