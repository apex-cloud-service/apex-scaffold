package com.apex.scaffold.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分页请求参数
 *
 * <p>数据传输对象（DTO）
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageRequest {
    /**
     * 页码
     *
     * <p>从1开始，默认为1
     */
    @Schema(description = "页码")
    @NotNull(message = "{param.not_null}")
    @Min(value = 1, message = "页码必须大于等于1")
    private Integer pageNum;

    /**
     * 每页大小
     *
     * <p>默认为10，最大值一般不超过100
     */
    @Schema(description = "每页数量")
    @NotNull(message = "{param.not_null}")
    @Min(value = 1, message = "每页大小必须大于等于1")
    @Max(value = 100, message = "每页大小必须小于等于100")
    private Integer pageSize;

    private String orderBy;

    private Boolean isAsc = false;
}
