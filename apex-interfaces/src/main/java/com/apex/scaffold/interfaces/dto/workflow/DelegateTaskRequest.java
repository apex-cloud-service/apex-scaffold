package com.apex.scaffold.interfaces.dto.workflow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 任务委派请求 DTO。
 *
 * @author Apex Team
 * @version 1.3.0
 * @since 1.3.0
 */
@Data
public class DelegateTaskRequest {

    @NotNull(message = "受托人ID不能为空")
    private Long delegateToId;

    @NotBlank(message = "委派说明不能为空")
    @Size(max = 500, message = "委派说明不能超过500字")
    private String reason;
}
