package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 体检历史快照视图对象。
 *
 * <p>只暴露总体结论、状态计数与摘要，不含逐项 {@code source}/{@code evidence}，
 * 避免平台实例细节经历史列表扩散。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "体检历史快照")
public class HealthSnapshotDTO {

    @Schema(description = "快照ID", example = "1958000000000000001")
    private Long id;

    @Schema(description = "采集来源 MANUAL/SCHEDULED", example = "MANUAL")
    private String captureSource;

    @Schema(description = "采集时的服务名", example = "apex-system-service")
    private String serviceName;

    @Schema(description = "采集时的实例标识", example = "12345@apex-host-1")
    private String instanceId;

    @Schema(description = "采集范围，固定 INSTANCE", example = "INSTANCE")
    private String scope;

    @Schema(description = "判定规则版本", example = "health-report-v1")
    private String ruleVersion;

    @Schema(description = "总体结论 UP/DEGRADED/DOWN/UNKNOWN", example = "DEGRADED")
    private String overallStatus;

    @Schema(description = "总体结论说明")
    private String summary;

    @Schema(description = "UP 检查项数", example = "8")
    private Integer upCount;

    @Schema(description = "DEGRADED 检查项数", example = "1")
    private Integer degradedCount;

    @Schema(description = "DOWN 检查项数", example = "0")
    private Integer downCount;

    @Schema(description = "UNKNOWN 检查项数", example = "2")
    private Integer unknownCount;

    @Schema(description = "NOT_CONFIGURED 检查项数", example = "2")
    private Integer notConfiguredCount;

    @Schema(description = "报告生成时间", example = "2026-10-29T10:15:30")
    private LocalDateTime generatedAt;

    @Schema(description = "落库时间", example = "2026-10-29T10:15:31")
    private LocalDateTime createTime;
}
