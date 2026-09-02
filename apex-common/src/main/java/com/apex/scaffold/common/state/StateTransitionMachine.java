package com.apex.scaffold.common.state;

import com.apex.scaffold.common.error.ErrorCode;
import com.apex.scaffold.common.exception.BusinessException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 通用业务状态迁移器。
 *
 *
 *@param <S> 状态类型
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public final class StateTransitionMachine<S> {

    private final Map<S, Set<S>> transitions;

    private StateTransitionMachine(Map<S, Set<S>> transitions) {
        Map<S, Set<S>> immutableTransitions = new HashMap<>(transitions.size());
        transitions.forEach((source, targets) -> immutableTransitions.put(source, Set.copyOf(targets)));
        this.transitions = Collections.unmodifiableMap(immutableTransitions);
    }

    /**
     * 创建状态迁移器构建器。
     *
     * @param <S> 状态类型
     * @return 空配置的 {@link Builder} 实例
     */
    public static <S> Builder<S> builder() {
        return new Builder<>();
    }

    /**
     * 判断是否允许从 {@code source} 迁移到 {@code target}。
     *
     * @param source 源状态（不允许为 {@code null}）
     * @param target 目标状态（不允许为 {@code null}）
     * @return {@code true} 表示迁移合法
     */
    public boolean canTransition(S source, S target) {
        return source != null
                && target != null
                && transitions.getOrDefault(source, Collections.emptySet()).contains(target);
    }

    /**
     * 执行状态迁移并返回目标状态。
     *
     * @param source 源状态
     * @param target 目标状态
     * @return 目标状态
     * @throws BusinessException 当迁移不合法（未在 {@link #builder()} 中登记）时抛出
     */
    public S transition(S source, S target) {
        if (!canTransition(source, target)) {
            throw new BusinessException(
                    ErrorCode.OPERATION_NOT_ALLOWED.getCode(), "不允许从状态 " + source + " 流转到 " + target);
        }
        return target;
    }

    /**
     * 状态迁移规则构建器。
     *
     * @param <S> 状态类型
     */
    public static final class Builder<S> {

        private final Map<S, Set<S>> transitions = new HashMap<>();

        /**
         * 登记一条允许的状态迁移规则。
         *
         * @param source 源状态（不允许为 {@code null}）
         * @param target 目标状态（不允许为 {@code null}，且不得与源状态相同）
         * @return 当前构建器，支持链式调用
         * @throws IllegalArgumentException 当参数为 {@code null} 或源目标状态相同时抛出
         */
        public Builder<S> allow(S source, S target) {
            if (source == null || target == null) {
                throw new IllegalArgumentException("状态不能为空");
            }
            if (source.equals(target)) {
                throw new IllegalArgumentException("不允许定义相同状态的迁移");
            }
            transitions.computeIfAbsent(source, ignored -> new HashSet<>()).add(target);
            return this;
        }

        /**
         * 构建不可变的 {@link StateTransitionMachine}。
         *
         * @return 状态迁移器实例
         */
        public StateTransitionMachine<S> build() {
            return new StateTransitionMachine<>(transitions);
        }
    }
}
