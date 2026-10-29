package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 体检快照落库命令。
 *
 * <p>由接口层把即时报告映射为本命令，避免 application 直接依赖接口层 DTO。
 * 只携带结论级字段，逐项证据不进入快照。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "体检快照落库命令")
public class HealthSnapshotCommand {

    @Schema(description = "采集来源 MANUAL/SCHEDULED", example = "MANUAL")
    private String captureSource;

    @Schema(description = "采集时的服务名")
    private String serviceName;

    @Schema(description = "采集时的实例标识")
    private String instanceId;

    @Schema(description = "采集范围，固定 INSTANCE")
    private String scope;

    @Schema(description = "判定规则版本")
    private String ruleVersion;

    @Schema(description = "总体结论 UP/DEGRADED/DOWN/UNKNOWN")
    private String overallStatus;

    @Schema(description = "总体结论说明")
    private String summary;

    @Schema(description = "UP 检查项数")
    private Integer upCount;

    @Schema(description = "DEGRADED 检查项数")
    private Integer degradedCount;

    @Schema(description = "DOWN 检查项数")
    private Integer downCount;

    @Schema(description = "UNKNOWN 检查项数")
    private Integer unknownCount;

    @Schema(description = "NOT_CONFIGURED 检查项数")
    private Integer notConfiguredCount;

    @Schema(description = "报告生成时间（采集时刻）")
    private LocalDateTime generatedAt;
}
