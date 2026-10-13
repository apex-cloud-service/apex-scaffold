package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代码生成配置响应 DTO
 *
 * <p>代码生成配置的完整信息，包含配置详情和关联的数据源信息。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "代码生成配置响应")
public class GenConfigDTO {

    @Schema(description = "配置 ID", example = "1")
    private Long id;

    @Schema(description = "租户 ID", example = "tenant-001")
    private String tenantId;

    @Schema(description = "数据源 ID", example = "1")
    private Long datasourceId;

    private String configName;

    private String tableNames;

    private String templateIds;

    private String extraConfig;

    @Schema(description = "数据源名称", example = "主库数据源")
    private String datasourceName;

    @Schema(description = "表名", example = "sys_user")
    private String tableName;

    @Schema(description = "实体类名", example = "User")
    private String entityName;

    @Schema(description = "包路径", example = "com.apex.scaffold")
    private String packageName;

    @Schema(description = "模块名", example = "system")
    private String moduleName;

    @Schema(description = "作者", example = "Apex Team")
    private String author;

    @Schema(description = "表前缀（移除）", example = "sys_")
    private String tablePrefix;

    @Schema(description = "输出路径", example = "/Users/work/apex-scaffold/output")
    private String outputPath;

    @Schema(description = "备注说明", example = "用户管理代码生成")
    private String remark;

    @Schema(description = "创建时间", example = "2024-01-01T10:00:00")
    private LocalDateTime createTime;

    @Schema(description = "更新时间", example = "2024-01-15T15:30:00")
    private LocalDateTime updateTime;
}
