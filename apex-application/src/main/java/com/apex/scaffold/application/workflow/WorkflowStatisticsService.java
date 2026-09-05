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
                    long todayStarted = processInstanceMapper.selectCount(new LambdaQueryWrapper<ProcessInstancePO>()
                            .ge(ProcessInstancePO::getCreateTime, todayStart)
                            .le(ProcessInstancePO::getCreateTime, todayEnd));
                    stats.setTodayStarted((int) todayStarted);

                    // 运行中流程总数
                    long runningTotal = processInstanceMapper.selectCount(
                            new LambdaQueryWrapper<ProcessInstancePO>().eq(ProcessInstancePO::getStatus, "RUNNING"));
                    stats.setRunningTotal((int) runningTotal);

                    // 待办任务总数
                    long pendingTaskTotal =
                            taskMapper.selectCount(new LambdaQueryWrapper<TaskPO>().eq(TaskPO::getStatus, "PENDING"));
                    stats.setPendingTaskTotal((int) pendingTaskTotal);

                    // 平均完成时长（秒）
                    // 这里简化处理，实际应通过原生 SQL 计算 AVG(TIMESTAMPDIFF(SECOND, start_time, end_time))
                    stats.setAvgCompletionSeconds(0L);

                    // 流程状态分布
                    Map<String, Integer> statusDistribution = new HashMap<>();
                    statusDistribution.put("RUNNING", (int) runningTotal);
                    statusDistribution.put("APPROVED", 0);
                    statusDistribution.put("REJECTED", 0);
                    statusDistribution.put("CANCELLED", 0);
                    stats.setStatusDistribution(statusDistribution);

                    return stats;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 获取工作流趋势数据（最近7天）。
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

                        // 当天发起数量
                        long started = processInstanceMapper.selectCount(new LambdaQueryWrapper<ProcessInstancePO>()
                                .ge(ProcessInstancePO::getCreateTime, dayStart)
                                .le(ProcessInstancePO::getCreateTime, dayEnd));
                        startedCounts.add((int) started);

                        // 当天完成数量
                        long completed = processInstanceMapper.selectCount(new LambdaQueryWrapper<ProcessInstancePO>()
                                .in(ProcessInstancePO::getStatus, Arrays.asList("APPROVED", "REJECTED"))
                                .ge(ProcessInstancePO::getEndTime, dayStart)
                                .le(ProcessInstancePO::getEndTime, dayEnd));
                        completedCounts.add((int) completed);
                    }

                    trend.setDates(dates);
                    trend.setStartedCounts(startedCounts);
                    trend.setCompletedCounts(completedCounts);
                    return trend;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }
}
