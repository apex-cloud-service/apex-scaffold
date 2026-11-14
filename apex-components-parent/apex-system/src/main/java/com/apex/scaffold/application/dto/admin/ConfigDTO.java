package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 参数配置响应 DTO。
 *
 * <p>系统管理-参数配置的响应对象，承载参数键值对与类型、
 * 状态等管理信息。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "参数配置响应")
public class ConfigDTO {

    /**
     * 参数配置 ID。
     */
    @Schema(description = "参数配置ID")
    private Long id;

    /**
     * 参数名称。
     */
    @Schema(description = "参数名称")
    private String configName;

    /**
     * 参数键名。
     */
    @Schema(description = "参数键名")
    private String configKey;

    /**
     * 参数键值。
     */
    @Schema(description = "参数键值")
    private String configValue;

    /**
     * 参数类型（0-内置，1-用户自定义）。
     */
    @Schema(description = "参数类型：0-内置，1-用户自定义")
    private Integer configType;

    /**
     * 参数状态（0-停用，1-正常）。
     */
    @Schema(description = "参数状态：0-停用，1-正常")
    private Integer status;

    /**
     * 备注。
     */
    @Schema(description = "备注")
    private String remark;

    /**
     * 创建时间。
     */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
