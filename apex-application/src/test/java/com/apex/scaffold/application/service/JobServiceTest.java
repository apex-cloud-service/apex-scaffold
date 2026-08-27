package com.apex.scaffold.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.dto.admin.JobRequest;
import com.apex.scaffold.application.job.JobScheduler;
import com.apex.scaffold.common.exception.BusinessException;
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

    @InjectMocks
    private JobService jobService;

    /** 任务实体 */
    private JobEntity jobEntity;

    /**
     * 初始化测试数据。
     */
    @BeforeEach
    void setUp() {
        jobEntity = JobEntity.create("清理过期令牌", "SYSTEM", "tokenCleanupTask.cleanup()", "0 0 2 * * ?");
        jobEntity.setId(1L);
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

    /**
     * 测试非法 Cron 表达式被拒绝。
     */
    @Test
    @DisplayName("createJob - 非法Cron表达式抛出异常")
    void createJob_InvalidCronFails() {
        JobRequest request = new JobRequest();
        request.setJobName("非法任务");
        request.setInvokeTarget("tokenCleanupTask.cleanup()");
        request.setCronExpression("0 0");

        jobService
                .createJob(request)
                .as(StepVerifier::create)
                .expectError(BusinessException.class)
                .verify();
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
}
