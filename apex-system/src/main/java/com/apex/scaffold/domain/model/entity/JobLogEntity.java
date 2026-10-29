package com.apex.scaffold.domain.model.entity;

import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 定时任务执行日志实体。
 *
 * <p>记录每次定时任务触发执行的完整信息，包括调用目标、执行状态、
 * 异常详情与耗时，供管理界面排查任务执行问题。</p>
 *
 * <h2>核心业务规则</h2>
 * <ul>
 *   <li>每次执行生成一条独立日志，{@code jobId} 关联任务定义</li>
 *   <li>执行结果分为成功（0）与失败（1），失败时记录异常信息</li>
 *   <li>执行耗时为结束时间与开始时间的差值（毫秒）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see JobEntity
 * @see BaseEntity
 */
@Getter
@lombok.Setter
@lombok.NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class JobLogEntity extends BaseEntity {

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 任务 ID。
     *
     * <p>关联 {@link JobEntity#getId()}，标识日志归属的任务。</p>
     */
    private Long jobId;

    @lombok.Setter(lombok.AccessLevel.NONE)
    private String tenantId;

    /**
     * 任务名称（执行时快照）。
     */
    private String jobName;

    /**
     * 任务分组（执行时快照）。
     */
    private String jobGroup;

    /**
     * 调用目标（执行时快照）。
     */
    private String invokeTarget;

    /**
     * 执行消息。
     *
     * <p>成功时的简要描述或失败时的错误摘要。</p>
     */
    private String jobMessage;

    /**
     * 执行状态。
     *
     * <p>取值说明：0-成功，1-失败。</p>
     */
    private Integer status;

    /**
     * 异常信息。
     *
     * <p>失败时的堆栈摘要，成功时为 {@code null}。</p>
     */
    private String exceptionInfo;

    /**
     * 开始时间。
     */
    private LocalDateTime startTime;

    /**
     * 结束时间。
     */
    private LocalDateTime endTime;

    /**
     * 执行耗时（毫秒）。
     */
    private Long duration;

    /**
     * 静态工厂方法：创建执行日志骨架。
     *
     * <p>创建时仅填充任务快照与开始时间，执行完成后通过
     * {@link #complete(String, boolean, String)} 补全结果。</p>
     *
     * @param job 任务实体
     * @return 新创建的执行日志实体，状态为进行中
     */
    public static JobLogEntity start(JobEntity job) {
        JobLogEntity entity = new JobLogEntity();
        entity.jobId = job.getId();
        entity.tenantId = job.getTenantId();
        entity.jobName = job.getJobName();
        entity.jobGroup = job.getJobGroup();
        entity.invokeTarget = job.getInvokeTarget();
        entity.status = RunStatus.RUNNING.getCode();
        entity.startTime = LocalDateTime.now();
        entity.initTimestamps();
        return entity;
    }

    /**
     * 领域行为：补全执行结果。
     *
     * @param message 执行消息
     * @param success 是否成功
     * @param exception 异常摘要，成功时为 {@code null}
     */
    public void complete(String message, boolean success, String exception) {
        this.endTime = LocalDateTime.now();
        this.duration = java.time.Duration.between(startTime, endTime).toMillis();
        this.jobMessage = message;
        this.status = success ? RunStatus.SUCCESS.getCode() : RunStatus.FAILED.getCode();
        this.exceptionInfo = exception;
        touch();
    }

    /**
     * 领域行为：检查执行是否成功。
     *
     * @return true-成功，false-失败
     */
    public boolean isSuccess() {
        return status != null && status.equals(RunStatus.SUCCESS.getCode());
    }

    /**
     * 执行状态枚举。
     */
    @Getter
    @lombok.RequiredArgsConstructor
    public enum RunStatus {
        /** 执行中 */
        RUNNING(2),
        /** 成功 */
        SUCCESS(0),
        /** 失败 */
        FAILED(1);

        private final Integer code;
    }
}
