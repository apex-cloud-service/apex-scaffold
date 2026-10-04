package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代码生成历史响应。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "代码生成历史响应")
public class GenHistoryDTO {

    private Long id;

    private Long configId;

    private String generatedFiles;

    private String outputPath;

    private String status;

    private String errorMessage;

    private Long operatorId;

    private String tenantId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
