package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 字典类型创建/更新请求体。
 *
 * <p>系统管理-字典管理的字典类型请求 DTO。字典类型编码必须全局唯一，
 * 采用小写下划线格式，例如 {@code sys_user_sex}。</p>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "字典类型创建/更新请求")
/**n * DictTypeRequestn *n * <p>TODO: 补充类的详细说明n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public class DictTypeRequest {

    /**
     * 字典名称。
     */
    @Schema(description = "字典名称", example = "用户性别")
    @NotBlank(message = "{dict.name_not_blank}")
    @Size(max = 100, message = "{dict.name_too_long}")
    private String dictName;

    /**
     * 字典类型编码。
     *
     * <p>全局唯一，格式为小写下划线，例如 {@code sys_user_sex}。</p>
     */
    @Schema(description = "字典类型编码", example = "sys_user_sex")
    @NotBlank(message = "{dict.type_not_blank}")
    @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "{dict.type_invalid}")
    @Size(max = 100, message = "{dict.type_too_long}")
    private String dictType;

    /**
     * 备注。
     */
    @Schema(description = "备注", example = "用户性别字典")
    @Size(max = 500, message = "{dict.remark_too_long}")
    private String remark;
}
