package com.apex.scaffold.workflow.application.query;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.workflow.application.dto.WorkflowStatisticsDTO;
import com.apex.scaffold.workflow.application.dto.WorkflowTrendDTO;
import com.apex.scaffold.workflow.infrastructure.persistence.mapper.ProcessInstanceMapper;
import com.apex.scaffold.workflow.infrastructure.persistence.mapper.TaskMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 工作流统计服务。
 *
 * <p>提供流程统计数据、趋势分析等功能。</p>
 *
 * <p>该服务基于响应式编程模型，所有统计查询方法均返回 {@link Mono}，
 * 内部对 MyBatis-Plus 的阻塞式 Mapper 统计调用统一通过
 * {@link BlockingIoSchedulers#jdbc()} 调度器承载，避免阻塞 Netty 事件循环线程。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>工作流统计概览：今日发起数、运行中总数、待办任务数、异常计数、平均完成时长等</li>
 *   <li>工作流趋势分析：按日统计最近 N 天的发起数量与完成数量</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Service
@RequiredArgsConstructor
public class WorkflowStatisticsService {

    /** 流程实例 Mapper，负责流程实例表的统计查询 */
    private final ProcessInstanceMapper processInstanceMapper;

    /** 任务 Mapper，负责任务表的统计查询 */
    private final TaskMapper taskMapper;

    /**
     * 获取工作流统计概览。
     *
     * <p>聚合今日发起流程数、运行中总数、待办任务总数、已挂起流程数、已超时任务数、
     * 已转办任务数、平均完成时长以及流程状态分布等指标，
     * 供监控页面展示整体运行状况。
     *
     * @return 统计概览 DTO
     */
    public Mono<WorkflowStatisticsDTO> getStatistics() {
        return requireTenant().flatMap(tenant -> Mono.fromCallable(() -> {
                    WorkflowStatisticsDTO stats = new WorkflowStatisticsDTO();

                    // 今日发起流程数
                    LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
                    LocalDateTime todayEnd = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);
                    Integer todayStarted = processInstanceMapper.countTodayStarted(tenant, todayStart, todayEnd);
                    stats.setTodayStarted(todayStarted != null ? todayStarted : 0);

                    Integer runningTotal = processInstanceMapper.countByStatus(tenant, "RUNNING");
                    stats.setRunningTotal(runningTotal != null ? runningTotal : 0);

                    Integer suspendedCount = processInstanceMapper.countSuspended(tenant);
                    stats.setSuspendedCount(suspendedCount != null ? suspendedCount : 0);

                    Integer pendingTaskTotal = taskMapper.countPendingTasks(tenant);
                    stats.setPendingTaskTotal(pendingTaskTotal != null ? pendingTaskTotal : 0);

                    Integer timeoutCount = taskMapper.countTimeoutTasks(tenant, LocalDateTime.now());
                    stats.setTimeoutCount(timeoutCount != null ? timeoutCount : 0);

                    Integer transferredCount = taskMapper.countTransferredTasks(tenant);
                    stats.setTransferredCount(transferredCount != null ? transferredCount : 0);

                    Long avgSeconds = processInstanceMapper.avgCompletionSeconds(tenant);
                    stats.setAvgCompletionSeconds(avgSeconds != null ? avgSeconds : 0L);

                    // 转换状态分布 List<Map> 为 Map<String, Integer>
                    List<Map<String, Object>> statusList = processInstanceMapper.statusDistribution(tenant);
                    Map<String, Integer> statusMap = new HashMap<>(16);
                    if (statusList != null) {
                        for (Map<String, Object> row : statusList) {
                            String status = (String) row.get("status");
                            Object countObj = row.get("count");
                            Integer count =
                                    countObj instanceof Long ? ((Long) countObj).intValue() : (Integer) countObj;
                            statusMap.put(status, count);
                        }
                    }
                    stats.setStatusDistribution(statusMap);

                    return stats;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc()));
    }

    /**
     * 获取工作流趋势数据（最近N天）。
     *
     * <p>按日统计给定天数范围内的每日流程发起数量与完成数量，
     * 三个列表（日期、发起数、完成数）索引一一对应，供趋势图表展示。
     *
     * @param days 天数，默认7天
     * @return 趋势 DTO
     */
    public Mono<WorkflowTrendDTO> getTrend(int days) {
        return requireTenant().flatMap(tenant -> Mono.fromCallable(() -> {
                    WorkflowTrendDTO trend = new WorkflowTrendDTO();
                    List<String> dates = new ArrayList<>(16);
                    List<Integer> startedCounts = new ArrayList<>(16);
                    List<Integer> completedCounts = new ArrayList<>(16);

                    LocalDate today = LocalDate.now();
                    for (int i = days - 1; i >= 0; i--) {
                        LocalDate date = today.minusDays(i);
                        dates.add(date.toString());

                        LocalDateTime dayStart = LocalDateTime.of(date, LocalTime.MIN);
                        LocalDateTime dayEnd = LocalDateTime.of(date, LocalTime.MAX);

                        // 转换 List<Map> 为 Map<LocalDate, Integer>
                        List<Map<String, Object>> startedList =
                                processInstanceMapper.countStartedByDateRange(tenant, dayStart, dayEnd);
                        List<Map<String, Object>> completedList =
                                processInstanceMapper.countCompletedByDateRange(tenant, dayStart, dayEnd);

                        Map<LocalDate, Integer> startedByDate = convertToDateMap(startedList);
                        Map<LocalDate, Integer> completedByDate = convertToDateMap(completedList);

                        startedCounts.add(startedByDate.getOrDefault(date, 0));
                        completedCounts.add(completedByDate.getOrDefault(date, 0));
                    }

                    trend.setDates(dates);
                    trend.setStartedCounts(startedCounts);
                    trend.setCompletedCounts(completedCounts);
                    return trend;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc()));
    }

    /**
     * 订阅时解析可信租户，缺失即失败，避免退化为跨租户统计。
     *
     * @return 租户标识
     */
    private Mono<String> requireTenant() {
        return Mono.deferContextual(context -> {
            String tenant = context.getOrDefault(TenantContext.REACTOR_CONTEXT_KEY, null);
            if (tenant == null || tenant.isBlank()) {
                return Mono.error(new IllegalStateException("工作流统计缺少有效租户上下文"));
            }
            return Mono.just(tenant);
        });
    }

    /**
     * 转换 List&lt;Map&gt; 为 Map&lt;LocalDate, Integer&gt;。
     *
     * <p>将统计查询返回的行集合转换为以日期为键、数量为值的映射。
     * 兼容 MySQL DATE() 返回的 {@link java.sql.Date} 以及直接返回的 {@link LocalDate} 两种类型。
     *
     * @param list 统计查询结果行集合
     * @return 以日期为键、数量为值的映射
     */
    private Map<LocalDate, Integer> convertToDateMap(List<Map<String, Object>> list) {
        Map<LocalDate, Integer> result = new HashMap<>(16);
        if (list != null) {
            for (Map<String, Object> row : list) {
                Object dateObj = row.get("date");
                Object countObj = row.get("count");

                // MySQL DATE() 返回 java.sql.Date
                LocalDate date = null;
                if (dateObj instanceof java.sql.Date) {
                    date = ((java.sql.Date) dateObj).toLocalDate();
                } else if (dateObj instanceof LocalDate) {
                    date = (LocalDate) dateObj;
                }

                Integer count = countObj instanceof Long ? ((Long) countObj).intValue() : (Integer) countObj;

                if (date != null && count != null) {
                    result.put(date, count);
                }
            }
        }
        return result;
    }
}
