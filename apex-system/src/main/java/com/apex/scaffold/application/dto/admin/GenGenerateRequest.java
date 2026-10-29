package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代码生成向导执行请求 DTO
 *
 * <p>执行必须携带预览签发的 {@code previewToken}，服务端据此校验配置、模板内容摘要与字段映射
 * 在预览之后未发生变化。默认拒绝覆盖已存在的目标文件，需显式设置 {@code overwrite=true}。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "代码生成向导执行请求")
public class GenGenerateRequest {

    @Schema(description = "生成配置 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "生成配置 ID 不能为空")
    private Long configId;

    @Schema(description = "预览令牌", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "预览令牌不能为空，请先预览")
    private String previewToken;

    @Schema(description = "字段映射覆盖（须与预览时一致）")
    private List<GenFieldMappingDTO> fieldMappings;

    @Schema(description = "是否允许覆盖已存在的目标文件", example = "false")
    private Boolean overwrite;
}
