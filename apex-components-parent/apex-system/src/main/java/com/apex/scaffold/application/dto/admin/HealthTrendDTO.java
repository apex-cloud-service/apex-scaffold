package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 体检趋势响应。
 *
 * <p>按时间升序返回快照取样点，并给出结论分布与首尾对比，用于判断风险是否收敛。
 * 跨规则版本的取样点仍会返回，但通过 {@code ruleVersions} 显式提示口径可能不一致。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "体检趋势")
public class HealthTrendDTO {

    @Schema(description = "统计起始时间")
    private LocalDateTime from;

    @Schema(description = "统计结束时间")
    private LocalDateTime to;

    @Schema(description = "取样点数量")
    private Integer sampleCount;

    @Schema(description = "参与统计的判定规则版本集合，多于一个时趋势口径可能不一致")
    private List<String> ruleVersions;

    @Schema(description = "结论分布（各总体结论出现次数）")
    private List<StatusCount> distribution;

    @Schema(description = "区间起点结论，无取样时为 null")
    private String startStatus;

    @Schema(description = "区间终点结论，无取样时为 null")
    private String endStatus;

    @Schema(description = "是否为改善（按严重度终点低于起点）；取样不足时为 null")
    private Boolean improving;

    @Schema(description = "趋势取样点，按生成时间升序")
    private List<Point> points;

    /** 结论分布项。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "结论分布项")
    public static class StatusCount {

        @Schema(description = "总体结论", example = "DEGRADED")
        private String status;

        @Schema(description = "出现次数", example = "3")
        private int count;
    }

    /** 趋势取样点。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "趋势取样点")
    public static class Point {

        @Schema(description = "报告生成时间")
        private LocalDateTime generatedAt;

        @Schema(description = "总体结论")
        private String overallStatus;

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
    }
}
