package com.apex.scaffold.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.dto.WorkflowStatisticsDTO;
import com.apex.scaffold.application.dto.WorkflowTrendDTO;
import com.apex.scaffold.application.workflow.WorkflowStatisticsService;
import com.apex.scaffold.infrastructure.persistence.mapper.ProcessInstanceMapper;
import com.apex.scaffold.infrastructure.persistence.mapper.TaskMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * {@link WorkflowStatisticsService} 单元测试
 *
 * <p>测试工作流统计服务的核心功能：统计概览和趋势分析
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowStatisticsService 单元测试")
class WorkflowStatisticsServiceTest {

    @Mock
    private ProcessInstanceMapper processInstanceMapper;

    @Mock
    private TaskMapper taskMapper;

    @InjectMocks
    private WorkflowStatisticsService statisticsService;

    @BeforeEach
    void setUp() {
        // Mock 数据准备会在各测试方法中完成
    }

    @Test
    @DisplayName("getStatistics - 成功返回统计概览数据")
    void testGetStatistics_Success() {
        // Given: 模拟今日发起数
        when(processInstanceMapper.countTodayStarted(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(15);

        // 模拟运行中总数
        when(processInstanceMapper.countByStatus("RUNNING")).thenReturn(8);
        when(processInstanceMapper.countSuspended()).thenReturn(2);

        // 模拟待办任务总数
        when(taskMapper.countPendingTasks()).thenReturn(23);
        when(taskMapper.countTimeoutTasks(any(LocalDateTime.class))).thenReturn(4);
        when(taskMapper.countTransferredTasks()).thenReturn(6);

        // 模拟平均完成时长（秒）
        when(processInstanceMapper.avgCompletionSeconds()).thenReturn(7200L);

        // 模拟状态分布
        List<Map<String, Object>> statusDist = new ArrayList<>();
        Map<String, Object> running = new HashMap<>();
        running.put("status", "RUNNING");
        running.put("count", 8);
        statusDist.add(running);

        Map<String, Object> approved = new HashMap<>();
        approved.put("status", "APPROVED");
        approved.put("count", 45);
        statusDist.add(approved);

        Map<String, Object> rejected = new HashMap<>();
        rejected.put("status", "REJECTED");
        rejected.put("count", 3);
        statusDist.add(rejected);

        when(processInstanceMapper.statusDistribution()).thenReturn(statusDist);

        // When: 调用统计服务
        Mono<WorkflowStatisticsDTO> result = statisticsService.getStatistics();

        // Then: 验证结果
        StepVerifier.create(result)
                .assertNext(dto -> {
                    assertThat(dto.getTodayStarted()).isEqualTo(15);
                    assertThat(dto.getRunningTotal()).isEqualTo(8);
                    assertThat(dto.getPendingTaskTotal()).isEqualTo(23);
                    assertThat(dto.getSuspendedCount()).isEqualTo(2);
                    assertThat(dto.getTimeoutCount()).isEqualTo(4);
                    assertThat(dto.getTransferredCount()).isEqualTo(6);
                    assertThat(dto.getAvgCompletionSeconds()).isEqualTo(7200L);
                    assertThat(dto.getStatusDistribution()).hasSize(3);
                    assertThat(dto.getStatusDistribution().get("RUNNING")).isEqualTo(8);
                    assertThat(dto.getStatusDistribution().get("APPROVED")).isEqualTo(45);
                    assertThat(dto.getStatusDistribution().get("REJECTED")).isEqualTo(3);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("getStatistics - 无数据时返回默认值")
    void testGetStatistics_NoData() {
        // Given: 所有查询返回 0 或空
        when(processInstanceMapper.countTodayStarted(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(0);
        when(processInstanceMapper.countByStatus("RUNNING")).thenReturn(0);
        when(processInstanceMapper.countSuspended()).thenReturn(0);
        when(taskMapper.countPendingTasks()).thenReturn(0);
        when(taskMapper.countTimeoutTasks(any(LocalDateTime.class))).thenReturn(0);
        when(taskMapper.countTransferredTasks()).thenReturn(0);
        when(processInstanceMapper.avgCompletionSeconds()).thenReturn(0L);
        when(processInstanceMapper.statusDistribution()).thenReturn(new ArrayList<>());

        // When: 调用统计服务
        Mono<WorkflowStatisticsDTO> result = statisticsService.getStatistics();

        // Then: 验证结果
        StepVerifier.create(result)
                .assertNext(dto -> {
                    assertThat(dto.getTodayStarted()).isZero();
                    assertThat(dto.getRunningTotal()).isZero();
                    assertThat(dto.getPendingTaskTotal()).isZero();
                    assertThat(dto.getAvgCompletionSeconds()).isZero();
                    assertThat(dto.getStatusDistribution()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("getTrend - 7天趋势数据")
    void testGetTrend_7Days() {
        // Given: 模拟7天数据
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> startedList = new ArrayList<>();
        List<Map<String, Object>> completedList = new ArrayList<>();

        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);

            Map<String, Object> startedMap = new HashMap<>();
            startedMap.put("date", date);
            startedMap.put("count", 5 + i);
            startedList.add(startedMap);

            Map<String, Object> completedMap = new HashMap<>();
            completedMap.put("date", date);
            completedMap.put("count", 4 + i);
            completedList.add(completedMap);
        }

        when(processInstanceMapper.countStartedByDateRange(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(startedList);
        when(processInstanceMapper.countCompletedByDateRange(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(completedList);

        // When: 调用趋势分析
        Mono<WorkflowTrendDTO> result = statisticsService.getTrend(7);

        // Then: 验证结果
        StepVerifier.create(result)
                .assertNext(dto -> {
                    assertThat(dto.getDates()).hasSize(7);
                    assertThat(dto.getStarted()).hasSize(7);
                    assertThat(dto.getCompleted()).hasSize(7);

                    // 验证日期格式
                    assertThat(dto.getDates().get(0)).matches("\\d{4}-\\d{2}-\\d{2}");

                    // 验证数据顺序（从旧到新）
                    assertThat(dto.getStarted().get(0)).isEqualTo(11); // 6天前：5+6
                    assertThat(dto.getStarted().get(6)).isEqualTo(5); // 今天：5+0
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("getTrend - 30天趋势数据")
    void testGetTrend_30Days() {
        // Given: 模拟30天数据
        List<Map<String, Object>> startedList = new ArrayList<>();
        List<Map<String, Object>> completedList = new ArrayList<>();

        LocalDate today = LocalDate.now();
        for (int i = 29; i >= 0; i--) {
            LocalDate date = today.minusDays(i);

            Map<String, Object> startedMap = new HashMap<>();
            startedMap.put("date", date);
            startedMap.put("count", 10);
            startedList.add(startedMap);

            Map<String, Object> completedMap = new HashMap<>();
            completedMap.put("date", date);
            completedMap.put("count", 9);
            completedList.add(completedMap);
        }

        when(processInstanceMapper.countStartedByDateRange(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(startedList);
        when(processInstanceMapper.countCompletedByDateRange(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(completedList);

        // When: 调用30天趋势
        Mono<WorkflowTrendDTO> result = statisticsService.getTrend(30);

        // Then: 验证结果
        StepVerifier.create(result)
                .assertNext(dto -> {
                    assertThat(dto.getDates()).hasSize(30);
                    assertThat(dto.getStarted()).hasSize(30);
                    assertThat(dto.getCompleted()).hasSize(30);
                    assertThat(dto.getStarted()).allMatch(count -> count == 10);
                    assertThat(dto.getCompleted()).allMatch(count -> count == 9);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("getTrend - 部分日期无数据时填充0")
    void testGetTrend_PartialData() {
        // Given: 只有部分日期有数据
        List<Map<String, Object>> startedList = new ArrayList<>();
        List<Map<String, Object>> completedList = new ArrayList<>();

        LocalDate today = LocalDate.now();
        // 只填充第1、3、5天的数据
        Map<String, Object> started1 = new HashMap<>();
        started1.put("date", today.minusDays(6));
        started1.put("count", 5);
        startedList.add(started1);

        Map<String, Object> started2 = new HashMap<>();
        started2.put("date", today.minusDays(4));
        started2.put("count", 8);
        startedList.add(started2);

        Map<String, Object> started3 = new HashMap<>();
        started3.put("date", today.minusDays(2));
        started3.put("count", 12);
        startedList.add(started3);

        Map<String, Object> completed1 = new HashMap<>();
        completed1.put("date", today.minusDays(6));
        completed1.put("count", 4);
        completedList.add(completed1);

        Map<String, Object> completed2 = new HashMap<>();
        completed2.put("date", today.minusDays(4));
        completed2.put("count", 7);
        completedList.add(completed2);

        Map<String, Object> completed3 = new HashMap<>();
        completed3.put("date", today.minusDays(2));
        completed3.put("count", 10);
        completedList.add(completed3);

        when(processInstanceMapper.countStartedByDateRange(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(startedList);
        when(processInstanceMapper.countCompletedByDateRange(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(completedList);

        // When: 调用趋势分析
        Mono<WorkflowTrendDTO> result = statisticsService.getTrend(7);

        // Then: 验证结果，无数据的日期应该为0
        StepVerifier.create(result)
                .assertNext(dto -> {
                    assertThat(dto.getDates()).hasSize(7);
                    assertThat(dto.getStarted()).hasSize(7);
                    assertThat(dto.getCompleted()).hasSize(7);

                    // 第1天有数据
                    assertThat(dto.getStarted().get(0)).isEqualTo(5);
                    // 第2天无数据，应为0
                    assertThat(dto.getStarted().get(1)).isZero();
                    // 第3天有数据
                    assertThat(dto.getStarted().get(2)).isEqualTo(8);
                })
                .verifyComplete();
    }
}
