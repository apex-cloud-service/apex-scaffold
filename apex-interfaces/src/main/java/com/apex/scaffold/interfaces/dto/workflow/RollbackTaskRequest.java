package com.apex.scaffold.interfaces.dto.workflow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 工作流任务回退请求 DTO。
 *
 * <p>用于将流程实例中的当前任务回退到指定的历史节点，
 * 支持驳回审批、流程撤回等业务场景。
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>审批驳回：当前审批人驳回到上一审批节点</li>
 *   <li>流程撤回：发起人撤回已提交的流程</li>
 *   <li>异常回退：因业务异常需要回退到特定节点重新处理</li>
 * </ul>
 *
 * <h2>字段说明</h2>
 * <ul>
 *   <li>targetNodeCode：目标回退节点的编码，必须是流程定义中的有效节点</li>
 *   <li>reason：回退原因，用于审计和流程追溯，最多 500 字</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.0
 * @since 1.3.0
 */
@Data
public class RollbackTaskRequest {

    @NotBlank(message = "目标节点编码不能为空")
    private String targetNodeCode;

    @NotBlank(message = "回退原因不能为空")
    @Size(max = 500, message = "回退原因不能超过500字")
    private String reason;
}
