package com.apex.scaffold.domain.office.approval;

import reactor.core.publisher.Mono;

/**
 * 审批流程领域服务接口
 *
 * <p>定义审批流程相关的领域业务逻辑。
 *
 * @author Apex Team
 * @since 1.3.0
 */
public interface ApprovalProcessDomainService {

    /**
     * 发起审批
     *
     * @param process 审批流程
     * @return 发起后的审批流程
     */
    Mono<ApprovalProcess> initiateApproval(ApprovalProcess process);

    /**
     * 同步审批状态
     *
     * @param processId 审批流程ID
     * @return 同步后的审批流程
     */
    Mono<ApprovalProcess> syncApprovalStatus(Long processId);

    /**
     * 撤销审批
     *
     * @param processId 审批流程ID
     * @param reason 撤销原因
     * @return 撤销后的审批流程
     */
    Mono<ApprovalProcess> cancelApproval(Long processId, String reason);

    /**
     * 催办审批
     *
     * @param processId 审批流程ID
     * @return 催办结果
     */
    Mono<Boolean> urgeApproval(Long processId);
}
