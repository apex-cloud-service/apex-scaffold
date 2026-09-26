package com.apex.scaffold.interfaces.dto.workflow;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发起工作流实例请求 DTO。
 *
 * <p>用于启动一个新的工作流流程实例，将业务数据与流程引擎关联。
 * 每个流程实例通过唯一的业务主键（businessKey）标识，支持携带流程上下文数据。
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>请假申请：员工发起请假流程，businessKey 为请假单编号</li>
 *   <li>采购审批：发起采购流程，businessKey 为采购单编号</li>
 *   <li>合同审批：发起合同审批流程，businessKey 为合同编号</li>
 * </ul>
 *
 * <h2>字段说明</h2>
 * <ul>
 *   <li>businessKey：业务主键，用于关联业务数据，最多 200 字符</li>
 *   <li>contextJson：流程上下文数据（JSON 格式），包含流程所需的业务参数，最多 10000 字符</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.0
 * @since 1.3.0
 */
@Data
@Schema(description = "发起工作流实例请求")
public class StartProcessInstanceRequest {

    @NotBlank(message = "业务主键不能为空")
    @Size(max = 200, message = "业务主键不能超过200字符")
    @Schema(description = "业务主键，用于关联业务数据（如请假单编号、采购单编号、合同编号），最多 200 字符", example = "LEAVE-20260926-0001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String businessKey;

    @Size(max = 10000, message = "流程上下文不能超过10000字符")
    @Schema(description = "流程上下文数据（JSON 格式），包含流程流转所需的业务参数，最多 10000 字符", example = "{\"days\":3,\"leaveType\":\"annual\"}")
    private String contextJson;
}
