package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话配置响应对象
 *
 * <p>用于返回会话管理配置信息，包括会话超时时间、并发控制、踢出策略等。
 * 包含审计字段以追踪配置的创建和更新历史。
 *
 * @author Apex Scaffold Generator
 * @since 2026-09-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "会话配置响应对象")
public class SessionConfigDTO {

    @Schema(description = "配置 ID", example = "1")
    private Long id;

    @Schema(description = "租户 ID", example = "1")
    private Long tenantId;

    @Schema(description = "会话超时时间（秒）", example = "1800")
    private Integer sessionTimeout;

    @Schema(description = "每个用户的最大并发会话数", example = "1")
    private Integer maxConcurrentSessions;

    @Schema(description = "达到最大并发数后是否阻止新会话登录", example = "false")
    private Boolean preventLogin;

    @Schema(description = "是否启用会话固定攻击保护", example = "true")
    private Boolean enableSessionFixationProtection;

    @Schema(description = "是否启用记住我功能", example = "true")
    private Boolean enableRememberMe;

    @Schema(description = "记住我有效期（秒）", example = "604800")
    private Integer rememberMeDuration;

    @Schema(description = "是否启用会话活动监控", example = "true")
    private Boolean enableActivityTracking;

    @Schema(description = "过期会话清理间隔（秒）", example = "300")
    private Integer cleanupInterval;

    @Schema(description = "创建时间", example = "2026-09-19T10:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间", example = "2026-09-19T10:00:00")
    private LocalDateTime updatedAt;

    @Schema(description = "创建人 ID", example = "1")
    private Long createdBy;

    @Schema(description = "更新人 ID", example = "1")
    private Long updatedBy;
}
