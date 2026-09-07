package com.apex.scaffold.application.dto;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作流统计概览 DTO。
 *
 * <p>用于监控页面展示统计数据。</p>
 *
 * @author Apex Team
 * @version 1.4.0
 * @since 1.4.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
