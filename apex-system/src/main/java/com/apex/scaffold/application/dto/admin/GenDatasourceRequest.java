package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代码生成数据源请求 DTO
 *
 * <p>用于创建或更新代码生成数据源的请求参数，包含数据库连接信息。
 * 密码字段在传输时明文，持久化时自动加密。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "代码生成数据源请求")
public class GenDatasourceRequest {

    @Schema(description = "数据源名称", example = "主库数据源", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "数据源名称不能为空")
    @Size(max = 100, message = "数据源名称长度不能超过100")
    private String name;

    @Schema(description = "数据库类型", example = "mysql", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "数据库类型不能为空")
    @Size(max = 50, message = "数据库类型长度不能超过50")
    private String dbType;

    @Schema(
            description = "JDBC 连接 URL",
            example = "jdbc:mysql://localhost:3306/apex_scaffold?useSSL=false&serverTimezone=Asia/Shanghai",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "JDBC URL 不能为空")
    @Size(max = 500, message = "JDBC URL 长度不能超过500")
    private String jdbcUrl;

    @Schema(description = "数据库用户名", example = "root", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "数据库用户名不能为空")
    @Size(max = 100, message = "数据库用户名长度不能超过100")
    private String username;

    @Schema(description = "数据库密码", example = "password123", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "数据库密码不能为空")
    @Size(max = 200, message = "数据库密码长度不能超过200")
    private String password;

    @Schema(description = "备注说明", example = "开发环境主库")
    @Size(max = 500, message = "备注长度不能超过500")
    private String remark;
}
