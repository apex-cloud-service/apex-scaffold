package com.apex.scaffold.interfaces.dto.workflow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 任务转办请求 DTO。
 *
 * @author Apex Team
 * @version 1.3.0
 * @since 1.3.0
 */
@Data
public class TransferTaskRequest {

    @NotNull(message = "新审批人ID不能为空")
    private Long newAssigneeId;

    @NotBlank(message = "转办原因不能为空")
    @Size(max = 500, message = "转办原因不能超过500字")
    private String reason;
}
