package com.apex.scaffold.interfaces.dto.monitor;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 核心业务监控响应（/admin-api/v1/monitor/business）。
 *
 * <p>提供核心业务指标快照与近 10 分钟趋势（按 1 分钟窗口聚合）。</p>
 *
 * @param timestamp 采集时间戳（毫秒）
 * @param metrics 业务指标快照列表
 * @param trends 业务指标趋势列表
 */
@Schema(description = "核心业务监控")
public record MonitorBusinessResponse(
        @Schema(description = "采集时间戳（毫秒）") long timestamp,
        @Schema(description = "业务指标快照") List<MetricPoint> metrics,
        @Schema(description = "业务指标趋势") List<BusinessTrend> trends) {

    /** 业务指标点。 */
    @Schema(description = "业务指标点")
    public record MetricPoint(
            @Schema(description = "指标键") String key,
            @Schema(description = "指标名称") String name,
            @Schema(description = "指标值") double value,
            @Schema(description = "单位") String unit) {}

    /** 业务趋势。 */
    @Schema(description = "业务趋势")
    public record BusinessTrend(
            @Schema(description = "指标名称") String name,
            @Schema(description = "指标键") String key,
            @Schema(description = "趋势点列表") List<TrendPoint> points) {}

    /** 趋势点。 */
    @Schema(description = "趋势点")
    public record TrendPoint(
            @Schema(description = "时间（HH:mm:ss）") String time, @Schema(description = "指标值") double value) {}
}
