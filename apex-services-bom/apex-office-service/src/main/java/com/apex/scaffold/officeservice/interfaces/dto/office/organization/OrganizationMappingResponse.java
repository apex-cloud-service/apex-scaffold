package com.apex.scaffold.officeservice.interfaces.dto.office.organization;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 组织映射响应 DTO
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "组织映射响应")
public class OrganizationMappingResponse {

    @Schema(description = "映射ID", example = "1")
    private Long id;

    @Schema(description = "平台类型", example = "FEISHU")
    private String platform;

    @Schema(description = "外部组织ID（部门ID）", example = "od-123456")
    private String externalOrgId;

    @Schema(description = "外部用户ID", example = "ou-abcdef")
    private String externalUserId;

    @Schema(description = "Apex 部门ID", example = "dept-001")
    private String apexDeptId;

    @Schema(description = "Apex 用户ID", example = "user-001")
    private String apexUserId;

    @Schema(description = "创建时间", example = "2026-09-14T10:30:00")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间", example = "2026-09-14T10:30:00")
    private LocalDateTime updatedAt;
}
