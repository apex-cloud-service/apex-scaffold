package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代码生成任务提交响应。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.5.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "代码生成任务提交响应")
public class GenTaskSubmitResponse {

    private Long taskId;

    private String status;
}
