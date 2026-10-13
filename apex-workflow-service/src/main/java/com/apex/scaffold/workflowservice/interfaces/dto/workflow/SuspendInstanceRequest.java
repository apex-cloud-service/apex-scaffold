package com.apex.scaffold.workflowservice.interfaces.dto.workflow;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 挂起工作流实例请求 DTO。
 *
 * <p>用于暂停流程实例的执行，挂起后所有待办任务将被冻结，无法继续流转。
 * 适用于需要临时暂停流程、等待外部条件满足的场景。
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>等待资料：流程需要等待补充资料后才能继续</li>
 *   <li>异常处理：发现流程异常，需要暂停处理</li>
 *   <li>外部依赖：等待外部系统或第三方审批结果</li>
 *   <li>业务变更：业务规则变更，需要暂停流程重新评估</li>
 * </ul>
 *
 * <h2>字段说明</h2>
 * <ul>
 *   <li>reason：挂起原因，用于记录挂起说明和审计追溯，最多 500 字</li>
 * </ul>
 *
 * <h2>注意事项</h2>
 * <ul>
 *   <li>挂起的流程实例需要通过恢复接口才能继续执行</li>
 *   <li>挂起期间不会影响已完成的任务记录</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.0
 * @since 1.3.0
 */
@Data
@Schema(description = "挂起工作流实例请求")
public class SuspendInstanceRequest {

    @NotBlank(message = "挂起原因不能为空")
    @Size(max = 500, message = "挂起原因不能超过500字")
    @Schema(
            description = "挂起原因，用于记录挂起说明和审计追溯，最多 500 字",
            example = "等待外部审批结果，暂时挂起流程",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String reason;
}
