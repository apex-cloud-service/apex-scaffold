package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 定时任务响应 DTO。
 *
 * <p>系统管理-定时任务的响应对象，承载任务定义、调度状态与执行策略
 * 等管理信息。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Schema(description = "定时任务响应")
public class JobDTO {

    /**
     * 构造定时任务响应。
     */
    public JobDTO() {}

    /**
     * 任务 ID。
     */
    @Schema(description = "任务ID")
    private Long id;

    /**
     * 任务名称。
     */
    @Schema(description = "任务名称")
    private String jobName;

    /**
     * 任务分组。
     */
    @Schema(description = "任务分组")
    private String jobGroup;

    /**
     * 调用目标。
     */
    @Schema(description = "调用目标")
    private String invokeTarget;

    /**
     * Cron 表达式。
     */
    @Schema(description = "Cron表达式")
    private String cronExpression;

    /**
     * 错失执行策略（0-默认，1-立即补执行，2-放弃）。
     */
    @Schema(description = "错失执行策略：0-默认，1-立即补执行，2-放弃")
    private Integer misfirePolicy;

    /**
     * 是否允许并发（0-禁止，1-允许）。
     */
    @Schema(description = "是否允许并发：0-禁止，1-允许")
    private Integer concurrent;

    /**
     * 任务状态（0-停用，1-正常）。
     */
    @Schema(description = "任务状态：0-停用，1-正常")
    private Integer status;

    /**
     * 备注。
     */
    @Schema(description = "备注")
    private String remark;

    /**
     * 创建时间。
     */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
