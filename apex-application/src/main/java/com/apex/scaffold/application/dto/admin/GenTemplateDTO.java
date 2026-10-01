package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代码生成模板响应 DTO
 *
 * <p>代码生成模板的完整信息，包含模板详情和使用统计。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "代码生成模板响应")
public class GenTemplateDTO {

    @Schema(description = "模板 ID", example = "1")
    private Long id;

    @Schema(description = "租户 ID", example = "tenant-001")
    private String tenantId;

    @Schema(description = "模板名称", example = "Entity 实体类模板")
    private String templateName;

    @Schema(description = "模板类型", example = "entity")
    private String templateType;

    @Schema(description = "模板内容（FreeMarker 语法）")
    private String templateContent;

    @Schema(description = "输出文件名模板", example = "${entityName}Entity.java")
    private String outputFileName;

    @Schema(description = "输出子路径", example = "domain/model/entity")
    private String outputSubPath;

    @Schema(description = "备注说明", example = "DDD 领域实体模板")
    private String remark;

    @Schema(description = "创建时间", example = "2024-01-01T10:00:00")
    private LocalDateTime createTime;

    @Schema(description = "更新时间", example = "2024-01-15T15:30:00")
    private LocalDateTime updateTime;
}
