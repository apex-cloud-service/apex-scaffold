package com.apex.scaffold.application.workflow;

import com.apex.scaffold.application.dto.WorkflowStatisticsDTO;
import com.apex.scaffold.application.dto.WorkflowTrendDTO;
import com.apex.scaffold.infrastructure.persistence.mapper.ProcessInstanceMapper;
import com.apex.scaffold.infrastructure.persistence.mapper.TaskMapper;
import com.apex.scaffold.infrastructure.persistence.po.ProcessInstancePO;
import com.apex.scaffold.infrastructure.persistence.po.TaskPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 工作流统计服务。
 *
 * <p>提供流程统计数据、趋势分析等功能。</p>
 *
 * @author Apex Team
 * @version 1.4.0
 * @since 1.4.0
 */
@Service
@RequiredArgsConstructor
public class WorkflowStatisticsService {

    private final ProcessInstanceMapper processInstanceMapper;
    private final TaskMapper taskMapper;

    /**
     * 获取工作流统计概览。
     *
     * @return 统计概览 DTO
     */
    public Mono<WorkflowStatisticsDTO> getStatistics() {
        return Mono.fromCallable(() -> {
                    WorkflowStatisticsDTO stats = new WorkflowStatisticsDTO();

                    // 今日发起流程数
                    LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
                    LocalDateTime todayEnd = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);
                    Integer todayStarted = processInstanceMapper.countTodayStarted(todayStart, todayEnd);
                    stats.setTodayStarted(todayStarted != null ? todayStarted : 0);

                    Integer runningTotal = processInstanceMapper.countByStatus("RUNNING");
                    stats.setRunningTotal(runningTotal != null ? runningTotal : 0);
                    
                    Integer suspendedCount = processInstanceMapper.countSuspended();
                    stats.setSuspendedCount(suspendedCount != null ? suspendedCount : 0);

                    Integer pendingTaskTotal = taskMapper.countPendingTasks();
                    stats.setPendingTaskTotal(pendingTaskTotal != null ? pendingTaskTotal : 0);
                    
                    Integer timeoutCount = taskMapper.countTimeoutTasks(LocalDateTime.now());
                    stats.setTimeoutCount(timeoutCount != null ? timeoutCount : 0);
                    
                    Integer transferredCount = taskMapper.countTransferredTasks();
                    stats.setTransferredCount(transferredCount != null ? transferredCount : 0);

                    Long avgSeconds = processInstanceMapper.avgCompletionSeconds();
                    stats.setAvgCompletionSeconds(avgSeconds != null ? avgSeconds : 0L);
                    
                    // 转换状态分布 List<Map> 为 Map<String, Integer>
                    List<Map<String, Object>> statusList = processInstanceMapper.statusDistribution();
                    Map<String, Integer> statusMap = new HashMap<>();
                    if (statusList != null) {
                        for (Map<String, Object> row : statusList) {
                            String status = (String) row.get("status");
                            Object countObj = row.get("count");
                            Integer count = countObj instanceof Long ? ((Long) countObj).intValue() : (Integer) countObj;
                            statusMap.put(status, count);
                        }
                    }
                    stats.setStatusDistribution(statusMap);

                    return stats;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 获取工作流趋势数据（最近N天）。
     *
     * @param days 天数，默认7天
     * @return 趋势 DTO
     */
    public Mono<WorkflowTrendDTO> getTrend(int days) {
        return Mono.fromCallable(() -> {
                    WorkflowTrendDTO trend = new WorkflowTrendDTO();
                    List<String> dates = new ArrayList<>();
                    List<Integer> startedCounts = new ArrayList<>();
                    List<Integer> completedCounts = new ArrayList<>();

                    LocalDate today = LocalDate.now();
                    for (int i = days - 1; i >= 0; i--) {
                        LocalDate date = today.minusDays(i);
                        dates.add(date.toString());

                        LocalDateTime dayStart = LocalDateTime.of(date, LocalTime.MIN);
                        LocalDateTime dayEnd = LocalDateTime.of(date, LocalTime.MAX);

                        // 转换 List<Map> 为 Map<LocalDate, Integer>
                        List<Map<String, Object>> startedList =
                                processInstanceMapper.countStartedByDateRange(dayStart, dayEnd);
                        List<Map<String, Object>> completedList =
                                processInstanceMapper.countCompletedByDateRange(dayStart, dayEnd);

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
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 转换 List<Map> 为 Map<LocalDate, Integer>。
     */
    private Map<LocalDate, Integer> convertToDateMap(List<Map<String, Object>> list) {
        Map<LocalDate, Integer> result = new HashMap<>();
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
