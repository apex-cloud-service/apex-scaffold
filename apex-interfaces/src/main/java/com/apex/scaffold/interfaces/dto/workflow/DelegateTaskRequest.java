package com.apex.scaffold.interfaces.dto.workflow;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 工作流任务委派请求 DTO。
 *
 * <p>用于将当前待办任务委派给其他用户处理。委派后原任务执行人仍可查看任务，
 * 受托人完成任务后会自动回到委派人手中继续处理。
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>临时授权：审批人临时外出，委派给代理人审批</li>
 *   <li>专业咨询：需要专业人员提供意见后再做决策</li>
 *   <li>协同处理：需要其他人员协助完成某个环节</li>
 * </ul>
 *
 * <h2>字段说明</h2>
 * <ul>
 *   <li>delegateToId：受托人用户 ID，必须是系统中的有效用户</li>
 *   <li>reason：委派说明，用于记录委派原因，最多 500 字</li>
 * </ul>
 *
 * <h2>委派 vs 转办</h2>
 * <ul>
 *   <li>委派：受托人处理完成后任务回到委派人，委派人需要继续处理</li>
 *   <li>转办：任务直接转给新执行人，原执行人不再参与后续处理</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.0
 * @since 1.3.0
 */
@Data
@Schema(description = "工作流任务委派请求")
public class DelegateTaskRequest {

    @NotNull(message = "受托人ID不能为空")
    @Schema(description = "受托人用户 ID，必须是系统中的有效用户，受托人处理完成后任务回到委派人", example = "10002", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long delegateToId;

    @NotBlank(message = "委派说明不能为空")
    @Size(max = 500, message = "委派说明不能超过500字")
    @Schema(description = "委派说明，用于记录委派原因，最多 500 字", example = "本人出差，委派张三代为审批", requiredMode = Schema.RequiredMode.REQUIRED)
    private String reason;
}
