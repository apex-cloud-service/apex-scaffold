package com.apex.scaffold.application.dto.data;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图表创建/更新请求 DTO。
 *
 * <p>用于创建或更新图表配置，包含图表名称、类型、分类、描述、数据源、配置和状态。</p>
 *
 * <p>主要字段与校验规则：</p>
 * <ul>
 *   <li>{@code chartName} - 图表名称，必填，长度不超过 200</li>
 *   <li>{@code chartType} - 图表类型，必填</li>
 *   <li>{@code category} - 图表分类，必填</li>
 *   <li>{@code description} - 图表描述，可选，长度不超过 500</li>
 *   <li>{@code dataSource} - 数据源标识，可选</li>
 *   <li>{@code config} - 图表配置（Map 结构），可选</li>
 *   <li>{@code status} - 状态（0-草稿，1-正常），可选</li>
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
@Schema(description = "图表创建/更新请求")
/**n * ChartRequestn *n * <p>TODO: 补充类的详细说明n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public class ChartRequest {

    @Schema(description = "图表名称", example = "月度销售趋势")
    @NotBlank(message = "图表名称不能为空")
    @Size(max = 200, message = "图表名称长度不能超过200")
    private String chartName;

    @Schema(description = "图表类型", example = "line")
    @NotBlank(message = "图表类型不能为空")
    private String chartType;

    @Schema(description = "图表分类", example = "business")
    @NotBlank(message = "图表分类不能为空")
    private String category;

    @Schema(description = "图表描述")
    @Size(max = 500, message = "图表描述长度不能超过500")
    private String description;

    @Schema(description = "数据源")
    private String dataSource;

    @Schema(description = "图表配置")
    private Map<String, Object> config;

    @Schema(description = "状态(0=草稿 1=正常)", example = "0")
    private Integer status;
}
