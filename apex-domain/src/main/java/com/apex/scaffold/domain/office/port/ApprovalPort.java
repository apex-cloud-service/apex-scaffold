package com.apex.scaffold.domain.office.port;

import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * 审批端口接口
 *
 * <p>定义审批领域的外部依赖接口，遵循端口-适配器架构模式。
 * 该接口由 Domain 层定义，由 Infrastructure 层实现，Application 层使用。
 *
 * <p>这是一个出站端口（Outbound Port），用于与外部办公平台的审批系统交互。
 * 通过依赖倒置原则，Application 层依赖此接口而非具体实现，保持架构清晰。
 *
 * @author Apex Scaffold
 * @since 1.2.0
 */
public interface ApprovalPort {

    /**
     * 发起审批流程
     *
     * @param templateId 审批模板ID
     * @param initiatorId 发起人ID（在办公平台中的用户ID）
     * @param approverIds 审批人ID列表（在办公平台中的用户ID）
     * @param formData 审批表单数据
     * @param title 审批标题
     * @return 平台返回的审批实例ID
     */
    Mono<String> createApproval(
            String templateId, String initiatorId, String[] approverIds, Map<String, Object> formData, String title);

    /**
     * 查询审批状态
     *
     * @param platformInstanceId 平台中的审批实例ID
     * @return 审批状态（PENDING/IN_PROGRESS/APPROVED/REJECTED/CANCELLED）
     */
    Mono<String> getApprovalStatus(String platformInstanceId);

    /**
     * 执行审批操作（同意/拒绝）
     *
     * @param platformInstanceId 平台中的审批实例ID
     * @param approverId 审批人ID
     * @param action 审批动作（APPROVE/REJECT）
     * @param comment 审批意见
     * @return 操作是否成功
     */
    Mono<Boolean> approveProcess(String platformInstanceId, String approverId, String action, String comment);

    /**
     * 撤销审批流程
     *
     * @param platformInstanceId 平台中的审批实例ID
     * @param initiatorId 发起人ID
     * @return 撤销是否成功
     */
    Mono<Boolean> cancelApproval(String platformInstanceId, String initiatorId);

    /**
     * 查询审批详情
     *
     * @param platformInstanceId 平台中的审批实例ID
     * @return 审批详情（包含审批记录、当前审批人等信息）
     */
    Mono<Map<String, Object>> getApprovalDetail(String platformInstanceId);

    /**
     * 获取支持的平台类型
     *
     * @return 平台类型标识（feishu/dingtalk/wecom）
     */
    String getPlatformType();
}
