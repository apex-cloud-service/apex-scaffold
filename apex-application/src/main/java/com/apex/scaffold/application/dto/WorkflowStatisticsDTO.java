package com.apex.scaffold.application.dto;

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
/**n * WorkflowStatisticsDTOn *n * <p>TODO: 补充类的详细说明n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public class WorkflowStatisticsDTO {

    /** 今日发起流程数 */
    private Integer todayStarted;

    /** 运行中流程总数 */
    private Integer runningTotal;

    /** 待办任务总数 */
    private Integer pendingTaskTotal;

    private Integer suspendedCount;

    private Integer timeoutCount;

    private Integer transferredCount;

    /** 平均完成时长（秒） */
    private Long avgCompletionSeconds;

    /** 流程状态分布：状态 -> 数量 */
    private Map<String, Integer> statusDistribution;
}
