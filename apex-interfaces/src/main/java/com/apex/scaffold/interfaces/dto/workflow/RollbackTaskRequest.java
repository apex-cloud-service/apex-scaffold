package com.apex.scaffold.interfaces.dto.workflow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 流程回退请求 DTO。
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
