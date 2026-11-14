package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代码生成向导预检结果 DTO
 *
 * <p>预检不渲染、不写盘，只校验配置归属、数据源能力、目标表集合、模板归属与引擎能力、
 * 输出目录与覆盖冲突。{@code ready=false} 时存在 ERROR 级问题，不允许继续预览或执行。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "代码生成向导预检结果")
public class GenPreflightResponse {

    @Schema(description = "是否通过预检（无 ERROR 级问题）", example = "true")
    private Boolean ready;

    @Schema(description = "结构化问题项")
    private List<GenIssueDTO> issues;

    @Schema(description = "目标表数量", example = "2")
    private Integer tableCount;

    @Schema(description = "可用模板数量", example = "3")
    private Integer templateCount;

    @Schema(description = "预计产物数量（表数量 × 模板数量）", example = "6")
    private Integer artifactCount;

    @Schema(description = "输出根目录（部署配置项，不返回绝对路径给前端）", example = "${apex.generator.output-root}")
    private String outputRootAlias;
}
