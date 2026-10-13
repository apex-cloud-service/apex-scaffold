package com.apex.scaffold.file.application.dto.data;

import com.apex.scaffold.file.application.dto.PageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 图表查询请求 DTO。
 *
 * <p>用于图表列表的分页与条件查询,继承 {@link PageRequest} 的分页参数,支持按关键词、图表类型、分类和状态过滤。</p>
 *
 * <p>主要字段:</p>
 * <ul>
 *   <li>{@code keyword} - 关键词,匹配图表名称或描述</li>
 *   <li>{@code chartType} - 图表类型过滤</li>
 *   <li>{@code category} - 图表分类过滤</li>
 *   <li>{@code status} - 状态过滤(0-草稿,1-正常)</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@Schema(description = "图表查询请求")
public class ChartQueryRequest extends PageRequest {

    @Schema(description = "关键词(名称/描述)")
    private String keyword;

    @Schema(description = "图表类型")
    private String chartType;

    @Schema(description = "图表分类")
    private String category;

    @Schema(description = "状态")
    private Integer status;
}
