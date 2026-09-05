package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 字典数据创建/更新请求体。
 *
 * <p>系统管理-字典管理的字典数据请求 DTO。字典数据通过
 * {@code dictType} 编码归属字典类型，标签与键值均必填。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "字典数据创建/更新请求")
public class DictDataRequest {

    /**
     * 字典排序。
     */
    @Schema(description = "字典排序", example = "0")
    private Integer dictSort;

    /**
     * 字典标签。
     */
    @Schema(description = "字典标签", example = "男")
    @NotBlank(message = "{dict.label_not_blank}")
    @Size(max = 100, message = "{dict.label_too_long}")
    private String dictLabel;

    /**
     * 字典键值。
     */
    @Schema(description = "字典键值", example = "1")
    @NotBlank(message = "{dict.value_not_blank}")
    @Size(max = 100, message = "{dict.value_too_long}")
    private String dictValue;

    /**
     * 所属字典类型编码。
     */
    @Schema(description = "所属字典类型编码", example = "sys_user_sex")
    @NotBlank(message = "{dict.type_not_blank}")
    @Size(max = 100, message = "{dict.type_too_long}")
    private String dictType;

    /**
     * 是否默认（0-否，1-是）。
     */
    @Schema(description = "是否默认：0-否，1-是", example = "0")
    private Integer isDefault;

    /**
     * 备注。
     */
    @Schema(description = "备注")
    @Size(max = 500, message = "{dict.remark_too_long}")
    private String remark;
}
