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
import java.util.HashMap;
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
        Map<String, Integer> statusDist = new HashMap<>();
        statusDist.put("RUNNING", 8);
        statusDist.put("APPROVED", 45);
        statusDist.put("REJECTED", 3);
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
        when(processInstanceMapper.statusDistribution()).thenReturn(new HashMap<>());

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
    @DisplayName("getTrend - 成功返回7天趋势数据")
    void testGetTrend_Success() {
        // Given: 模拟最近7天的发起和完成数据
        LocalDate today = LocalDate.now();
        Map<LocalDate, Integer> startedMap = new HashMap<>();
        Map<LocalDate, Integer> completedMap = new HashMap<>();

        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            startedMap.put(date, 5 + i);
            completedMap.put(date, 4 + i);
        }

        when(processInstanceMapper.countStartedByDateRange(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(startedMap);
        when(processInstanceMapper.countCompletedByDateRange(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(completedMap);

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
        Map<LocalDate, Integer> startedMap = new HashMap<>();
        Map<LocalDate, Integer> completedMap = new HashMap<>();

        LocalDate today = LocalDate.now();
        for (int i = 29; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            startedMap.put(date, 10);
            completedMap.put(date, 9);
        }

        when(processInstanceMapper.countStartedByDateRange(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(startedMap);
        when(processInstanceMapper.countCompletedByDateRange(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(completedMap);

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
        Map<LocalDate, Integer> startedMap = new HashMap<>();
        Map<LocalDate, Integer> completedMap = new HashMap<>();

        LocalDate today = LocalDate.now();
        // 只填充第1、3、5天的数据
        startedMap.put(today.minusDays(6), 5);
        startedMap.put(today.minusDays(4), 8);
        startedMap.put(today.minusDays(2), 12);

        completedMap.put(today.minusDays(6), 4);
        completedMap.put(today.minusDays(4), 7);
        completedMap.put(today.minusDays(2), 10);

        when(processInstanceMapper.countStartedByDateRange(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(startedMap);
        when(processInstanceMapper.countCompletedByDateRange(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(completedMap);

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
