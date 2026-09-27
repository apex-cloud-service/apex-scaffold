package com.apex.scaffold.authcenter.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审计日志查询请求DTO
 *
 * @author Apex Scaffold Team
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "审计日志查询请求")
public class AuditLogQueryRequest {

    @Schema(description = "租户ID", example = "tenant-001")
    private String tenantId;

    @Schema(description = "用户ID", example = "1001")
    private Long userId;

    @Schema(description = "事件类型", example = "LOGIN")
    private String eventType;

    @Schema(description = "事件状态", example = "SUCCESS")
    private String eventStatus;

    @Schema(description = "资源类型", example = "USER")
    private String resourceType;

    @Schema(description = "IP地址", example = "192.168.1.100")
    private String ipAddress;

    @Schema(description = "风险等级", example = "HIGH")
    private String riskLevel;

    @Schema(description = "开始时间", example = "2024-01-01T00:00:00")
    private LocalDateTime startTime;

    @Schema(description = "结束时间", example = "2024-01-31T23:59:59")
    private LocalDateTime endTime;

    @Schema(description = "页码", example = "1")
    private Integer page;

    @Schema(description = "每页大小", example = "20")
    private Integer pageSize;
}
