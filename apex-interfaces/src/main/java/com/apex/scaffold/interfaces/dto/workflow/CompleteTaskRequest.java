package com.apex.scaffold.interfaces.dto.workflow;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 完成任务请求 DTO。
 *
 * @author Apex Team
 * @version 1.3.0
 * @since 1.3.0
 */
@Data
public class CompleteTaskRequest {

    @Size(max = 1000, message = "审批意见不能超过1000字")
    private String comment;
}
