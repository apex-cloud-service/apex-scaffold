package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 字典数据响应 DTO。
 *
 * <p>系统管理-字典管理的字典数据响应对象，供管理界面展示与
 * 业务代码读取字典项使用。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Schema(description = "字典数据响应")
public class DictDataDTO {

    /**
     * 构造字典数据响应。
     */
    public DictDataDTO() {}

    /**
     * 字典数据 ID。
     */
    @Schema(description = "字典数据ID")
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
