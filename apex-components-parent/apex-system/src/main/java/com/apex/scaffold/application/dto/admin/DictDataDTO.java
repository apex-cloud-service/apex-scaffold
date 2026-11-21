package com.apex.scaffold.application.dto.admin;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 字典数据响应 DTO。
 *
 * <p>系统管理-字典管理的字典数据响应对象，供管理界面展示与
 * 业务代码读取字典项使用。</p>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "字典数据响应")
public class DictDataDTO {

    /**
     * 字典数据 ID。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(type = "string", description = "字典数据ID（十进制字符串）", example = "9007199254740993")
    private Long id;

    /**
     * 字典排序。
     */
    @Schema(description = "字典排序")
    private Integer dictSort;

    /**
     * 字典标签。
     */
    @Schema(description = "字典标签")
    private String dictLabel;

    /**
     * 字典键值。
     */
    @Schema(description = "字典键值")
    private String dictValue;

    /**
     * 所属字典类型编码。
     */
    @Schema(description = "所属字典类型编码")
    private String dictType;

    /**
     * 是否默认（0-否，1-是）。
     */
    @Schema(description = "是否默认：0-否，1-是")
    private Integer isDefault;

    /**
     * 字典状态（0-停用，1-正常）。
     */
    @Schema(description = "字典状态：0-停用，1-正常")
    private Integer status;

    /**
     * 备注。
     */
    @Schema(description = "备注")
    private String remark;
}
