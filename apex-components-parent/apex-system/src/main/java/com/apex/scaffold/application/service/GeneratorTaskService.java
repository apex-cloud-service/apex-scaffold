package com.apex.scaffold.application.service;

import com.apex.scaffold.application.dto.admin.GenArtifactDTO;
import com.apex.scaffold.application.dto.admin.GenGenerateRequest;
import com.apex.scaffold.application.dto.admin.GenTaskDTO;
import com.apex.scaffold.application.dto.admin.GenTaskSubmitResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.model.generator.GenTaskEntity;
import com.apex.scaffold.domain.repository.GenTaskRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 代码生成任务应用服务。
 *
 * <p>把向导执行从请求线程内的同步串行渲染拆成「提交 → 后台执行 → 轮询进度 → 可选取消」：
 * 提交只做校验与任务落库并立即返回任务 ID；真正的渲染与写盘由调度器认领后在后台推进，
 * 每完成一项回写一次进度，取消通过进度回写失败感知。</p>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>任务表的注解 SQL 不过持久化拦截器，租户必须显式传入；提交时的租户由
 *       {@link GeneratorWizardService#prepareTask} 写入实体</li>
 *   <li>进度回写使用 {@code AND status='RUNNING'} 条件更新，返回 {@code false} 即任务已被取消或结束，
 *       直接把该结果作为「是否继续」信号回传给渲染链</li>
 *   <li>执行异常且已达最大尝试次数才落 FAILED，否则保留 RUNNING 等待租约过期后由其他实例接管重试</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.5.0
 * @see GeneratorWizardService
 * @see GenTaskRepository
 */
@Slf4j
@Service
public class GeneratorTaskService {

    /** 落库错误摘要最大长度，与调度器模板保持一致。 */
    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;

    private final GeneratorWizardService wizardService;

    private final GenTaskRepository genTaskRepository;

    private final int maxAttempts;

    /**
     * 构造代码生成任务应用服务。
     *
     * @param wizardService     向导渲染与写盘服务
     * @param genTaskRepository 生成任务仓储
     * @param maxAttempts       最大尝试次数，达到后不再重试
     */
    public GeneratorTaskService(
            GeneratorWizardService wizardService,
            GenTaskRepository genTaskRepository,
            @Value("${apex.generator.async-task.max-attempts:3}") int maxAttempts) {
        this.wizardService = wizardService;
        this.genTaskRepository = genTaskRepository;
        this.maxAttempts = maxAttempts;
    }

    /**
     * 提交异步生成任务。
     *
     * <p>只做输入校验、预览令牌比对与任务落库，不渲染不写盘。
     *
     * @param request    生成请求
     * @param operatorId 操作人 ID
     * @return 任务 ID 与初始状态
     */
    public Mono<GenTaskSubmitResponse> submit(GenGenerateRequest request, Long operatorId) {
        return wizardService
                .prepareTask(request, operatorId)
                .flatMap(genTaskRepository::insert)
                .map(task -> GenTaskSubmitResponse.builder()
                        .taskId(task.getId())
                        .status(task.getStatus())
                        .build());
    }

    /**
     * 查询任务进度。
     *
     * @param taskId 任务 ID
     * @return 任务进度，任务不存在或不属于当前租户时抛 {@link ResultCode#NOT_FOUND}
     */
    public Mono<GenTaskDTO> getProgress(Long taskId) {
        return wizardService
                .tenantId()
                .flatMap(tenant -> genTaskRepository.findById(taskId, tenant))
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "生成任务不存在")))
                .map(this::toDto);
    }

    /**
     * 查询任务的产物清单。
     *
     * <p>先按任务行校验当前租户归属（不存在或跨租户一律 {@link ResultCode#NOT_FOUND}，
     * 不泄露任务存在性），再返回该任务已登记的产物列表，供前端按产物 ID 发起下载。
     *
     * @param taskId 任务 ID
     * @return 产物 DTO 列表
     */
    public Mono<List<GenArtifactDTO>> getTaskArtifacts(Long taskId) {
        return wizardService
                .tenantId()
                .flatMap(tenant -> genTaskRepository.findById(taskId, tenant))
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "生成任务不存在")))
                .flatMap(task -> wizardService.listTaskArtifacts(taskId, task.getTenantId()));
    }

    /**
     * 请求取消任务。
     *
     * <p>PENDING 直接置 CANCELED；RUNNING 由执行方在下一次进度回写时感知并停止。
     *
     * @param taskId 任务 ID
     * @return 是否取消成功
     */
    public Mono<Boolean> cancel(Long taskId) {
        return wizardService
                .tenantId()
                .flatMap(tenant -> genTaskRepository.requestCancel(taskId, tenant, LocalDateTime.now()))
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "任务不存在或已结束，无法取消")));
    }

    /**
     * 执行一条已认领的任务。
     *
     * <p>由调度器在认领成功并重读任务后调用，租户由调用方显式传入，不使用请求上下文。
     * 执行权以任务的 {@code lockedBy} 为准：进度回写附带心跳时间顺延续租，
     * 租约守卫复用仓储续租条件更新，在渲染写盘与历史落库前复检执行权；
     * 返回 {@code false} 即任务已被取消或接管，执行立即停止且不写终态。
     *
     * @param task   已认领的任务（应来自认领后的数据库重读，含最新 attempts 与 lockedBy）
     * @param tenant 租户标识
     * @return 执行结果；失败但未达最大尝试次数时返回空，等待重试
     */
    public Mono<GeneratorWizardService.TaskOutcome> processTask(GenTaskEntity task, String tenant) {
        String lockedBy = task.getLockedBy();
        return Mono.defer(() -> wizardService
                .runTask(
                        task,
                        task.getOperatorId(),
                        tenant,
                        (processedCount, failedCount, errorMessage) -> genTaskRepository.updateProgress(
                                task.getId(),
                                tenant,
                                lockedBy,
                                LocalDateTime.now(),
                                totalCount(task),
                                processedCount,
                                failedCount,
                                truncate(errorMessage)),
                        () -> genTaskRepository.renewLease(task.getId(), tenant, lockedBy, LocalDateTime.now()))
                .flatMap(outcome -> finish(task, tenant, outcome, lockedBy))
                .onErrorResume(error -> handleFailure(task, tenant, error, lockedBy)));
    }

    /**
     * 按执行结果收敛任务终态。
     *
     * @param task     任务实体
     * @param tenant   租户标识
     * @param outcome  渲染执行结果
     * @param lockedBy 认领实例标识，写入终态 SQL 的执行者条件
     * @return 原执行结果
     */
    private Mono<GeneratorWizardService.TaskOutcome> finish(
            GenTaskEntity task, String tenant, GeneratorWizardService.TaskOutcome outcome, String lockedBy) {
        if (outcome.stopped()) {
            return Mono.just(outcome);
        }
        if (outcome.historyId() == null) {
            return genTaskRepository
                    .markFailed(task.getId(), tenant, lockedBy, "生成未产生任何产物", LocalDateTime.now())
                    .doOnNext(updated -> {
                        if (!Boolean.TRUE.equals(updated)) {
                            log.info("生成任务终态写入未生效（已取消或被接管）: taskId={}", task.getId());
                        }
                    })
                    .thenReturn(outcome);
        }
        return genTaskRepository
                .markCompleted(
                        task.getId(),
                        tenant,
                        lockedBy,
                        outcome.historyId(),
                        totalCount(task),
                        outcome.processedCount(),
                        outcome.failedCount(),
                        LocalDateTime.now())
                .doOnNext(updated -> {
                    if (!Boolean.TRUE.equals(updated)) {
                        log.info("生成任务终态写入未生效（已取消或被接管）: taskId={}", task.getId());
                    }
                })
                .thenReturn(outcome);
    }

    /**
     * 处理执行异常：达到最大尝试次数落 FAILED，否则保留 RUNNING 等待租约过期后重试。
     *
     * @param task     任务实体
     * @param tenant   租户标识
     * @param error    执行异常
     * @param lockedBy 认领实例标识，写入终态 SQL 的执行者条件
     * @return 空结果
     */
    private Mono<GeneratorWizardService.TaskOutcome> handleFailure(
            GenTaskEntity task, String tenant, Throwable error, String lockedBy) {
        String message = truncate(describe(error));
        Integer attempts = task.getAttempts();
        if (attempts != null && attempts >= maxAttempts) {
            log.warn("生成任务执行失败且已达最大尝试次数: taskId={}, attempts={}", task.getId(), attempts, error);
            return genTaskRepository
                    .markFailed(task.getId(), tenant, lockedBy, message, LocalDateTime.now())
                    .then(Mono.<GeneratorWizardService.TaskOutcome>empty());
        }
        log.warn("生成任务执行失败，等待租约过期后重试: taskId={}, attempts={}", task.getId(), attempts, error);
        return Mono.empty();
    }

    /**
     * 提取异常摘要。
     *
     * @param error 异常
     * @return 非空摘要
     */
    private String describe(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    /**
     * 截断错误摘要，避免超出落库字段长度。
     *
     * @param message 原始摘要
     * @return 截断后的摘要
     */
    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_ERROR_MESSAGE_LENGTH ? message : message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }

    /**
     * 读取任务总单元数，空值按 0 处理。
     *
     * @param task 任务实体
     * @return 总处理单元数
     */
    private int totalCount(GenTaskEntity task) {
        Integer total = task.getTotalCount();
        return total == null ? 0 : total;
    }

    /**
     * 实体转进度响应。
     *
     * @param task 任务实体
     * @return 进度响应
     */
    private GenTaskDTO toDto(GenTaskEntity task) {
        return GenTaskDTO.builder()
                .id(task.getId())
                .configId(task.getConfigId())
                .historyId(task.getHistoryId())
                .status(task.getStatus())
                .totalCount(task.getTotalCount())
                .processedCount(task.getProcessedCount())
                .failedCount(task.getFailedCount())
                .operatorId(task.getOperatorId())
                .errorMessage(task.getErrorMessage())
                .attempts(task.getAttempts())
                .completedAt(task.getCompletedAt())
                .canceledAt(task.getCanceledAt())
                .createTime(task.getCreateTime())
                .build();
    }
}
