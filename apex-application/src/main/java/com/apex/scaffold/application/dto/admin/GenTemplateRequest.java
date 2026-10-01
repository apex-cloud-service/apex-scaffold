package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代码生成模板请求 DTO
 *
 * <p>用于创建或更新代码生成模板的请求参数，包含模板名称、内容和输出路径等信息。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "代码生成模板请求")
public class GenTemplateRequest {

    @Schema(description = "模板名称", example = "Entity 实体类模板", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 100, message = "模板名称长度不能超过100")
    private String templateName;

    @Schema(description = "模板类型", example = "entity", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "模板类型不能为空")
    @Size(max = 50, message = "模板类型长度不能超过50")
    private String templateType;

    @Schema(description = "模板内容（FreeMarker 语法）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "模板内容不能为空")
    private String templateContent;

    @Schema(description = "输出文件名模板", example = "${entityName}Entity.java", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "输出文件名模板不能为空")
    @Size(max = 200, message = "输出文件名模板长度不能超过200")
    private String outputFileName;

    @Schema(description = "输出子路径", example = "domain/model/entity")
    @Size(max = 200, message = "输出子路径长度不能超过200")
    private String outputSubPath;

    @Schema(description = "备注说明", example = "DDD 领域实体模板")
    @Size(max = 500, message = "备注长度不能超过500")
    private String remark;
}
