package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代码生成数据源响应 DTO
 *
 * <p>代码生成数据源的完整信息，密码字段返回掩码，不暴露真实值。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "代码生成数据源响应")
public class GenDatasourceDTO {

    @Schema(description = "数据源 ID", example = "1")
    private Long id;

    @Schema(description = "租户 ID", example = "tenant-001")
    private String tenantId;

    @Schema(description = "数据源名称", example = "主库数据源")
    private String name;

    @Schema(description = "数据库类型", example = "mysql")
    private String dbType;

    @Schema(description = "JDBC 连接 URL", example = "jdbc:mysql://localhost:3306/apex_scaffold")
    private String jdbcUrl;

    @Schema(description = "数据库用户名", example = "root")
    private String username;

    @Schema(description = "数据库密码（掩码）", example = "******")
    private String password;

    @Schema(description = "备注说明", example = "开发环境主库")
    private String remark;

    @Schema(description = "创建时间", example = "2024-01-01T10:00:00")
    private LocalDateTime createTime;

    @Schema(description = "更新时间", example = "2024-01-15T15:30:00")
    private LocalDateTime updateTime;
}
