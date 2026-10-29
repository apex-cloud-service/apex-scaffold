package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代码生成向导预览请求 DTO
 *
 * <p>预览只做纯渲染，不写任何服务器文件。请求参数必须与随后的执行请求一致，
 * 否则服务端签发的预览令牌校验失败。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "代码生成向导预览请求")
public class GenPreviewRequest {

    @Schema(description = "生成配置 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "生成配置 ID 不能为空")
    private Long configId;

    @Schema(description = "字段映射覆盖（可选）")
    private List<GenFieldMappingDTO> fieldMappings;
}
