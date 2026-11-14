package com.apex.scaffold.office.domain.approval;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 审批操作类型枚举
 *
 * <p>定义审批流程中支持的所有操作类型，包括同意、拒绝、转交、加签等操作。
 * 不同办公平台支持的操作类型可能有所差异，需要在具体实现中进行适配。
 *
 * @author Apex Scaffold
 * @since 1.0.0
 */
@Getter
@RequiredArgsConstructor
public enum ApprovalAction {

    /** 同意审批 */
    APPROVE("同意"),

    /** 拒绝审批 */
    REJECT("拒绝"),

    /** 转交给其他人审批 */
    TRANSFER("转交"),

    /** 加签（添加额外审批人） */
    ADD_SIGN("加签"),

    /** 撤回审批 */
    RECALL("撤回");

    /** 操作说明 */
    private final String description;
}
