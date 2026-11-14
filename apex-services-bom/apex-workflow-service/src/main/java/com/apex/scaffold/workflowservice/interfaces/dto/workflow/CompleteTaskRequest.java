package com.apex.scaffold.workflowservice.interfaces.dto.workflow;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 完成工作流任务请求 DTO。
 *
 * <p>用于完成当前待办任务，推进流程到下一个节点。
 * 任务执行人可以在完成任务时填写审批意见或处理备注。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>审批通过：审批人同意流程继续执行</li>
 *   <li>审批拒绝：审批人拒绝并填写拒绝原因</li>
 *   <li>任务处理：执行人完成任务并填写处理结果</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>comment：审批意见或处理备注，用于流程追溯和审计，最多 1000 字</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.0
 * @since 1.3.0
 */
@Data
@Schema(description = "完成工作流任务请求")
public class CompleteTaskRequest {

    @Size(max = 1000, message = "审批意见不能超过1000字")
    @Schema(description = "审批意见或处理备注，用于流程追溯和审计，最多 1000 字", example = "同意，请按计划推进后续节点")
    private String comment;
}
