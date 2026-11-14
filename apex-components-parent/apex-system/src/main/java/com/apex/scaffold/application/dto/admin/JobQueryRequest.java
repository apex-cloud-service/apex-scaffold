package com.apex.scaffold.application.dto.admin;

import com.apex.scaffold.application.dto.PageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 定时任务查询请求 DTO。
 *
 * <p>用于定时任务列表的分页与条件查询，继承 {@link PageRequest} 的分页参数，
 * 支持按任务名称、任务分组、任务状态进行筛选。</p>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>{@code jobName} - 任务名称，支持模糊匹配</li>
 *   <li>{@code jobGroup} - 任务分组，精确匹配</li>
 *   <li>{@code status} - 任务状态，精确匹配（0-停用，1-正常）</li>
 * </ul>
 *
 * <p>查询结果按创建时间降序排列，支持分页参数控制返回数量。</p>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@Schema(description = "定时任务查询请求")
public class JobQueryRequest extends PageRequest {

    /**
     * 任务名称（模糊匹配）。
     *
     * <p>支持部分匹配，用于快速搜索目标任务。</p>
     */
    @Schema(description = "任务名称（支持模糊查询）", example = "数据同步")
    private String jobName;

    /**
     * 任务分组（精确匹配）。
     *
     * <p>按任务分组过滤，用于分类管理和查看特定分组的任务。</p>
     */
    @Schema(description = "任务分组", example = "DEFAULT")
    private String jobGroup;

    /**
     * 任务状态（精确匹配）。
     *
     * <p>取值说明：0-停用，1-正常。用于筛选启用或停用的任务。</p>
     */
    @Schema(description = "任务状态：0-停用，1-正常", example = "1")
    private Integer status;
}
