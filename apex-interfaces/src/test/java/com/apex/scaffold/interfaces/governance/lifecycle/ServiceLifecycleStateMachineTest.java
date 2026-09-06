package com.apex.scaffold.interfaces.governance.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 服务生命周期状态机测试。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("ServiceLifecycleStateMachine 单元测试")
class ServiceLifecycleStateMachineTest {

    /**
     * 验证状态机仅允许定义的状态迁移。
     */
    @Test
    @DisplayName("应允许合法迁移并拒绝非法迁移")
    void shouldAllowValidTransitionsAndRejectInvalidTransitions() {
        ServiceLifecycleStateMachine stateMachine = new ServiceLifecycleStateMachine();

        assertThat(stateMachine.current()).isEqualTo(ServiceLifecycleState.STARTING);
        assertThat(stateMachine.transitionTo(ServiceLifecycleState.READY)).isTrue();
        assertThat(stateMachine.transitionTo(ServiceLifecycleState.DRAINING)).isTrue();
        assertThat(stateMachine.transitionTo(ServiceLifecycleState.READY)).isFalse();
        assertThat(stateMachine.current()).isEqualTo(ServiceLifecycleState.DRAINING);
    }

    /**
     * 验证迁移到当前状态具备幂等性。
     */
    @Test
    @DisplayName("迁移到当前状态应保持幂等")
    void shouldBeIdempotentWhenTargetIsCurrentState() {
        ServiceLifecycleStateMachine stateMachine = new ServiceLifecycleStateMachine();

        assertThat(stateMachine.transitionTo(ServiceLifecycleState.STARTING)).isTrue();
        assertThat(stateMachine.current()).isEqualTo(ServiceLifecycleState.STARTING);
    }
}
