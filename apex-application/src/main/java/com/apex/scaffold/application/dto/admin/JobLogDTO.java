package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 定时任务执行日志响应 DTO。
 *
 * <p>系统管理-定时任务的执行日志响应对象，承载单次执行的快照、
 * 状态、异常与耗时信息，供管理界面排查执行问题。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "定时任务执行日志响应")
/**n * JobLogDTOn *n * <p>TODO: 补充类的详细说明n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public class JobLogDTO {

    /**
     * 日志 ID。
     */
    @Schema(description = "日志ID")
    private Long id;

    /**
     * 任务 ID。
     */
    @Schema(description = "任务ID")
    private Long jobId;

    /**
     * 任务名称（执行时快照）。
     */
    @Schema(description = "任务名称")
    private String jobName;

    /**
     * 任务分组（执行时快照）。
     */
    @Schema(description = "任务分组")
    private String jobGroup;

    /**
     * 调用目标（执行时快照）。
     */
    @Schema(description = "调用目标")
    private String invokeTarget;

    /**
     * 执行消息。
     */
    @Schema(description = "执行消息")
    private String jobMessage;

    /**
     * 执行状态（0-成功，1-失败，2-执行中）。
     */
    @Schema(description = "执行状态：0-成功，1-失败，2-执行中")
    private Integer status;

    /**
     * 异常信息。
     */
    @Schema(description = "异常信息")
    private String exceptionInfo;

    /**
     * 开始时间。
     */
    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    /**
     * 结束时间。
     */
    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    /**
     * 执行耗时（毫秒）。
     */
    @Schema(description = "执行耗时（毫秒）")
    private Long duration;
}
