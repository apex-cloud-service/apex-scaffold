package com.apex.scaffold.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.assembler.JobAssembler;
import com.apex.scaffold.application.dto.admin.JobDTO;
import com.apex.scaffold.application.dto.admin.JobRequest;
import com.apex.scaffold.application.job.JobScheduler;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
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
 * 定时任务应用服务单元测试。
 *
 * <p>覆盖任务 CRUD、启停与手动触发的核心业务规则，重点验证
 * Cron 表达式合法性校验与调度器联动（注册/取消/触发）。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class JobServiceTest {

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

    private JobEntity jobEntity;
    private JobDTO jobDTO;

    @BeforeEach
    void setUp() {
        jobEntity = JobEntity.create("清理过期令牌", "SYSTEM", "tokenCleanupTask.cleanup()", "0 0 2 * * ?");
        jobEntity.setId(1L);

        jobDTO = new JobDTO();
        jobDTO.setId(1L);
        jobDTO.setJobName("清理过期令牌");
        jobDTO.setJobGroup("SYSTEM");
        jobDTO.setInvokeTarget("tokenCleanupTask.cleanup()");
        jobDTO.setCronExpression("0 0 2 * * ?");

        lenient().when(jobAssembler.toDTO(any(JobEntity.class))).thenReturn(jobDTO);
    }

    /**
     * 测试创建任务成功并注册调度。
     */
    @Test
    @DisplayName("createJob - 成功创建并注册调度")
    void createJob_Success() {
        JobRequest request = new JobRequest();
        request.setJobName("清理过期令牌");
        request.setJobGroup("SYSTEM");
        request.setInvokeTarget("tokenCleanupTask.cleanup()");
        request.setCronExpression("0 0 2 * * ?");

        TenantContext.setTenantId("tenant-a");
        when(jobRepository.insert(any(JobEntity.class))).thenReturn(Mono.just(1));
        when(jobScheduler.register(any(JobEntity.class))).thenReturn(Mono.empty());

        jobService
                .createJob(request)
                .as(StepVerifier::create)
                .assertNext(job -> {
                    assertThat(job.getJobName()).isEqualTo("清理过期令牌");
                    assertThat(job.isEnabled()).isTrue();
                })
                .verifyComplete();

        verify(jobScheduler).register(any(JobEntity.class));
    }

    @Test
    @DisplayName("createJob - 缺少可信租户上下文时拒绝")
    void createJob_WithoutTenantFails() {
        TenantContext.clear();
        JobRequest request = new JobRequest();
        request.setJobName("无租户任务");
        request.setInvokeTarget("tokenCleanupTask.cleanup()");
        request.setCronExpression("0 0 2 * * ?");

        jobService
                .createJob(request)
                .as(StepVerifier::create)
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("租户上下文不存在"))
                .verify();
    }

    /**
     * 测试非法 Cron 表达式被拒绝。
     */
    @Test
    @DisplayName("createJob - 日字段和周字段同时使用问号时返回参数错误")
    void createJob_InvalidQuestionMarkCronFailsWithParamError() {
        JobRequest request = new JobRequest();
        request.setJobName("非法任务");
        request.setInvokeTarget("tokenCleanupTask.cleanup()");
        request.setCronExpression("0 0 0 ? * ?");

        jobService
                .createJob(request)
                .as(StepVerifier::create)
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BusinessException.class);
                    BusinessException exception = (BusinessException) error;
                    assertThat(exception.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
                    assertThat(exception.getMessage()).contains("Cron 表达式格式不合法");
                })
                .verify();

        verifyNoInteractions(jobRepository, jobScheduler);
    }

    /**
     * 测试更新任务成功并重新注册调度。
     */
    @Test
    @DisplayName("updateJob - 成功更新并重新注册调度")
    void updateJob_Success() {
        JobRequest request = new JobRequest();
        request.setJobName("清理过期令牌-更新");
        request.setJobGroup("SYSTEM");
        request.setInvokeTarget("tokenCleanupTask.cleanup()");
        request.setCronExpression("0 0 3 * * ?");

        when(jobRepository.findById(1L)).thenReturn(Mono.just(jobEntity));
        when(jobRepository.updateById(any(JobEntity.class))).thenReturn(Mono.just(1));
        when(jobScheduler.register(any(JobEntity.class))).thenReturn(Mono.empty());

        jobService
                .updateJob(1L, request)
                .as(StepVerifier::create)
                .assertNext(job -> assertThat(job.getCronExpression()).isEqualTo("0 0 3 * * ?"))
                .verifyComplete();

        verify(jobScheduler).register(any(JobEntity.class));
    }

    /**
     * 测试删除任务成功并取消调度。
     */
    @Test
    @DisplayName("deleteJob - 成功删除并取消调度")
    void deleteJob_Success() {
        when(jobRepository.findById(1L)).thenReturn(Mono.just(jobEntity));
        when(jobRepository.deleteById(1L)).thenReturn(Mono.just(1));
        when(jobScheduler.unregister(1L)).thenReturn(Mono.empty());

        jobService.deleteJob(1L).as(StepVerifier::create).expectNext(true).verifyComplete();

        verify(jobScheduler).unregister(1L);
    }

    /**
     * 测试删除不存在的任务抛出异常。
     */
    @Test
    @DisplayName("deleteJob - 任务不存在时抛出异常")
    void deleteJob_NotFoundFails() {
        when(jobRepository.findById(99L)).thenReturn(Mono.empty());

        jobService
                .deleteJob(99L)
                .as(StepVerifier::create)
                .expectError(BusinessException.class)
                .verify();
    }

    /**
     * 测试停用任务时取消调度。
     */
    @Test
    @DisplayName("updateStatus - 停用任务时取消调度")
    void updateStatus_DisableUnregisters() {
        when(jobRepository.findById(1L)).thenReturn(Mono.just(jobEntity));
        when(jobRepository.updateById(any(JobEntity.class))).thenReturn(Mono.just(1));
        when(jobScheduler.unregister(1L)).thenReturn(Mono.empty());

        jobService
                .updateStatus(1L, false)
                .as(StepVerifier::create)
                .assertNext(job -> assertThat(job.isEnabled()).isFalse())
                .verifyComplete();

        verify(jobScheduler).unregister(1L);
    }

    /**
     * 测试启用任务时注册调度。
     */
    @Test
    @DisplayName("updateStatus - 启用任务时注册调度")
    void updateStatus_EnableRegisters() {
        jobEntity.disable();
        when(jobRepository.findById(1L)).thenReturn(Mono.just(jobEntity));
        when(jobRepository.updateById(any(JobEntity.class))).thenReturn(Mono.just(1));
        when(jobScheduler.register(any(JobEntity.class))).thenReturn(Mono.empty());

        jobService
                .updateStatus(1L, true)
                .as(StepVerifier::create)
                .assertNext(job -> assertThat(job.isEnabled()).isTrue())
                .verifyComplete();

        verify(jobScheduler).register(any(JobEntity.class));
    }

    /**
     * 测试手动触发任务。
     */
    @Test
    @DisplayName("runOnce - 成功手动触发任务")
    void runOnce_Success() {
        when(jobRepository.findById(1L)).thenReturn(Mono.just(jobEntity));
        when(jobScheduler.trigger(jobEntity)).thenReturn(Mono.empty());

        jobService.runOnce(1L).as(StepVerifier::create).verifyComplete();

        verify(jobScheduler).trigger(jobEntity);
    }

    /**
     * 测试查询任务列表。
     */
    @Test
    @DisplayName("listJobs - 成功查询全部任务")
    void listJobs_Success() {
        when(jobRepository.findAll()).thenReturn(Mono.just(List.of(jobEntity)));

        jobService
                .listJobs()
                .as(StepVerifier::create)
                .assertNext(jobs -> {
                    assertThat(jobs).hasSize(1);
                    assertThat(jobs.get(0).getJobName()).isEqualTo("清理过期令牌");
                })
                .verifyComplete();
    }

    /**
     * 测试分页查询任务列表 - 无条件查询。
     */
    @Test
    @DisplayName("pageJobs - 无条件分页查询成功")
    void pageJobs_WithoutFiltersSuccess() {
        com.apex.scaffold.application.dto.admin.JobQueryRequest queryRequest =
                com.apex.scaffold.application.dto.admin.JobQueryRequest.builder()
                        .pageNum(1)
                        .pageSize(10)
                        .build();

        JobEntity job2 = JobEntity.create("数据备份", "SYSTEM", "backupTask.backup()", "0 0 3 * * ?");
        job2.setId(2L);

        when(jobRepository.countByCondition(null, null, null)).thenReturn(Mono.just(2L));
        when(jobRepository.findByPage(null, null, null, 0, 10)).thenReturn(Mono.just(List.of(jobEntity, job2)));

        JobDTO jobDTO2 = new JobDTO();
        jobDTO2.setId(2L);
        jobDTO2.setJobName("数据备份");
        jobDTO2.setJobGroup("SYSTEM");
        when(jobAssembler.toDTO(job2)).thenReturn(jobDTO2);

        jobService
                .pageJobs(queryRequest)
                .as(StepVerifier::create)
                .assertNext(pageResponse -> {
                    assertThat(pageResponse.getTotal()).isEqualTo(2L);
                    assertThat(pageResponse.getRecords()).hasSize(2);
                    assertThat(pageResponse.getRecords().get(0).getJobName()).isEqualTo("清理过期令牌");
                    assertThat(pageResponse.getRecords().get(1).getJobName()).isEqualTo("数据备份");
                    assertThat(pageResponse.getPageNum()).isEqualTo(1);
                    assertThat(pageResponse.getPageSize()).isEqualTo(10);
                })
                .verifyComplete();
    }

    /**
     * 测试分页查询任务列表 - 按任务名称模糊查询。
     */
    @Test
    @DisplayName("pageJobs - 按任务名称模糊查询成功")
    void pageJobs_WithJobNameFilterSuccess() {
        com.apex.scaffold.application.dto.admin.JobQueryRequest queryRequest =
                com.apex.scaffold.application.dto.admin.JobQueryRequest.builder()
                        .pageNum(1)
                        .pageSize(10)
                        .jobName("清理")
                        .build();

        when(jobRepository.countByCondition("清理", null, null)).thenReturn(Mono.just(1L));
        when(jobRepository.findByPage("清理", null, null, 0, 10)).thenReturn(Mono.just(List.of(jobEntity)));

        jobService
                .pageJobs(queryRequest)
                .as(StepVerifier::create)
                .assertNext(pageResponse -> {
                    assertThat(pageResponse.getTotal()).isEqualTo(1L);
                    assertThat(pageResponse.getRecords()).hasSize(1);
                    assertThat(pageResponse.getRecords().get(0).getJobName()).isEqualTo("清理过期令牌");
                })
                .verifyComplete();
    }

    /**
     * 测试分页查询任务列表 - 按任务分组和状态查询。
     */
    @Test
    @DisplayName("pageJobs - 按任务分组和状态查询成功")
    void pageJobs_WithGroupAndStatusFilterSuccess() {
        com.apex.scaffold.application.dto.admin.JobQueryRequest queryRequest =
                com.apex.scaffold.application.dto.admin.JobQueryRequest.builder()
                        .pageNum(1)
                        .pageSize(10)
                        .jobGroup("SYSTEM")
                        .status(1)
                        .build();

        when(jobRepository.countByCondition(null, "SYSTEM", 1)).thenReturn(Mono.just(1L));
        when(jobRepository.findByPage(null, "SYSTEM", 1, 0, 10)).thenReturn(Mono.just(List.of(jobEntity)));

        jobService
                .pageJobs(queryRequest)
                .as(StepVerifier::create)
                .assertNext(pageResponse -> {
                    assertThat(pageResponse.getTotal()).isEqualTo(1L);
                    assertThat(pageResponse.getRecords()).hasSize(1);
                    assertThat(pageResponse.getRecords().get(0).getJobGroup()).isEqualTo("SYSTEM");
                })
                .verifyComplete();
    }

    /**
     * 测试分页查询任务列表 - 空结果。
     */
    @Test
    @DisplayName("pageJobs - 查询结果为空")
    void pageJobs_EmptyResult() {
        com.apex.scaffold.application.dto.admin.JobQueryRequest queryRequest =
                com.apex.scaffold.application.dto.admin.JobQueryRequest.builder()
                        .pageNum(1)
                        .pageSize(10)
                        .jobName("不存在的任务")
                        .build();

        when(jobRepository.countByCondition("不存在的任务", null, null)).thenReturn(Mono.just(0L));
        when(jobRepository.findByPage("不存在的任务", null, null, 0, 10)).thenReturn(Mono.just(List.of()));

        jobService
                .pageJobs(queryRequest)
                .as(StepVerifier::create)
                .assertNext(pageResponse -> {
                    assertThat(pageResponse.getTotal()).isEqualTo(0L);
                    assertThat(pageResponse.getRecords()).isEmpty();
                })
                .verifyComplete();
    }

    /**
     * 测试分页查询任务列表 - 第二页数据。
     */
    @Test
    @DisplayName("pageJobs - 查询第二页数据成功")
    void pageJobs_SecondPageSuccess() {
        com.apex.scaffold.application.dto.admin.JobQueryRequest queryRequest =
                com.apex.scaffold.application.dto.admin.JobQueryRequest.builder()
                        .pageNum(2)
                        .pageSize(5)
                        .build();

        JobEntity job2 = JobEntity.create("数据备份", "SYSTEM", "backupTask.backup()", "0 0 3 * * ?");
        job2.setId(2L);

        when(jobRepository.countByCondition(null, null, null)).thenReturn(Mono.just(6L));
        when(jobRepository.findByPage(null, null, null, 5, 5)).thenReturn(Mono.just(List.of(job2)));

        JobDTO jobDTO2 = new JobDTO();
        jobDTO2.setId(2L);
        jobDTO2.setJobName("数据备份");
        when(jobAssembler.toDTO(job2)).thenReturn(jobDTO2);

        jobService
                .pageJobs(queryRequest)
                .as(StepVerifier::create)
                .assertNext(pageResponse -> {
                    assertThat(pageResponse.getTotal()).isEqualTo(6L);
                    assertThat(pageResponse.getRecords()).hasSize(1);
                    assertThat(pageResponse.getPageNum()).isEqualTo(2);
                    assertThat(pageResponse.getPageSize()).isEqualTo(5);
                })
                .verifyComplete();
    }
}
