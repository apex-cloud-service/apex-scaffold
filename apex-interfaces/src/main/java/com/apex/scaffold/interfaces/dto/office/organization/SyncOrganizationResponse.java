package com.apex.scaffold.interfaces.dto.office.organization;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 同步组织响应 DTO
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "同步组织响应")
public class SyncOrganizationResponse {

    @Schema(description = "同步任务ID", example = "12345")
    private Long taskId;

    @Schema(description = "同步数量", example = "150")
    private Integer syncedCount;

    @Schema(description = "结果消息", example = "部门同步完成")
    private String message;

    @Schema(description = "是否成功", example = "true")
    private Boolean success;
}
