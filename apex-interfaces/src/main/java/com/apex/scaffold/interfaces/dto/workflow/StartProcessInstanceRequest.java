package com.apex.scaffold.interfaces.dto.workflow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发起流程实例请求 DTO。
 *
 * @author Apex Team
 * @version 1.3.0
 * @since 1.3.0
 */
@Data
public class StartProcessInstanceRequest {

    @NotBlank(message = "业务主键不能为空")
    @Size(max = 200, message = "业务主键不能超过200字符")
    private String businessKey;

    @Size(max = 10000, message = "流程上下文不能超过10000字符")
    private String contextJson;
}
