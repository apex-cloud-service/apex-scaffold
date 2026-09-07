package com.apex.scaffold.application.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 部署配置生成结果 DTO。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentConfigResultDTO {

    /**
     * YAML 格式配置。
     */
    private String yaml;

    /**
     * 环境变量格式配置。
     */
    private String env;

    /**
     * Docker Compose 配置（可选）。
     */
    private String dockerCompose;
}
