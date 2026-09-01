package com.apex.scaffold.generator;

import lombok.Data;

/**
 * 状态迁移定义（代码生成器输入模型）。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
public class StateTransitionDefinition {

    private String from;

    private String to;
}
