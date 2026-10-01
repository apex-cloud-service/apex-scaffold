package com.apex.scaffold.interfaces.dto.workflow;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 工作流任务转办请求 DTO。
 *
 * <p>用于将当前待办任务转交给其他用户处理。转办后原任务执行人不再负责该任务，
 * 新执行人完成任务后流程继续向下流转。
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>职责变更：原审批人离职或调岗，任务转给新负责人</li>
 *   <li>错误分配：任务分配错误，转给正确的处理人</li>
 *   <li>权限不足：当前执行人无权处理，转给有权限的人员</li>
 * </ul>
 *
 * <h2>字段说明</h2>
 * <ul>
 *   <li>newAssigneeId：新执行人用户 ID，必须是系统中的有效用户</li>
 *   <li>reason：转办原因，用于记录转办说明，最多 500 字</li>
 * </ul>
 *
 * <h2>转办 vs 委派</h2>
 * <ul>
 *   <li>转办：任务直接转给新执行人，原执行人不再参与后续处理</li>
 *   <li>委派：受托人处理完成后任务回到委派人，委派人需要继续处理</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.0
 * @since 1.3.0
 */
@Data
@Schema(description = "工作流任务转办请求")
public class TransferTaskRequest {

    @NotNull(message = "新审批人ID不能为空")
    @Schema(
            description = "新执行人用户 ID，必须是系统中的有效用户，转办后原执行人不再参与后续处理",
            example = "10003",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Long newAssigneeId;

    @NotBlank(message = "转办原因不能为空")
    @Size(max = 500, message = "转办原因不能超过500字")
    @Schema(
            description = "转办原因，用于记录转办说明，最多 500 字",
            example = "原审批人已调岗，转交给新的部门负责人",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String reason;
}
