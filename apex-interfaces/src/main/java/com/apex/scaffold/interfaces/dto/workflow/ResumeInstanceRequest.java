package com.apex.scaffold.interfaces.dto.workflow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 恢复流程实例请求 DTO。
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
