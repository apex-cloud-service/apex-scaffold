package com.apex.scaffold.interfaces.dto.office.approval;

import com.apex.scaffold.domain.office.OfficePlatform;
import com.apex.scaffold.domain.office.approval.ApprovalRecord;
import com.apex.scaffold.domain.office.approval.ApprovalStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审批流程响应DTO
 *
 * <p>返回审批流程的完整信息给前端。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "审批流程响应")
public class ApprovalProcessResponse {
    
    /**
     * 流程ID
     */
    @Schema(description = "流程ID", example = "1")
    private Long id;
    
    /**
     * 办公平台类型
     */
    @Schema(description = "办公平台类型", example = "FEISHU")
    private OfficePlatform platform;
    
    /**
     * 流程定义ID
     */
    @Schema(description = "流程定义ID", example = "approval_template_001")
    private String processDefinitionId;
    
    /**
     * 流程实例ID
     */
    @Schema(description = "流程实例ID", example = "inst_001")
    private String processInstanceId;
    
    /**
     * 平台流程实例ID
     */
    @Schema(description = "平台流程实例ID", example = "feishu_inst_123456")
    private String platformInstanceId;
    
    /**
     * 流程名称
     */
    @Schema(description = "流程名称", example = "请假申请")
    private String processName;
    
    /**
     * 发起人ID
     */
    @Schema(description = "发起人ID", example = "user_001")
    private String initiatorId;
    
    /**
     * 发起人姓名
     */
    @Schema(description = "发起人姓名", example = "张三")
    private String initiatorName;
    
    /**
     * 审批状态
     */
    @Schema(description = "审批状态", example = "IN_PROGRESS")
    private ApprovalStatus status;
    
    /**
     * 当前审批节点
     */
    @Schema(description = "当前审批节点", example = "部门主管审批")
    private String currentNode;
    
    /**
     * 当前审批人列表
     */
    @Schema(description = "当前审批人ID列表")
    private List<String> currentApproverIds;
    
    /**
     * 表单数据
     */
    @Schema(description = "表单数据（JSON格式）")
    private String formData;
    
    /**
     * 审批记录列表
     */
    @Schema(description = "审批记录列表")
    private List<ApprovalRecord> records;
    
    /**
     * 创建时间
     */
    @Schema(description = "创建时间", example = "2024-01-15T10:00:00")
    private LocalDateTime createdAt;
    
    /**
     * 完成时间
     */
    @Schema(description = "完成时间", example = "2024-01-15T15:30:00")
    private LocalDateTime completedAt;
    
    /**
     * 更新时间
     */
    @Schema(description = "更新时间", example = "2024-01-15T14:20:00")
    private LocalDateTime updatedAt;
}
