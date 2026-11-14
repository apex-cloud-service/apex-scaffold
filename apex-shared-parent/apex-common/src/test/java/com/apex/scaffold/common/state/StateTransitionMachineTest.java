package com.apex.scaffold.common.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apex.scaffold.common.error.ErrorCode;
import com.apex.scaffold.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("StateTransitionMachine 单元测试")
class StateTransitionMachineTest {

    @Test
    @DisplayName("transition - 允许已声明的状态迁移")
    void transition_shouldReturnTargetStateWhenTransitionIsAllowed() {
        StateTransitionMachine<String> machine = StateTransitionMachine.<String>builder()
                .allow("PENDING", "PROCESSING")
                .allow("PROCESSING", "DONE")
                .build();

        assertThat(machine.transition("PENDING", "PROCESSING")).isEqualTo("PROCESSING");
        assertThat(machine.canTransition("PROCESSING", "DONE")).isTrue();
    }

    @Test
    @DisplayName("transition - 拒绝未声明的状态迁移并返回统一错误码")
    void transition_shouldRejectUndeclaredTransition() {
        StateTransitionMachine<String> machine = StateTransitionMachine.<String>builder()
                .allow("PENDING", "PROCESSING")
                .build();

        assertThatThrownBy(() -> machine.transition("PENDING", "DONE"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getCode())
                        .isEqualTo(ErrorCode.OPERATION_NOT_ALLOWED.getCode()));
        assertThat(machine.canTransition("PENDING", "DONE")).isFalse();
    }

    @Test
    @DisplayName("builder - 拒绝空状态和重复状态迁移")
    void builder_shouldRejectInvalidTransitionDefinition() {
        assertThatThrownBy(() -> StateTransitionMachine.<String>builder().allow(null, "DONE"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StateTransitionMachine.<String>builder().allow("PENDING", "PENDING"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
