package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 定时任务创建/更新请求体。
 *
 * <p>系统管理-定时任务的请求 DTO。调用目标采用
 * {@code beanName.methodName} 或 {@code beanName.methodName("arg", 1)}
 * 格式；Cron 表达式为 6/7 段标准格式，最终合法性由服务端解析器校验。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Schema(description = "定时任务创建/更新请求")
public class JobRequest {

    /**
     * 构造定时任务请求体。
     */
    public JobRequest() {}

    /**
     * 任务名称。
     */
    @Schema(description = "任务名称", example = "清理过期令牌")
    @NotBlank(message = "{job.name_not_blank}")
    @Size(max = 64, message = "{job.name_too_long}")
    private String jobName;

    /**
     * 任务分组。
     *
     * <p>缺省为 {@code DEFAULT}。</p>
     */
    @Schema(description = "任务分组", example = "SYSTEM")
    @Size(max = 64, message = "{job.group_too_long}")
    private String jobGroup;

    /**
     * 调用目标。
     *
     * <p>格式为 {@code beanName.methodName} 或带参数形式
     * {@code beanName.methodName("arg", 1)}。</p>
     */
    @Schema(description = "调用目标", example = "tokenCleanupTask.cleanup()")
    @NotBlank(message = "{job.target_not_blank}")
    @Size(max = 500, message = "{job.target_too_long}")
    private String invokeTarget;

    /**
     * Cron 表达式。
     *
     * <p>6/7 段标准格式，例如 {@code 0 0 2 * * ?} 表示每天凌晨 2 点。</p>
     */
    @Schema(description = "Cron表达式", example = "0 0 2 * * ?")
    @NotBlank(message = "{job.cron_not_blank}")
    @Pattern(regexp = "^\\S+(\\s+\\S+){5,6}$", message = "{job.cron_invalid}")
    @Size(max = 255, message = "{job.cron_too_long}")
    private String cronExpression;

    /**
     * 是否允许并发执行（0-禁止，1-允许）。
     */
    @Schema(description = "是否允许并发：0-禁止，1-允许", example = "1")
    private Integer concurrent;

    /**
     * 备注。
     */
    @Schema(description = "备注")
    @Size(max = 500, message = "{job.remark_too_long}")
    private String remark;
}
