package com.apex.scaffold.workflow.domain.model;

/**
 * 工作流定义生命周期状态。
 *
 * <p>定义从草稿开始，经过结构校验后发布为不可变版本；已发布版本可以被停用，
 * 停用只阻止新的流程发起，不影响已经运行中的实例。</p>
 *
 * <ul>
 *   <li>{@link #DRAFT}：草稿，可编辑，不允许被实例化。</li>
 *   <li>{@link #PUBLISHED}：已发布，内容不可变，可被实例化。</li>
 *   <li>{@link #DISABLED}：已停用，内容不可变，阻止新的实例化。</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public enum WorkflowDefinitionStatus {

    /** 草稿状态。 */
    DRAFT,

    /** 已发布状态。 */
    PUBLISHED,

    /** 已停用状态。 */
    DISABLED;

    /**
     * 将持久化的状态文本转换为枚举。
     *
     * @param value 状态文本，空值按 {@link #DRAFT} 处理
     * @return 对应的状态枚举
     * @throws IllegalArgumentException 当文本不是合法状态时
     */
    public static WorkflowDefinitionStatus from(String value) {
        if (value == null || value.isBlank()) {
            return DRAFT;
        }
        return WorkflowDefinitionStatus.valueOf(value.trim().toUpperCase());
    }
}
