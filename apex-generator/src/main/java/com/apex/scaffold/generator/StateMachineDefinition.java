package com.apex.scaffold.generator;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 状态机定义（代码生成器输入模型）。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
public class StateMachineDefinition {

    private String field;

    private String initialState;

    private List<StateTransitionDefinition> transitions = new ArrayList<>();
}
