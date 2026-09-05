package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 字典类型响应 DTO。
 *
 * <p>系统管理-字典管理的字典类型响应对象，承载字典类型定义与
 * 数据项数量等管理信息。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "字典类型响应")
public class DictTypeDTO {

    /**
     * 字典类型 ID。
     */
    @Schema(description = "字典类型ID")
    private Long id;

    /**
     * 字典名称。
     */
    @Schema(description = "字典名称")
    private String dictName;

    /**
     * 字典类型编码。
     */
    @Schema(description = "字典类型编码")
    private String dictType;

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

    /**
     * 创建时间。
     */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
