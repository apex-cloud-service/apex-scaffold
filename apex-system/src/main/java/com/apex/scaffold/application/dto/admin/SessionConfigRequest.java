package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话配置请求对象
 *
 * <p>用于更新会话管理相关的配置参数，包括会话超时时间、并发控制、踢出策略等。
 * 所有参数均包含 JSR-303 校验注解以确保数据合法性。
 *
 * @author Apex Scaffold Generator
 * @since 1.3.1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "会话配置请求对象")
public class SessionConfigRequest {

    @NotNull(message = "会话超时时间不能为空")
    @Min(value = 300, message = "会话超时时间不能小于 300 秒")
    @Schema(description = "会话超时时间（秒）", required = true, example = "1800")
    private Integer sessionTimeout;

    @NotNull(message = "最大并发会话数不能为空")
    @Min(value = 1, message = "最大并发会话数不能小于 1")
    @Schema(description = "每个用户的最大并发会话数", required = true, example = "1")
    private Integer maxConcurrentSessions;

    @NotNull(message = "是否阻止新会话登录不能为空")
    @Schema(description = "达到最大并发数后是否阻止新会话登录（false 则踢出旧会话）", required = true, example = "false")
    private Boolean preventLogin;

    @NotNull(message = "是否启用会话固定攻击保护不能为空")
    @Schema(description = "是否启用会话固定攻击保护（登录后更换 Session ID）", required = true, example = "true")
    private Boolean enableSessionFixationProtection;

    @NotNull(message = "是否启用记住我功能不能为空")
    @Schema(description = "是否启用记住我功能", required = true, example = "true")
    private Boolean enableRememberMe;

    @NotNull(message = "记住我有效期不能为空")
    @Min(value = 86400, message = "记住我有效期不能小于 86400 秒")
    @Schema(description = "记住我有效期（秒）", required = true, example = "604800")
    private Integer rememberMeDuration;

    @NotNull(message = "是否启用会话活动监控不能为空")
    @Schema(description = "是否启用会话活动监控（记录用户最后活动时间）", required = true, example = "true")
    private Boolean enableActivityTracking;

    @NotNull(message = "会话清理间隔不能为空")
    @Min(value = 60, message = "会话清理间隔不能小于 60 秒")
    @Schema(description = "过期会话清理间隔（秒）", required = true, example = "300")
    private Integer cleanupInterval;
}
