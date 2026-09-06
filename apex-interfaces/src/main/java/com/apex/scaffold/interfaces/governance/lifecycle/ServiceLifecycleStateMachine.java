package com.apex.scaffold.interfaces.governance.lifecycle;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 服务生命周期状态机。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public final class ServiceLifecycleStateMachine {
    private static final Map<ServiceLifecycleState, Set<ServiceLifecycleState>> TRANSITIONS = transitions();
    private final AtomicReference<ServiceLifecycleState> state = new AtomicReference<>(ServiceLifecycleState.STARTING);

    /**
     * 返回当前状态。
     *
     * @return 当前状态
     */
    public ServiceLifecycleState current() {
        return state.get();
    }

    /**
     * 尝试迁移到目标状态。
     *
     * @param target 目标状态
     * @return 是否完成迁移或目标已是当前状态
     */
    public boolean transitionTo(ServiceLifecycleState target) {
        return state.get() == target
                || state.updateAndGet(current -> {
                            if (TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
                                return target;
                            }
                            return current;
                        })
                        == target;
    }

    private static Map<ServiceLifecycleState, Set<ServiceLifecycleState>> transitions() {
        EnumMap<ServiceLifecycleState, Set<ServiceLifecycleState>> map = new EnumMap<>(ServiceLifecycleState.class);
        map.put(ServiceLifecycleState.STARTING, EnumSet.of(ServiceLifecycleState.READY, ServiceLifecycleState.FAILED));
        map.put(
                ServiceLifecycleState.READY,
                EnumSet.of(
                        ServiceLifecycleState.DEGRADED,
                        ServiceLifecycleState.OUT_OF_SERVICE,
                        ServiceLifecycleState.DRAINING));
        map.put(
                ServiceLifecycleState.DEGRADED,
                EnumSet.of(
                        ServiceLifecycleState.READY,
                        ServiceLifecycleState.OUT_OF_SERVICE,
                        ServiceLifecycleState.DRAINING));
        map.put(
                ServiceLifecycleState.OUT_OF_SERVICE,
                EnumSet.of(ServiceLifecycleState.READY, ServiceLifecycleState.DRAINING));
        map.put(
                ServiceLifecycleState.DRAINING,
                EnumSet.of(ServiceLifecycleState.STOPPED, ServiceLifecycleState.FAILED));
        return Map.copyOf(map);
    }
}
