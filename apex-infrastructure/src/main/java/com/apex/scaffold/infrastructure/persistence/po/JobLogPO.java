package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 定时任务执行日志持久化对象。
 *
 * <p>对应数据库中的 {@code sys_job_log} 表，存储任务执行历史。
 * 每条日志记录一次完整执行的快照、状态与耗时。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_job_log")
public class JobLogPO extends BasePO {

    /**
     * 构造定时任务执行日志持久化对象。
     */
    public JobLogPO() {}

    private String tenantId;

    /**
     * 任务 ID。
     */
    private Long jobId;

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
     */
    private String jobMessage;

    /**
     * 执行状态（0-成功，1-失败，2-执行中）。
     */
    private Integer status;

    /**
     * 异常信息。
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
}
