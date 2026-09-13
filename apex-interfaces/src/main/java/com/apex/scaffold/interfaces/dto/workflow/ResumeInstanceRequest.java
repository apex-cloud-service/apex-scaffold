package com.apex.scaffold.interfaces.dto.workflow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 恢复工作流实例请求 DTO。
 *
 * <p>用于恢复已挂起的流程实例，使流程继续执行。
 * 恢复后待办任务将重新激活，流程可以继续流转。
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>资料补齐：等待的资料已补充完成，恢复流程</li>
 *   <li>异常解决：流程异常已处理，恢复正常流转</li>
 *   <li>外部条件满足：等待的外部审批或系统响应已到位</li>
 *   <li>业务确认：业务规则确认完成，恢复流程执行</li>
 * </ul>
 *
 * <h2>字段说明</h2>
 * <ul>
 *   <li>reason：恢复原因，用于记录恢复说明和审计追溯，最多 500 字</li>
 * </ul>
 *
 * <h2>注意事项</h2>
 * <ul>
 *   <li>只能恢复处于挂起状态的流程实例</li>
 *   <li>恢复后流程从挂起点继续执行</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.0
 * @since 1.3.0
 */
@Data
public class ResumeInstanceRequest {

    @NotBlank(message = "恢复原因不能为空")
    @Size(max = 500, message = "恢复原因不能超过500字")
    private String reason;
}
