package com.apex.scaffold.application.dto.admin;

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
/**n * DeploymentConfigResultDTOn *n * <p>TODO: 补充类的详细说明n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
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
