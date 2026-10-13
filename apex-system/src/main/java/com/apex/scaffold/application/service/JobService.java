package com.apex.scaffold.application.service;

import com.apex.scaffold.application.assembler.JobAssembler;
import com.apex.scaffold.application.dto.admin.JobDTO;
import com.apex.scaffold.application.dto.admin.JobLogDTO;
import com.apex.scaffold.application.dto.admin.JobRequest;
import com.apex.scaffold.application.job.CronExpression;
import com.apex.scaffold.application.job.JobScheduler;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.model.entity.JobEntity;
import com.apex.scaffold.domain.repository.JobRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 定时任务管理应用服务。
 *
 * <p>提供定时任务的 CRUD、启停与手动触发管理，并与 {@link JobScheduler}
 * 联动实现动态调度：创建/更新/启停后自动注册或取消调度链，删除任务时
 * 同时取消调度。应用启动时通过 {@code JobInitializer} 恢复全部启用任务。</p>
 *
 * <h2>核心业务规则</h2>
 * <ul>
 *   <li>Cron 表达式必须合法，保存前由 {@link CronExpression} 解析校验</li>
 *   <li>启用状态的任务在创建/更新后立即注册调度；停用/删除后取消调度</li>
 *   <li>手动触发（runOnce）不改变调度状态，仅执行一次并记录日志</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    /**
     * 定时任务仓储。
     */
    private final JobRepository jobRepository;

    /**
     * 定时任务调度器。
     */
    private final JobScheduler jobScheduler;

    /**
     * 任务执行日志服务。
     */
    private final JobLogService jobLogService;

    /**
     * 任务对象转换器。
     */
    private final JobAssembler jobAssembler;

    /**
     * 查询全部任务。
     *
     * @return 任务 DTO 列表
     */
    public Mono<List<JobDTO>> listJobs() {
        return jobRepository
                .findAll()
                .map(jobs -> jobs.stream().map(jobAssembler::toDTO).toList());
    }

    /**
     * 分页查询任务列表（支持条件筛选）。
     *
     * <p>根据查询条件进行分页查询，支持按任务名称模糊匹配、任务分组和状态精确筛选。
     * 查询结果按创建时间降序排列，返回分页响应对象。</p>
     *
     * @param request 查询请求，包含分页参数和筛选条件
     * @return 分页响应，包含任务 DTO 列表和分页信息
     */
    public Mono<com.apex.scaffold.common.dto.PageResponse<JobDTO>> pageJobs(
            com.apex.scaffold.application.dto.admin.JobQueryRequest request) {
        return Mono.defer(() -> {
                    String jobName = request.getJobName();
                    String jobGroup = request.getJobGroup();
                    Integer status = request.getStatus();
                    Integer offset = request.getOffset();
                    Integer pageSize = request.getPageSize();

                    Mono<List<JobEntity>> listMono =
                            jobRepository.findByPage(jobName, jobGroup, status, offset, pageSize);
                    Mono<Long> countMono = jobRepository.countByCondition(jobName, jobGroup, status);

                    return Mono.zip(listMono, countMono).map(tuple -> {
                        List<JobEntity> entities = tuple.getT1();
                        Long total = tuple.getT2();
                        List<JobDTO> dtos =
                                entities.stream().map(jobAssembler::toDTO).toList();
                        return com.apex.scaffold.common.dto.PageResponse.of(
                                dtos, total, request.getPageNum(), pageSize);
                    });
                })
                .doOnSuccess(result ->
                        log.debug("分页查询定时任务完成，total: {}, pageNum: {}", result.getTotal(), result.getPageNum()));
    }

    /**
     * 查询任务详情。
     *
     * @param id 任务 ID
     * @return 任务实体
     * @throws BusinessException 当任务不存在时抛出 {@code JOB_NOT_FOUND}
     */
    public Mono<JobEntity> getJob(Long id) {
        return jobRepository.findById(id).switchIfEmpty(Mono.error(new BusinessException(ResultCode.JOB_NOT_FOUND)));
    }

    /**
     * 创建任务。
     *
     * <p>校验 Cron 表达式合法后持久化；启用状态的任务立即注册调度。</p>
     *
     * @param request 任务请求体
     * @return 已创建的任务实体
     * @throws BusinessException 当 Cron 表达式非法时抛出
     */
    public Mono<JobEntity> createJob(JobRequest request) {
        return Mono.defer(() -> {
                    validateCron(request.getCronExpression());
                    String tenantId =
                            TenantContext.currentTenantId().orElseThrow(() -> new IllegalStateException("租户上下文不存在"));
                    JobEntity entity = JobEntity.create(
                            request.getJobName(),
                            request.getJobGroup(),
                            request.getInvokeTarget(),
                            request.getCronExpression(),
                            tenantId);
                    entity.setRemark(request.getRemark());
                    if (request.getConcurrent() != null) {
                        entity.setConcurrent(request.getConcurrent());
                    }
                    return jobRepository.insert(entity).map(rows -> entity);
                })
                .flatMap(entity -> jobScheduler.register(entity).thenReturn(entity))
                .doOnSuccess(job -> log.info("创建定时任务成功，name: {}", job.getJobName()));
    }

    /**
     * 更新任务。
     *
     * <p>更新后重新注册调度（启用时进入调度链，停用时取消）。</p>
     *
     * @param id 任务 ID
     * @param request 任务请求体
     * @return 已更新的任务实体
     * @throws BusinessException 当任务不存在或 Cron 表达式非法时抛出
     */
    public Mono<JobEntity> updateJob(Long id, JobRequest request) {
        return getJob(id)
                .flatMap(existing -> {
                    validateCron(request.getCronExpression());
                    existing.updateInfo(
                            request.getJobName(),
                            request.getJobGroup(),
                            request.getInvokeTarget(),
                            request.getCronExpression(),
                            request.getRemark());
                    if (request.getConcurrent() != null) {
                        existing.setConcurrent(request.getConcurrent());
                    }
                    return jobRepository.updateById(existing).map(rows -> existing);
                })
                .flatMap(job -> jobScheduler.register(job).thenReturn(job))
                .doOnSuccess(job -> log.info("更新定时任务成功，id: {}", id));
    }

    /**
     * 删除任务。
     *
     * <p>删除后取消调度链。</p>
     *
     * @param id 任务 ID
     * @return 删除结果，true-删除成功，false-未删除任何记录
     * @throws BusinessException 当任务不存在时抛出
     */
    public Mono<Boolean> deleteJob(Long id) {
        return getJob(id)
                .flatMap(job -> jobRepository.deleteById(id).map(rows -> rows > 0))
                .flatMap(deleted -> jobScheduler.unregister(id).thenReturn(deleted))
                .doOnSuccess(deleted -> log.info("删除定时任务完成，id: {}, deleted: {}", id, deleted));
    }

    /**
     * 启用或停用任务。
     *
     * <p>启用后注册调度，停用后取消调度。</p>
     *
     * @param id 任务 ID
     * @param enabled true-启用，false-停用
     * @return 更新后的任务实体
     * @throws BusinessException 当任务不存在时抛出
     */
    public Mono<JobEntity> updateStatus(Long id, boolean enabled) {
        return getJob(id)
                .flatMap(job -> {
                    if (enabled) {
                        job.enable();
                    } else {
                        job.disable();
                    }
                    return jobRepository.updateById(job).map(rows -> job);
                })
                .flatMap(job -> enabled
                        ? jobScheduler.register(job).thenReturn(job)
                        : jobScheduler.unregister(id).thenReturn(job));
    }

    /**
     * 立即执行一次任务。
     *
     * <p>不改变调度状态，仅触发一次执行并记录日志。</p>
     *
     * @param id 任务 ID
     * @return 执行完成的信号
     * @throws BusinessException 当任务不存在时抛出
     */
    public Mono<Void> runOnce(Long id) {
        return getJob(id).flatMap(jobScheduler::trigger);
    }

    /**
     * 查询任务的最近执行日志。
     *
     * @param jobId 任务 ID
     * @param limit 返回条数上限
     * @return 执行日志 DTO 列表
     */
    public Mono<List<JobLogDTO>> listLogs(Long jobId, int limit) {
        return jobLogService.findLatestByJobId(jobId, limit);
    }

    /**
     * 校验 Cron 表达式合法性。
     *
     * @param cronExpression Cron 表达式
     * @throws BusinessException 解析失败时抛出
     */
    private void validateCron(String cronExpression) {
        try {
            new CronExpression(cronExpression);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Cron 表达式格式不合法: " + e.getMessage());
        }
    }
}
