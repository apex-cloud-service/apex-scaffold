package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 租户体检摘要（受限视图）。
 *
 * <p>只读取业务风险分组的聚合结论，不含实例标识、证据与平台依赖细节；
 * 用于租户侧了解「本租户是否在可服务状态」，不暴露平台运维信息。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "租户体检摘要（仅业务风险聚合）")
public class TenantHealthSummaryDTO {

    @Schema(description = "数据来源：最近一次快照，无快照时为 null", example = "2026-10-29T10:15:30")
    private LocalDateTime snapshotAt;

    @Schema(description = "总体结论；无快照时固定为 UNKNOWN", example = "UP")
    private String overallStatus;

    @Schema(description = "结论说明")
    private String summary;

    @Schema(description = "采集范围，固定 INSTANCE", example = "INSTANCE")
    private String scope;

    @Schema(description = "范围说明：单实例结果不代表全栈")
    private String scopeNotice;

    @Schema(description = "业务风险检查项状态计数")
    private RiskCounts riskCounts;

    @Schema(description = "最近取样点，按生成时间升序，最多 20 个")
    private List<TrendPoint> recentTrend;

    /** 业务风险状态计数。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "业务风险状态计数")
    public static class RiskCounts {

        @Schema(description = "可服务风险项数（UP）")
        private int healthy;

        @Schema(description = "存在风险项数（DEGRADED）")
        private int degraded;

        @Schema(description = "不可用风险项数（DOWN）")
        private int down;

        @Schema(description = "证据不足项数（UNKNOWN）")
        private int unknown;
    }

    /** 近期取样点。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "近期取样点")
    public static class TrendPoint {

        @Schema(description = "报告生成时间")
        private LocalDateTime generatedAt;

        @Schema(description = "总体结论")
        private String overallStatus;
    }
}
