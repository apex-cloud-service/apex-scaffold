package com.apex.scaffold.domain.office.approval;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审批记录值对象
 *
 * <p>表示审批流程中的单个审批操作记录，记录审批人、审批动作、审批意见和审批时间等信息。
 * 一个审批流程可能包含多条审批记录，按时间顺序组成完整的审批链路。
 *
 * @author Apex Scaffold
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalRecord {

    /** 审批人ID（在办公平台中的用户ID） */
    private String approverId;

    /** 审批人姓名 */
    private String approverName;

    /** 审批动作 */
    private ApprovalAction action;

    /** 审批意见 */
    private String comment;

    /** 审批时间 */
    private LocalDateTime approvalTime;

    /** 是否为当前审批节点 */
    private Boolean isCurrent;

    /** 审批节点序号（第几个审批节点） */
    private Integer nodeOrder;
}
