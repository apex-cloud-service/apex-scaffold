package com.apex.scaffold.infrastructure.office.client;

import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 办公平台审批客户端接口
 *
 * <p>定义与各办公平台审批API交互的统一接口，屏蔽不同平台的API差异。
 * 支持飞书、钉钉、企业微信等多个办公平台的审批功能对接。
 *
 * <p>实现类需要处理：
 * <ul>
 *   <li>访问令牌的获取和刷新</li>
 *   <li>API请求的构建和发送</li>
 *   <li>响应结果的解析和错误处理</li>
 *   <li>平台特有参数的适配</li>
 * </ul>
 *
 * @author Apex Scaffold
 * @since 1.0.0
 */
public interface ApprovalClient {

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
            String templateId,
            String initiatorId,
            String[] approverIds,
            Map<String, Object> formData,
            String title
    );

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
    Mono<Boolean> approveProcess(
            String platformInstanceId,
            String approverId,
            String action,
            String comment
    );

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
