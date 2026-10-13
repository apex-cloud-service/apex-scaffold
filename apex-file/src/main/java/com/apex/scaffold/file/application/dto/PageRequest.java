package com.apex.scaffold.file.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 分页请求参数。
 *
 * <p>通用分页查询请求 DTO,承载页码、每页数量与排序条件。
 * 所有分页列表接口均需继承或复用本对象。</p>
 *
 * <p>主要字段与校验规则:</p>
 * <ul>
 *   <li>{@code pageNum}:页码,从 1 开始,必填,最小值为 1。</li>
 *   <li>{@code pageSize}:每页数量,必填,取值范围 1～100。</li>
 *   <li>{@code orderBy}:排序字段名,可选。</li>
 *   <li>{@code isAsc}:是否升序,可选,缺省为 {@code false}(降序)。</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Schema(description = "分页请求参数")
public class PageRequest {
    /**
     * 页码
     *
     * <p>从1开始，默认为1
     */
    @Schema(description = "页码，从 1 开始", example = "1", minimum = "1")
    @NotNull(message = "{param.not_null}")
    @Min(value = 1, message = "页码必须大于等于1")
    private Integer pageNum;

    /**
     * 每页大小
     *
     * <p>默认为10，最大值一般不超过100
     */
    @Schema(description = "每页数量，取值范围 1～100", example = "10", minimum = "1", maximum = "100")
    @NotNull(message = "{param.not_null}")
    @Min(value = 1, message = "每页大小必须大于等于1")
    @Max(value = 100, message = "每页大小必须小于等于100")
    private Integer pageSize;

    @Schema(description = "排序字段名，可选", example = "createTime")
    private String orderBy;

    @Schema(description = "是否升序，缺省为 false（降序）", example = "false")
    private Boolean isAsc = false;

    /**
     * 计算分页偏移量。
     *
     * <p>根据当前页码和每页大小计算 SQL LIMIT 的偏移量。
     * 公式：offset = (pageNum - 1) * pageSize</p>
     *
     * @return 分页偏移量
     */
    public Integer getOffset() {
        return (pageNum - 1) * pageSize;
    }
}
