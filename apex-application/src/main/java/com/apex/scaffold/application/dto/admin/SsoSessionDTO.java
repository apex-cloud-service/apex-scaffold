package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SSO 会话信息响应对象
 *
 * <p>用于返回 SSO 单点登录会话的信息，包含用户信息、登录时间、会话状态等。
 * 管理员可通过此对象查看活跃的 SSO 会话并进行管理操作。
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>SSO 会话列表查询</li>
 *   <li>活跃会话监控</li>
 *   <li>会话撤销操作</li>
 *   <li>安全审计和异常会话排查</li>
 * </ul>
 *
 * @author Apex Scaffold Generator
 * @since 2026-09-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "SSO 会话信息响应对象")
public class SsoSessionDTO {

    @Schema(description = "会话 ID", example = "1")
    private Long id;

    @Schema(description = "租户 ID", example = "1")
    private Long tenantId;

    @Schema(description = "SSO 提供商 ID", example = "1")
    private Long providerId;

    @Schema(description = "SSO 提供商名称", example = "企业 SAML 认证")
    private String providerName;

    @Schema(description = "用户 ID", example = "100")
    private Long userId;

    @Schema(description = "用户名", example = "zhangsan")
    private String username;

    @Schema(description = "SSO 会话令牌（外部 SSO 系统的会话标识）", example = "sso-session-abc123")
    private String ssoSessionToken;

    @Schema(description = "本地会话 ID（Apex 系统的会话 ID）", example = "local-session-xyz789")
    private String localSessionId;

    @Schema(description = "登录 IP 地址", example = "192.168.1.100")
    private String loginIp;

    @Schema(description = "用户代理（浏览器信息）", example = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
    private String userAgent;

    @Schema(description = "会话状态（ACTIVE-活跃/EXPIRED-过期/REVOKED-已撤销）", example = "ACTIVE")
    private String status;

    @Schema(description = "登录时间", example = "2026-09-19T10:00:00")
    private LocalDateTime loginTime;

    @Schema(description = "过期时间", example = "2026-09-19T18:00:00")
    private LocalDateTime expireTime;

    @Schema(description = "最后活跃时间", example = "2026-09-19T15:30:00")
    private LocalDateTime lastActiveTime;

    @Schema(description = "撤销时间", example = "2026-09-19T16:00:00")
    private LocalDateTime revokedAt;

    @Schema(description = "撤销人 ID", example = "1")
    private Long revokedBy;

    @Schema(description = "撤销原因", example = "管理员主动撤销")
    private String revokeReason;
}
