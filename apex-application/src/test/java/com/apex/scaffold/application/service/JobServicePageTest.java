package com.apex.scaffold.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.application.assembler.JobAssembler;
import com.apex.scaffold.application.dto.admin.JobDTO;
import com.apex.scaffold.application.dto.admin.JobQueryRequest;
import com.apex.scaffold.application.job.JobScheduler;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.domain.model.entity.JobEntity;
import com.apex.scaffold.domain.repository.JobRepository;
import java.util.List;
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
 * {@link JobService} 分页查询功能单元测试。
 *
 * <p>测试定时任务分页查询接口的核心业务逻辑，包括分页参数处理、条件筛选、
 * 数据转换和分页响应构建。使用 Mockito 模拟依赖，验证查询流程的正确性。</p>
 *
 * <h2>测试场景</h2>
 * <ul>
 *   <li>无条件分页查询</li>
 *   <li>按任务名称模糊查询</li>
 *   <li>按任务分组精确查询</li>
 *   <li>按状态筛选查询</li>
 *   <li>组合条件查询</li>
 *   <li>空结果集处理</li>
 *   <li>分页信息正确性</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("定时任务服务 - 分页查询测试")
class JobServicePageTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobScheduler jobScheduler;

    @Mock
    private JobLogService jobLogService;

    @Mock
    private JobAssembler jobAssembler;

    @InjectMocks
    private JobService jobService;

    private JobEntity testJobEntity1;
    private JobEntity testJobEntity2;
    private JobDTO testJobDTO1;
    private JobDTO testJobDTO2;

    /**
     * 测试前置准备。
     *
     * <p>初始化测试数据和租户上下文，准备用于模拟的任务实体和 DTO 对象。</p>
     */
    @BeforeEach
    void setUp() {
        TenantContext.setTenantId("test-tenant");

        testJobEntity1 = JobEntity.create("数据同步任务", "DEFAULT", "syncBean.execute", "0 0 2 * * ?", "test-tenant");
        testJobEntity1.setId(1L);
        testJobEntity1.setStatus(1);

        testJobEntity2 = JobEntity.create("报表生成任务", "REPORT", "reportBean.generate", "0 0 6 * * ?", "test-tenant");
        testJobEntity2.setId(2L);
        testJobEntity2.setStatus(1);

        testJobDTO1 = JobDTO.builder()
                .id(1L)
                .jobName("数据同步任务")
                .jobGroup("DEFAULT")
                .invokeTarget("syncBean.execute")
                .cronExpression("0 0 2 * * ?")
                .status(1)
                .build();

        testJobDTO2 = JobDTO.builder()
                .id(2L)
                .jobName("报表生成任务")
                .jobGroup("REPORT")
                .invokeTarget("reportBean.generate")
                .cronExpression("0 0 6 * * ?")
                .status(1)
                .build();
    }

    /**
     * 测试无条件分页查询。
     *
     * <p>验证不传递任何筛选条件时，能够正确执行分页查询并返回所有任务数据。</p>
     */
    @Test
    @DisplayName("无条件分页查询 - 应返回所有任务的分页数据")
    void testPageJobsWithoutConditions() {
        JobQueryRequest request =
                JobQueryRequest.builder().pageNum(1).pageSize(10).build();

        when(jobRepository.findByPage(null, null, null, 0, 10))
                .thenReturn(Mono.just(List.of(testJobEntity1, testJobEntity2)));
        when(jobRepository.countByCondition(null, null, null)).thenReturn(Mono.just(2L));
        when(jobAssembler.toDTO(testJobEntity1)).thenReturn(testJobDTO1);
        when(jobAssembler.toDTO(testJobEntity2)).thenReturn(testJobDTO2);

        StepVerifier.create(jobService.pageJobs(request))
                .assertNext(pageResponse -> {
                    assertNotNull(pageResponse, "分页响应不应为空");
                    assertEquals(2, pageResponse.getTotal(), "总记录数应为 2");
                    assertEquals(1, pageResponse.getPageNum(), "当前页码应为 1");
                    assertEquals(10, pageResponse.getPageSize(), "每页大小应为 10");
                    assertEquals(1, pageResponse.getPages(), "总页数应为 1");
                    assertFalse(pageResponse.getHasNext(), "不应有下一页");
                    assertEquals(2, pageResponse.getList().size(), "数据列表应包含 2 条记录");
                })
                .verifyComplete();

        verify(jobRepository).findByPage(null, null, null, 0, 10);
        verify(jobRepository).countByCondition(null, null, null);
        verify(jobAssembler, times(2)).toDTO(any(JobEntity.class));
    }

    /**
     * 测试按任务名称模糊查询。
     *
     * <p>验证传递任务名称关键词时，能够正确执行模糊匹配查询。</p>
     */
    @Test
    @DisplayName("按任务名称模糊查询 - 应返回匹配的任务")
    void testPageJobsByJobName() {
        JobQueryRequest request = JobQueryRequest.builder()
                .pageNum(1)
                .pageSize(10)
                .jobName("数据同步")
                .build();

        when(jobRepository.findByPage("数据同步", null, null, 0, 10)).thenReturn(Mono.just(List.of(testJobEntity1)));
        when(jobRepository.countByCondition("数据同步", null, null)).thenReturn(Mono.just(1L));
        when(jobAssembler.toDTO(testJobEntity1)).thenReturn(testJobDTO1);

        StepVerifier.create(jobService.pageJobs(request))
                .assertNext(pageResponse -> {
                    assertEquals(1, pageResponse.getTotal(), "总记录数应为 1");
                    assertEquals(1, pageResponse.getList().size(), "数据列表应包含 1 条记录");
                    assertEquals("数据同步任务", pageResponse.getList().get(0).getJobName(), "任务名称应匹配");
                })
                .verifyComplete();

        verify(jobRepository).findByPage("数据同步", null, null, 0, 10);
        verify(jobRepository).countByCondition("数据同步", null, null);
    }

    /**
     * 测试按任务分组精确查询。
     *
     * <p>验证传递任务分组时，能够正确执行精确匹配查询。</p>
     */
    @Test
    @DisplayName("按任务分组查询 - 应返回指定分组的任务")
    void testPageJobsByJobGroup() {
        JobQueryRequest request = JobQueryRequest.builder()
                .pageNum(1)
                .pageSize(10)
                .jobGroup("REPORT")
                .build();

        when(jobRepository.findByPage(null, "REPORT", null, 0, 10)).thenReturn(Mono.just(List.of(testJobEntity2)));
        when(jobRepository.countByCondition(null, "REPORT", null)).thenReturn(Mono.just(1L));
        when(jobAssembler.toDTO(testJobEntity2)).thenReturn(testJobDTO2);

        StepVerifier.create(jobService.pageJobs(request))
                .assertNext(pageResponse -> {
                    assertEquals(1, pageResponse.getTotal(), "总记录数应为 1");
                    assertEquals("REPORT", pageResponse.getList().get(0).getJobGroup(), "任务分组应为 REPORT");
                })
                .verifyComplete();

        verify(jobRepository).findByPage(null, "REPORT", null, 0, 10);
    }

    /**
     * 测试按状态筛选查询。
     *
     * <p>验证传递任务状态时，能够正确执行状态筛选查询。</p>
     */
    @Test
    @DisplayName("按状态筛选查询 - 应返回指定状态的任务")
    void testPageJobsByStatus() {
        JobQueryRequest request =
                JobQueryRequest.builder().pageNum(1).pageSize(10).status(1).build();

        when(jobRepository.findByPage(null, null, 1, 0, 10))
                .thenReturn(Mono.just(List.of(testJobEntity1, testJobEntity2)));
        when(jobRepository.countByCondition(null, null, 1)).thenReturn(Mono.just(2L));
        when(jobAssembler.toDTO(testJobEntity1)).thenReturn(testJobDTO1);
        when(jobAssembler.toDTO(testJobEntity2)).thenReturn(testJobDTO2);

        StepVerifier.create(jobService.pageJobs(request))
                .assertNext(pageResponse -> {
                    assertEquals(2, pageResponse.getTotal(), "总记录数应为 2");
                    assertTrue(
                            pageResponse.getList().stream()
                                    .allMatch(dto -> dto.getStatus().equals(1)),
                            "所有任务状态应为 1");
                })
                .verifyComplete();
    }

    /**
     * 测试组合条件查询。
     *
     * <p>验证同时传递多个筛选条件时，能够正确执行组合查询。</p>
     */
    @Test
    @DisplayName("组合条件查询 - 应同时应用多个筛选条件")
    void testPageJobsWithMultipleConditions() {
        JobQueryRequest request = JobQueryRequest.builder()
                .pageNum(1)
                .pageSize(10)
                .jobName("同步")
                .jobGroup("DEFAULT")
                .status(1)
                .build();

        when(jobRepository.findByPage("同步", "DEFAULT", 1, 0, 10)).thenReturn(Mono.just(List.of(testJobEntity1)));
        when(jobRepository.countByCondition("同步", "DEFAULT", 1)).thenReturn(Mono.just(1L));
        when(jobAssembler.toDTO(testJobEntity1)).thenReturn(testJobDTO1);

        StepVerifier.create(jobService.pageJobs(request))
                .assertNext(pageResponse -> {
                    assertEquals(1, pageResponse.getTotal(), "总记录数应为 1");
                    JobDTO dto = pageResponse.getList().get(0);
                    assertTrue(dto.getJobName().contains("同步"), "任务名称应包含'同步'");
                    assertEquals("DEFAULT", dto.getJobGroup(), "任务分组应为 DEFAULT");
                    assertEquals(1, dto.getStatus(), "任务状态应为 1");
                })
                .verifyComplete();

        verify(jobRepository).findByPage("同步", "DEFAULT", 1, 0, 10);
        verify(jobRepository).countByCondition("同步", "DEFAULT", 1);
    }

    /**
     * 测试空结果集处理。
     *
     * <p>验证查询条件不匹配任何数据时，能够正确返回空分页响应。</p>
     */
    @Test
    @DisplayName("空结果集 - 应返回空列表的分页响应")
    void testPageJobsWithEmptyResult() {
        JobQueryRequest request = JobQueryRequest.builder()
                .pageNum(1)
                .pageSize(10)
                .jobName("不存在的任务")
                .build();

        when(jobRepository.findByPage("不存在的任务", null, null, 0, 10)).thenReturn(Mono.just(List.of()));
        when(jobRepository.countByCondition("不存在的任务", null, null)).thenReturn(Mono.just(0L));

        StepVerifier.create(jobService.pageJobs(request))
                .assertNext(pageResponse -> {
                    assertEquals(0, pageResponse.getTotal(), "总记录数应为 0");
                    assertEquals(0, pageResponse.getList().size(), "数据列表应为空");
                    assertEquals(0, pageResponse.getPages(), "总页数应为 0");
                    assertFalse(pageResponse.getHasNext(), "不应有下一页");
                })
                .verifyComplete();
    }

    /**
     * 测试分页偏移量计算。
     *
     * <p>验证第二页查询时，分页偏移量计算正确。</p>
     */
    @Test
    @DisplayName("第二页查询 - 应正确计算分页偏移量")
    void testPageJobsSecondPage() {
        JobQueryRequest request =
                JobQueryRequest.builder().pageNum(2).pageSize(5).build();

        when(jobRepository.findByPage(null, null, null, 5, 5)).thenReturn(Mono.just(List.of(testJobEntity2)));
        when(jobRepository.countByCondition(null, null, null)).thenReturn(Mono.just(10L));
        when(jobAssembler.toDTO(testJobEntity2)).thenReturn(testJobDTO2);

        StepVerifier.create(jobService.pageJobs(request))
                .assertNext(pageResponse -> {
                    assertEquals(10, pageResponse.getTotal(), "总记录数应为 10");
                    assertEquals(2, pageResponse.getPageNum(), "当前页码应为 2");
                    assertEquals(5, pageResponse.getPageSize(), "每页大小应为 5");
                    assertEquals(2, pageResponse.getPages(), "总页数应为 2");
                    assertFalse(pageResponse.getHasNext(), "不应有下一页");
                })
                .verifyComplete();

        verify(jobRepository).findByPage(null, null, null, 5, 5);
    }
}
