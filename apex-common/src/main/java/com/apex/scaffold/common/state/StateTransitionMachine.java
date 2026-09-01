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
 * @param <S> 状态类型
 * @author Apex Team
 */
public final class StateTransitionMachine<S> {

    private final Map<S, Set<S>> transitions;

    private StateTransitionMachine(Map<S, Set<S>> transitions) {
        Map<S, Set<S>> immutableTransitions = new HashMap<>(transitions.size());
        transitions.forEach((source, targets) -> immutableTransitions.put(source, Set.copyOf(targets)));
        this.transitions = Collections.unmodifiableMap(immutableTransitions);
    }

    public static <S> Builder<S> builder() {
        return new Builder<>();
    }

    public boolean canTransition(S source, S target) {
        return source != null
                && target != null
                && transitions.getOrDefault(source, Collections.emptySet()).contains(target);
    }

    public S transition(S source, S target) {
        if (!canTransition(source, target)) {
            throw new BusinessException(
                    ErrorCode.OPERATION_NOT_ALLOWED.getCode(), "不允许从状态 " + source + " 流转到 " + target);
        }
        return target;
    }

    public static final class Builder<S> {

        private final Map<S, Set<S>> transitions = new HashMap<>();

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

        public StateTransitionMachine<S> build() {
            return new StateTransitionMachine<>(transitions);
        }
    }
}
