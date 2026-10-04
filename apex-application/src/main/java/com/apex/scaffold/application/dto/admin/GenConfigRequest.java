package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代码生成配置请求 DTO
 *
 * <p>用于创建或更新代码生成配置的请求参数，包含表名、包路径、模板等配置信息。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "代码生成配置请求")
public class GenConfigRequest {

    @Schema(description = "数据源 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "数据源 ID 不能为空")
    private Long datasourceId;

    private String configName;

    @Schema(description = "表名", example = "sys_user", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "表名不能为空")
    @Size(max = 200, message = "表名长度不能超过200")
    private String tableName;

    private String tableNames;

    private String templateIds;

    private String extraConfig;

    @Schema(description = "实体类名", example = "User", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "实体类名不能为空")
    @Size(max = 100, message = "实体类名长度不能超过100")
    private String entityName;

    @Schema(description = "包路径", example = "com.apex.scaffold", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "包路径不能为空")
    @Size(max = 200, message = "包路径长度不能超过200")
    private String packageName;

    @Schema(description = "模块名", example = "system")
    @Size(max = 100, message = "模块名长度不能超过100")
    private String moduleName;

    @Schema(description = "作者", example = "Apex Team")
    @Size(max = 100, message = "作者长度不能超过100")
    private String author;

    @Schema(description = "表前缀（移除）", example = "sys_")
    @Size(max = 50, message = "表前缀长度不能超过50")
    private String tablePrefix;

    @Schema(
            description = "输出路径",
            example = "/Users/work/apex-scaffold/output",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "输出路径不能为空")
    @Size(max = 500, message = "输出路径长度不能超过500")
    private String outputPath;

    @Schema(description = "备注说明", example = "用户管理代码生成")
    @Size(max = 500, message = "备注长度不能超过500")
    private String remark;
}
