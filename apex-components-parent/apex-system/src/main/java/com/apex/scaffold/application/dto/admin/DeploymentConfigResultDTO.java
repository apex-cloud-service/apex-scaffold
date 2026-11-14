package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 部署配置生成结果 DTO。
 *
 * <p>系统管理-部署配置生成的响应对象，根据输入参数生成多格式的
 * 部署配置内容，供运维人员直接使用或下载。</p>
 *
 * <p>主要字段：</p>
 * <ul>
 *   <li>{@code yaml}：YAML 格式配置内容。</li>
 *   <li>{@code env}：环境变量（.env）格式配置内容。</li>
 *   <li>{@code dockerCompose}：Docker Compose 配置内容，可选。</li>
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
@Schema(name = "DeploymentConfigResultDTO", description = "部署配置生成结果响应（供运维人员直接使用或下载）")
public class DeploymentConfigResultDTO {

    /**
     * YAML 格式配置。
     */
    @Schema(
            description = "YAML 格式配置内容（数据库密码以环境变量占位符注入）",
            example = "spring:\n  datasource:\n    url: jdbc:mysql://192.168.1.100:3306/apex_scaffold")
    private String yaml;

    /**
     * 环境变量格式配置。
     */
    @Schema(
            description = "环境变量（.env）格式配置内容",
            example = "DB_URL=jdbc:mysql://192.168.1.100:3306/apex_scaffold\n"
                    + "DB_USERNAME=apex\n"
                    + "DB_PASSWORD=******")
    private String env;

    /**
     * Docker Compose 配置（可选）。
     */
    @Schema(description = "Docker Compose 配置内容（可为空）", example = "services:\n  database:\n    image: mysql:9.0")
    private String dockerCompose;
}
