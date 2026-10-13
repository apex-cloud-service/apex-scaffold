package com.apex.scaffold.auth.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话数据传输对象
 *
 * <p>用于前端与后端之间传输会话信息，包含用户会话状态、设备信息、登录时间等。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2024-01-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "会话信息")
public class SessionDTO {

    @Schema(description = "会话ID", example = "1")
    private Long id;

    @Schema(description = "租户ID", example = "tenant_001")
    private String tenantId;

    @Schema(description = "用户ID", example = "1001")
    private Long userId;

    @Schema(description = "会话唯一标识", example = "session_abc123")
    private String sessionId;

    @Schema(description = "设备唯一标识", example = "device_xyz789")
    private String deviceId;

    @Schema(description = "设备类型", example = "WEB")
    private String deviceType;

    @Schema(description = "客户端IP地址", example = "192.168.1.100")
    private String clientIp;

    @Schema(description = "用户代理信息", example = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
    private String userAgent;

    @Schema(description = "最后活跃时间", example = "2024-01-01T12:00:00")
    private LocalDateTime lastActiveAt;

    @Schema(description = "会话过期时间", example = "2024-01-02T12:00:00")
    private LocalDateTime expiresAt;

    @Schema(description = "会话状态（ACTIVE=活跃 EXPIRED=已过期 TERMINATED=已终止 KICKED_OUT=被踢出）", example = "ACTIVE")
    private String status;

    @Schema(description = "创建时间", example = "2024-01-01T12:00:00")
    private LocalDateTime createdAt;
}
