package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 参数配置创建/更新请求体。
 *
 * <p>系统管理-参数配置的请求 DTO。参数键名全局唯一，采用
 * 点分命名风格，例如 {@code sys.site.name}。</p>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "参数配置创建/更新请求")
/**n * ConfigRequestn *n * <p>TODO: 补充类的详细说明n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public class ConfigRequest {

    /**
     * 参数名称。
     */
    @Schema(description = "参数名称", example = "系统名称")
    @NotBlank(message = "{config.name_not_blank}")
    @Size(max = 100, message = "{config.name_too_long}")
    private String configName;

    /**
     * 参数键名。
     *
     * <p>全局唯一，例如 {@code sys.site.name}。</p>
     */
    @Schema(description = "参数键名", example = "sys.site.name")
    @NotBlank(message = "{config.key_not_blank}")
    @Size(max = 100, message = "{config.key_too_long}")
    private String configKey;

    /**
     * 参数键值。
     */
    @Schema(description = "参数键值", example = "Apex Scaffold")
    @NotBlank(message = "{config.value_not_blank}")
    @Size(max = 500, message = "{config.value_too_long}")
    private String configValue;

    /**
     * 备注。
     */
    @Schema(description = "备注")
    @Size(max = 500, message = "{config.remark_too_long}")
    private String remark;
}
