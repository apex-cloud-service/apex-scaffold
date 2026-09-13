package com.apex.scaffold.application.dto.data;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图表数据响应 DTO。
 *
 * <p>用于返回图表渲染所需的数据结构，包含图表标识、类型、标题、X 轴数据以及一个或多个数据系列。</p>
 *
 * <p>主要字段：</p>
 * <ul>
 *   <li>{@code chartId} - 图表 ID</li>
 *   <li>{@code chartType} - 图表类型</li>
 *   <li>{@code title} - 图表标题</li>
 *   <li>{@code xAxis} - X 轴数据列表</li>
 *   <li>{@code series} - 数据系列列表，每个系列包含名称、类型与数据值（见内部类 {@link ChartSeries}）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "图表数据响应")
/**n * ChartDataResponsen *n * <p>TODO: 补充类的详细说明n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public class ChartDataResponse {

    @Schema(description = "图表ID")
    private String chartId;

    @Schema(description = "图表类型")
    private String chartType;

    @Schema(description = "图表标题")
    private String title;

    @Schema(description = "X轴数据")
    private List<String> xAxis;

    @Schema(description = "数据系列")
    private List<ChartSeries> series;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "图表数据系列")
    public static class ChartSeries {
        @Schema(description = "系列名称")
        private String name;

        @Schema(description = "系列类型")
        private String type;

        @Schema(description = "数据值")
        private List<Object> data;
    }
}
