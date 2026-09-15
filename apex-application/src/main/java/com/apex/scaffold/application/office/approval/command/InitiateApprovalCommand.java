package com.apex.scaffold.application.office.approval.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发起审批命令
 *
 * <p>用于封装发起审批的请求参数。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitiateApprovalCommand {
    
    /**
     * 租户ID
     */
    private Long tenantId;
    
    /**
     * 办公平台类型
     */
    private String platform;
    
    /**
     * 应用ID
     */
    private Long appId;
    
    /**
     * 平台审批定义ID
     */
    private String platformApprovalCode;
    
    /**
     * 审批标题
     */
    private String title;
    
    /**
     * 发起人用户ID
     */
    private String initiatorUserId;
    
    /**
     * 审批表单数据（JSON格式）
     */
    private String formData;
}
