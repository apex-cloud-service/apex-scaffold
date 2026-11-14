package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代码生成向导预检请求 DTO
 *
 * <p>预检与预览都基于已保存的生成配置，可选地携带字段映射覆盖。
 * 服务端会重新读取数据源元数据、模板内容与配置归属，任何一项不合法都以结构化问题项返回，
 * 而不是直接抛出单条异常。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "代码生成向导预检请求")
public class GenPreflightRequest {

    @Schema(description = "字段映射覆盖（可选）")
    private List<GenFieldMappingDTO> fieldMappings;

    @Schema(description = "是否允许已存在的目标文件被覆盖", example = "false")
    private Boolean overwrite;

    @Schema(description = "生成配置 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "生成配置 ID 不能为空")
    private Long configId;
}
