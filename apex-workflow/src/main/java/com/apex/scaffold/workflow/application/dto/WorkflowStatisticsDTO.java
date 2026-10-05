package com.apex.scaffold.workflow.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作流统计概览 DTO。
 *
 * <p>工作流监控页面的统计响应对象，承载今日发起数、运行中总数、
 * 待办任务数、异常状态计数、平均完成时长与状态分布等汇总指标。</p>
 *
 * <p>主要字段：</p>
 * <ul>
 *   <li>{@code todayStarted}：今日发起流程数。</li>
 *   <li>{@code runningTotal}：运行中流程总数。</li>
 *   <li>{@code pendingTaskTotal}：待办任务总数。</li>
 *   <li>{@code suspendedCount}：已挂起流程数。</li>
 *   <li>{@code timeoutCount}：已超时流程数。</li>
 *   <li>{@code transferredCount}：已转交流程数。</li>
 *   <li>{@code avgCompletionSeconds}：平均完成时长（秒）。</li>
 *   <li>{@code statusDistribution}：流程状态分布，键为状态、值为数量。</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "工作流统计概览DTO，工作流监控页面的统计响应，承载发起数、运行数、待办数、异常计数与状态分布等汇总指标")
public class WorkflowStatisticsDTO {

    /** 今日发起流程数 */
    @Schema(description = "今日发起流程数", example = "25")
    private Integer todayStarted;

    /** 运行中流程总数 */
    @Schema(description = "运行中流程总数", example = "120")
    private Integer runningTotal;

    /** 待办任务总数 */
    @Schema(description = "待办任务总数", example = "86")
    private Integer pendingTaskTotal;

    @Schema(description = "已挂起流程数", example = "3")
    private Integer suspendedCount;

    @Schema(description = "已超时流程数", example = "5")
    private Integer timeoutCount;

    @Schema(description = "已转交流程数", example = "12")
    private Integer transferredCount;

    /** 平均完成时长（秒） */
    @Schema(description = "平均完成时长（秒）", example = "86400")
    private Long avgCompletionSeconds;

    /** 流程状态分布：状态 -> 数量 */
    @Schema(description = "流程状态分布，键为流程状态（如 RUNNING、APPROVED），值为该状态的流程数量")
    private Map<String, Integer> statusDistribution;
}