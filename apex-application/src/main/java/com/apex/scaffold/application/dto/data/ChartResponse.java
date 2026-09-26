package com.apex.scaffold.application.dto.data;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图表响应 DTO。
 *
 * <p>用于接口层返回图表的完整信息，包括图表基本属性、配置、创建人及统计数据等，避免领域实体直接暴露给前端。</p>
 *
 * <p>主要字段：</p>
 * <ul>
 *   <li>{@code id} - 图表主键 ID</li>
 *   <li>{@code chartName} - 图表名称</li>
 *   <li>{@code chartType} - 图表类型（如 line、bar、pie 等）</li>
 *   <li>{@code category} - 图表分类</li>
 *   <li>{@code description} - 图表描述</li>
 *   <li>{@code dataSource} - 数据源标识</li>
 *   <li>{@code config} - 图表配置（Map 结构，存储渲染所需配置）</li>
 *   <li>{@code status} - 状态（0-草稿，1-正常）</li>
 *   <li>{@code creatorId}/{@code creatorName} - 创建人 ID 与姓名</li>
 *   <li>{@code viewCount} - 查看次数</li>
 *   <li>{@code createTime}/{@code updateTime} - 创建与更新时间</li>
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
@Schema(description = "图表响应")
public class ChartResponse {

    @Schema(description = "图表ID")
    private Long id;

    @Schema(description = "图表名称")
    private String chartName;

    @Schema(description = "图表类型")
    private String chartType;

    @Schema(description = "图表分类")
    private String category;

    @Schema(description = "图表描述")
    private String description;

    @Schema(description = "数据源")
    private String dataSource;

    @Schema(description = "图表配置")
    private Map<String, Object> config;

    @Schema(description = "状态")
    private Integer status;

    @Schema(description = "创建人ID")
    private Long creatorId;

    @Schema(description = "创建人姓名")
    private String creatorName;

    @Schema(description = "查看次数")
    private Integer viewCount;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
