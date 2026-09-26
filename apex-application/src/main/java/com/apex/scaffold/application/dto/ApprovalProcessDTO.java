package com.apex.scaffold.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审批流程 DTO。
 *
 * <p>办公集成业务域的审批流程数据载体，承载流程实例标识、审批模板、审批标题、
 * 发起人、审批状态、表单数据与审批记录等信息，用于 {@code OfficeIntegrationService}
 * 审批流程管理用例中实体与服务层之间的数据传输。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "审批流程DTO")
public class ApprovalProcessDTO {

    @Schema(description = "流程ID")
    private Long id;

    @Schema(description = "办公平台类型")
    private String platform;

    @Schema(description = "平台实例ID")
    private String platformInstanceId;

    @Schema(description = "业务流程实例ID")
    private String processInstanceId;

    @Schema(description = "审批模板ID")
    private String templateId;

    @Schema(description = "审批标题", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(description = "发起人ID")
    private String initiatorId;

    @Schema(description = "发起人姓名")
    private String initiatorName;

    @Schema(description = "审批状态")
    private String status;

    @Schema(description = "表单数据JSON")
    private String formData;

    @Schema(description = "审批记录JSON")
    private String records;

    @Schema(description = "当前审批人IDs")
    private String currentApproverIds;

    @Schema(description = "发起时间")
    private LocalDateTime initiateTime;

    @Schema(description = "完成时间")
    private LocalDateTime completeTime;
}
